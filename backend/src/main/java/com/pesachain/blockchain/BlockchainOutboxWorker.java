package com.pesachain.blockchain;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pesachain.blockchain.worker.enabled", havingValue = "true", matchIfMissing = true)
public class BlockchainOutboxWorker {
    private final BlockchainOutboxRepository repository;
    private final BlockchainClient client;
    private final int requiredConfirmations;
    private final int maxAttempts;
    private final long leaseMillis;
    private final long pollMillis;
    private final long retryBaseMillis;
    private final long retryMaxMillis;

    public BlockchainOutboxWorker(BlockchainOutboxRepository repository, BlockchainClient client,
            @Value("${pesachain.blockchain.required-confirmations:1}") int requiredConfirmations,
            @Value("${pesachain.blockchain.max-attempts:8}") int maxAttempts,
            @Value("${pesachain.blockchain.lease-ms:60000}") long leaseMillis,
            @Value("${pesachain.blockchain.worker-interval-ms:2000}") long pollMillis,
            @Value("${pesachain.blockchain.retry-base-ms:2000}") long retryBaseMillis,
            @Value("${pesachain.blockchain.retry-max-ms:300000}") long retryMaxMillis) {
        if (requiredConfirmations < 1 || maxAttempts < 1 || leaseMillis < 1000 || pollMillis < 100
                || retryBaseMillis < 1 || retryMaxMillis < retryBaseMillis) {
            throw new IllegalArgumentException("Blockchain worker configuration is invalid.");
        }
        this.repository = repository;
        this.client = client;
        this.requiredConfirmations = requiredConfirmations;
        this.maxAttempts = maxAttempts;
        this.leaseMillis = leaseMillis;
        this.pollMillis = pollMillis;
        this.retryBaseMillis = retryBaseMillis;
        this.retryMaxMillis = retryMaxMillis;
    }

    @Scheduled(fixedDelayString = "${pesachain.blockchain.worker-interval-ms:2000}")
    public void processNext() {
        UUID workerId = UUID.randomUUID();
        var claimed = repository.claim(workerId, maxAttempts, leaseMillis);
        if (claimed.isEmpty()) return;
        OutboxEvent event = claimed.get();
        String activeTransactionHash = event.transactionHash();
        try {
            ChainEventEvidence evidence = null;
            if (event.transactionHash() != null) {
                var receipt = client.receipt(event, event.transactionHash());
                if (receipt.isEmpty()) {
                    repository.waitingForReceipt(event, workerId, pollMillis);
                    return;
                }
                evidence = receipt.get();
            } else {
                var existing = client.find(event);
                if (existing.isPresent()) {
                    evidence = existing.get();
                    activeTransactionHash = evidence.transactionHash();
                    repository.submitted(event, workerId, evidence.transactionHash(), pollMillis);
                } else {
                    String transactionHash = client.submit(event);
                    activeTransactionHash = transactionHash;
                    repository.submitted(event, workerId, transactionHash, pollMillis);
                    var receipt = client.receipt(event, transactionHash);
                    if (receipt.isEmpty()) return;
                    evidence = receipt.get();
                }
            }

            if (evidence.confirmations() >= requiredConfirmations) {
                repository.recorded(event, workerId, evidence);
            } else {
                repository.waitingForConfirmation(event, workerId, evidence, pollMillis);
            }
        } catch (BlockchainChainException failure) {
            repository.failed(event, workerId, activeTransactionHash, failure.code(), failure.retryable(), retryDelay(event.attemptCount()), maxAttempts);
        } catch (RuntimeException failure) {
            repository.failed(event, workerId, activeTransactionHash, "BLOCKCHAIN_PROCESSING_FAILED", true,
                    retryDelay(event.attemptCount()), maxAttempts);
        }
    }

    long retryDelay(int attempt) {
        int exponent = Math.min(30, Math.max(0, attempt - 1));
        long scaled;
        try { scaled = Math.multiplyExact(retryBaseMillis, 1L << exponent); }
        catch (ArithmeticException overflow) { scaled = retryMaxMillis; }
        long bounded = Math.min(retryMaxMillis, scaled);
        long spread = Math.max(1, bounded / 4);
        long lower = Math.max(1, bounded - spread);
        long upper = bounded + spread;
        return Math.min(retryMaxMillis, ThreadLocalRandom.current().nextLong(lower, upper + 1));
    }
}
