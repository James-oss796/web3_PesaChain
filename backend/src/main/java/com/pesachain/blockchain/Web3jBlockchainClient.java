package com.pesachain.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Numeric;

@Component
public class Web3jBlockchainClient implements BlockchainClient {
    private static final Event RECORDED_EVENT = new Event("LoanEventRecorded", List.of(
            new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {},
            new TypeReference<Uint8>() {}, new TypeReference<Uint256>() {},
            new TypeReference<Uint64>() {}, new TypeReference<Address>() {}));
    private static final String EVENT_TOPIC = EventEncoder.encode(RECORDED_EVENT);

    private final Web3j web3j;
    private final String rpcUrl;
    private final String contractAddress;
    private final String signerPrivateKey;
    private final long expectedChainId;
    private final BigInteger gasLimit;

    public Web3jBlockchainClient(
            @Value("${pesachain.blockchain.rpc-url:http://127.0.0.1:8545}") String rpcUrl,
            @Value("${pesachain.blockchain.contract-address:}") String contractAddress,
            @Value("${pesachain.blockchain.signer-private-key:}") String signerPrivateKey,
            @Value("${pesachain.blockchain.chain-id:31337}") long expectedChainId,
            @Value("${pesachain.blockchain.gas-limit:500000}") long gasLimit) {
        this.rpcUrl = rpcUrl;
        this.contractAddress = contractAddress;
        this.signerPrivateKey = signerPrivateKey;
        this.expectedChainId = expectedChainId;
        this.gasLimit = BigInteger.valueOf(gasLimit);
        this.web3j = Web3j.build(new HttpService(rpcUrl));
    }

    @Override
    public String submit(OutboxEvent event) {
        Runtime runtime = runtime();
        Function function = new Function("recordEvent", List.of(
                new Bytes32(Numeric.hexStringToByteArray(event.eventIdHex())),
                new Bytes32(event.aggregateKey()),
                new Uint8(BigInteger.valueOf(event.eventType().ordinal())),
                new Uint256(event.amountMinorUnits()),
                new Uint64(BigInteger.valueOf(event.occurredAt().getEpochSecond()))), List.of());
        try {
            var gasPrice = web3j.ethGasPrice().send().getGasPrice();
            var manager = new RawTransactionManager(web3j, runtime.credentials(), expectedChainId);
            var response = manager.sendTransaction(gasPrice, gasLimit, contractAddress,
                    FunctionEncoder.encode(function), BigInteger.ZERO);
            if (response.hasError()) throw new BlockchainChainException("TRANSACTION_SUBMISSION_REJECTED", false);
            return response.getTransactionHash();
        } catch (BlockchainChainException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BlockchainChainException("RPC_SUBMISSION_UNAVAILABLE", true, failure);
        }
    }

    @Override
    public Optional<ChainEventEvidence> find(OutboxEvent event) {
        Runtime runtime = runtime();
        if (!eventExists(runtime, event.eventIdHex())) return Optional.empty();
        ChainEventEvidence details = eventDetails(runtime, event.eventIdHex());
        List<ChainEventEvidence> logs = logsFor(event.eventIdHex());
        if (logs.size() != 1) throw new BlockchainChainException("CHAIN_EVENT_LOG_MISMATCH", false);
        ChainEventEvidence evidence = logs.getFirst();
        verifyEvidenceMatches(event, details);
        verifyEvidenceMatches(event, evidence);
        if (!details.aggregateKey().equalsIgnoreCase(evidence.aggregateKey())
                || details.eventType() != evidence.eventType()
                || !details.amountMinorUnits().equals(evidence.amountMinorUnits())
                || details.occurredAt() != evidence.occurredAt()) {
            throw new BlockchainChainException("CHAIN_STATE_LOG_MISMATCH", false);
        }
        return Optional.of(evidence);
    }

    @Override
    public Optional<ChainEventEvidence> receipt(OutboxEvent event, String transactionHash) {
        runtime();
        try {
            var response = web3j.ethGetTransactionReceipt(transactionHash).send();
            if (response.getTransactionReceipt().isEmpty()) return Optional.empty();
            var receipt = response.getTransactionReceipt().orElseThrow();
            if (!"0x1".equalsIgnoreCase(receipt.getStatus())) {
                throw new BlockchainChainException("TRANSACTION_REVERTED", false);
            }
            List<ChainEventEvidence> matching = new ArrayList<>();
            for (Log log : receipt.getLogs()) {
                if (isLedgerEvent(log)) matching.add(decodeLog(log));
            }
            if (matching.size() != 1) throw new BlockchainChainException("RECEIPT_EVENT_MISMATCH", false);
            ChainEventEvidence evidence = withConfirmations(matching.getFirst());
            if (!evidence.transactionHash().equalsIgnoreCase(transactionHash))
                throw new BlockchainChainException("RECEIPT_HASH_MISMATCH", false);
            verifyEvidenceMatches(event, evidence);
            return Optional.of(evidence);
        } catch (BlockchainChainException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BlockchainChainException("RPC_RECEIPT_UNAVAILABLE", true, failure);
        }
    }

    @Override
    public List<ChainEventEvidence> allEvents() {
        runtime();
        try {
            BigInteger head = web3j.ethBlockNumber().send().getBlockNumber();
            EthFilter filter = new EthFilter(new DefaultBlockParameterNumber(BigInteger.ZERO),
                    new DefaultBlockParameterNumber(head), contractAddress).addSingleTopic(EVENT_TOPIC);
            var response = web3j.ethGetLogs(filter).send();
            if (response.hasError()) throw new BlockchainChainException("RPC_LOG_QUERY_FAILED", true);
            List<ChainEventEvidence> result = new ArrayList<>();
            for (EthLog.LogResult<?> raw : response.getLogs()) {
                Object entry = raw.get();
                if (!(entry instanceof Log log)) throw new BlockchainChainException("MALFORMED_CHAIN_LOG", false);
                result.add(withConfirmations(decodeLog(log)));
            }
            return result;
        } catch (BlockchainChainException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BlockchainChainException("RPC_LOG_QUERY_FAILED", true, failure);
        }
    }

    @Override
    public BlockchainNodeHealth health() {
        Instant checkedAt = Instant.now();
        try {
            Runtime runtime = runtime();
            var blockResponse = web3j.ethBlockNumber().send();
            if (blockResponse.hasError() || blockResponse.getBlockNumber() == null)
                throw new BlockchainChainException("RPC_BLOCK_HEIGHT_UNAVAILABLE", true);
            return BlockchainNodeHealth.available(expectedChainId, runtime.chainId().toString(),
                    blockResponse.getBlockNumber().toString(), checkedAt);
        } catch (BlockchainChainException failure) {
            return BlockchainNodeHealth.unavailable(expectedChainId, checkedAt, failure.code());
        } catch (Exception failure) {
            return BlockchainNodeHealth.unavailable(expectedChainId, checkedAt, "RPC_UNAVAILABLE");
        }
    }

    private Runtime runtime() {
        if (rpcUrl == null || rpcUrl.isBlank() || contractAddress == null || contractAddress.isBlank()
                || signerPrivateKey == null || signerPrivateKey.isBlank()) {
            throw new BlockchainChainException("BLOCKCHAIN_NOT_CONFIGURED", true);
        }
        try {
            Credentials credentials = Credentials.create(Numeric.cleanHexPrefix(signerPrivateKey));
            var chainResponse = web3j.ethChainId().send();
            if (chainResponse.hasError()) throw new BlockchainChainException("RPC_CHAIN_ID_UNAVAILABLE", true);
            if (chainResponse.getChainId().longValueExact() != expectedChainId)
                throw new BlockchainChainException("RPC_CHAIN_ID_MISMATCH", false);
            var codeResponse = web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST).send();
            if (codeResponse.hasError() || codeResponse.getCode() == null || "0x".equals(codeResponse.getCode()))
                throw new BlockchainChainException("CONTRACT_CODE_UNAVAILABLE", true);
            Runtime runtime = new Runtime(credentials, chainResponse.getChainId());
            if (!probeContract(runtime)) throw new BlockchainChainException("CONTRACT_ABI_MISMATCH", false);
            if (!isRecorder(runtime, credentials.getAddress())) throw new BlockchainChainException("SIGNER_NOT_AUTHORIZED", false);
            return runtime;
        } catch (BlockchainChainException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BlockchainChainException("RPC_UNAVAILABLE", true, failure);
        }
    }

    private boolean probeContract(Runtime runtime) {
        Function details = new Function("getEventDetails", List.of(new Bytes32(new byte[32])), List.of(
                new TypeReference<Bytes32>() {}, new TypeReference<Uint8>() {}, new TypeReference<Uint256>() {},
                new TypeReference<Uint64>() {}, new TypeReference<Address>() {}));
        return call(runtime, details).size() == 5;
    }

    private boolean isRecorder(Runtime runtime, String account) {
        Function function = new Function("isRecorder", List.of(new Address(account)), List.of(new TypeReference<Bool>() {}));
        List<Type> response = call(runtime, function);
        return !response.isEmpty() && Boolean.TRUE.equals(response.getFirst().getValue());
    }

    private boolean eventExists(Runtime runtime, String eventId) {
        Function function = new Function("eventExists", List.of(new Bytes32(Numeric.hexStringToByteArray(eventId))),
                List.of(new TypeReference<Bool>() {}));
        List<Type> response = call(runtime, function);
        return (Boolean) response.getFirst().getValue();
    }

    private ChainEventEvidence eventDetails(Runtime runtime, String eventId) {
        Function function = new Function("getEventDetails", List.of(new Bytes32(Numeric.hexStringToByteArray(eventId))), List.of(
                new TypeReference<Bytes32>() {}, new TypeReference<Uint8>() {}, new TypeReference<Uint256>() {},
                new TypeReference<Uint64>() {}, new TypeReference<Address>() {}));
        List<Type> response = call(runtime, function);
        return new ChainEventEvidence(eventId, Numeric.toHexString((byte[]) response.get(0).getValue()),
                ((BigInteger) response.get(1).getValue()).intValueExact(), (BigInteger) response.get(2).getValue(),
                ((BigInteger) response.get(3).getValue()).longValueExact(), response.get(4).getValue().toString(),
                null, null, 0);
    }

    private List<Type> call(Runtime runtime, Function function) {
        try {
            String data = FunctionEncoder.encode(function);
            var response = web3j.ethCall(Transaction.createEthCallTransaction(runtime.credentials().getAddress(), contractAddress, data),
                    DefaultBlockParameterName.LATEST).send();
            if (response.hasError()) throw new BlockchainChainException("RPC_CONTRACT_READ_FAILED", true);
            return FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        } catch (BlockchainChainException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BlockchainChainException("RPC_CONTRACT_READ_FAILED", true, failure);
        }
    }

    private List<ChainEventEvidence> logsFor(String eventId) {
        try {
            EthFilter filter = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contractAddress)
                    .addSingleTopic(EVENT_TOPIC).addOptionalTopics(eventId);
            var response = web3j.ethGetLogs(filter).send();
            if (response.hasError()) throw new BlockchainChainException("RPC_LOG_QUERY_FAILED", true);
            List<ChainEventEvidence> result = new ArrayList<>();
            for (EthLog.LogResult<?> raw : response.getLogs()) {
                Object entry = raw.get();
                if (!(entry instanceof Log log)) throw new BlockchainChainException("MALFORMED_CHAIN_LOG", false);
                result.add(withConfirmations(decodeLog(log)));
            }
            return result;
        } catch (BlockchainChainException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BlockchainChainException("RPC_LOG_QUERY_FAILED", true, failure);
        }
    }

    private ChainEventEvidence decodeLog(Log log) {
        if (!isLedgerEvent(log) || log.getTopics().size() != 3)
            throw new BlockchainChainException("MALFORMED_CHAIN_LOG", false);
        List<Type> decoded = FunctionReturnDecoder.decode(log.getData(), RECORDED_EVENT.getNonIndexedParameters());
        if (decoded.size() != 4) throw new BlockchainChainException("MALFORMED_CHAIN_LOG", false);
        return new ChainEventEvidence(log.getTopics().get(1), log.getTopics().get(2),
                ((BigInteger) decoded.get(0).getValue()).intValueExact(), (BigInteger) decoded.get(1).getValue(),
                ((BigInteger) decoded.get(2).getValue()).longValueExact(), decoded.get(3).getValue().toString(),
                log.getTransactionHash(), log.getBlockNumber(), 0);
    }

    private boolean isLedgerEvent(Log log) {
        return log.getAddress() != null && log.getAddress().equalsIgnoreCase(contractAddress)
                && log.getTopics() != null && !log.getTopics().isEmpty()
                && EVENT_TOPIC.equalsIgnoreCase(log.getTopics().getFirst());
    }

    private ChainEventEvidence withConfirmations(ChainEventEvidence evidence) {
        if (evidence.blockNumber() == null) return evidence;
        try {
            BigInteger head = web3j.ethBlockNumber().send().getBlockNumber();
            int count = head.subtract(evidence.blockNumber()).add(BigInteger.ONE).max(BigInteger.ZERO).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
            return new ChainEventEvidence(evidence.eventId(), evidence.aggregateKey(), evidence.eventType(), evidence.amountMinorUnits(),
                    evidence.occurredAt(), evidence.recorderAddress(), evidence.transactionHash(), evidence.blockNumber(), count);
        } catch (IOException failure) {
            throw new BlockchainChainException("RPC_CONFIRMATION_QUERY_FAILED", true, failure);
        }
    }

    private static void verifyEvidenceMatches(OutboxEvent expected, ChainEventEvidence actual) {
        String eventId = expected.eventIdHex();
        if (!eventId.equalsIgnoreCase(actual.eventId()) || !expected.aggregateKeyHex().equalsIgnoreCase(actual.aggregateKey())
                || expected.eventType().ordinal() != actual.eventType()
                || !expected.amountMinorUnits().equals(actual.amountMinorUnits())
                || expected.occurredAt().getEpochSecond() != actual.occurredAt()) {
            throw new BlockchainChainException("CHAIN_EVENT_PAYLOAD_MISMATCH", false);
        }
    }

    private record Runtime(Credentials credentials, BigInteger chainId) {}
}
