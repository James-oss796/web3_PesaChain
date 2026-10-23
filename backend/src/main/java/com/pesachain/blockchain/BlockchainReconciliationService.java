package com.pesachain.blockchain;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BlockchainReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(BlockchainReconciliationService.class);
    private static final List<String> FINDING_CODES = List.of(
            "MISSING_ON_CHAIN", "PAYLOAD_MISMATCH", "DUPLICATE_CHAIN_EVENT", "UNEXPECTED_CHAIN_EVENT", "LOCAL_CHAIN_MISMATCH");
    private final BlockchainOutboxRepository repository;
    private final BlockchainClient client;
    private final int candidateLimit;
    private final boolean enabled;

    public BlockchainReconciliationService(BlockchainOutboxRepository repository, BlockchainClient client,
            @Value("${pesachain.blockchain.reconciliation-limit:5000}") int candidateLimit,
            @Value("${pesachain.blockchain.worker.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.client = client;
        this.candidateLimit = candidateLimit;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pesachain.blockchain.reconciliation-interval-ms:60000}")
    public void scheduledReconciliation() {
        if (!enabled) return;
        try { reconcile(); }
        catch (BlockchainChainException failure) { log.warn("Blockchain reconciliation unavailable: {}", failure.code()); }
        catch (RuntimeException failure) { log.warn("Blockchain reconciliation failed: BLOCKCHAIN_RECONCILIATION_FAILED"); }
    }

    public ReconciliationResult reconcile(UserPrincipal administrator) {
        requireAdministrator(administrator);
        return reconcile();
    }

    private ReconciliationResult reconcile() {
        List<ChainEventEvidence> chainEvents = client.allEvents();
        List<OutboxEvent> localEvents = repository.reconciliationCandidates(candidateLimit);
        Set<String> allKnownIds = new HashSet<>(repository.allEventIds());
        Map<String, List<ChainEventEvidence>> chainById = new HashMap<>();
        for (ChainEventEvidence evidence : chainEvents) {
            chainById.computeIfAbsent(evidence.eventId().toLowerCase(), ignored -> new ArrayList<>()).add(evidence);
        }

        int missing = 0, mismatched = 0, duplicates = 0, unexpected = 0, locallyPending = 0;
        Set<String> checked = new HashSet<>();
        for (OutboxEvent local : localEvents) {
            String chainId = local.eventIdHex();
            String mapKey = chainId.toLowerCase();
            checked.add(mapKey);
            List<ChainEventEvidence> observed = chainById.getOrDefault(mapKey, List.of());
            if (observed.isEmpty()) {
                if ("RECORDED".equals(local.previousStatus())) {
                    repository.finding(local.eventId(), chainId, "MISSING_ON_CHAIN", local.transactionHash(), local.blockNumber());
                    missing++;
                }
                continue;
            }
            if (observed.size() > 1) {
                repository.finding(local.eventId(), chainId, "DUPLICATE_CHAIN_EVENT", observed.getFirst().transactionHash(), observed.getFirst().blockNumber());
                duplicates++;
            }
            ChainEventEvidence found = observed.getFirst();
            if (!matches(local, found)) {
                repository.finding(local.eventId(), chainId, "PAYLOAD_MISMATCH", found.transactionHash(), found.blockNumber());
                mismatched++;
                continue;
            }
            for (String code : FINDING_CODES) {
                if (!"DUPLICATE_CHAIN_EVENT".equals(code) || observed.size() == 1) repository.resolve(local.eventId(), chainId, code);
            }
            if (!"RECORDED".equals(local.previousStatus())) {
                repository.finding(local.eventId(), chainId, "LOCAL_CHAIN_MISMATCH", found.transactionHash(), found.blockNumber());
                locallyPending++;
            }
        }

        for (Map.Entry<String, List<ChainEventEvidence>> entry : chainById.entrySet()) {
            if (!allKnownIds.contains(entry.getKey())) {
                ChainEventEvidence evidence = entry.getValue().getFirst();
                repository.finding(null, evidence.eventId(), "UNEXPECTED_CHAIN_EVENT", evidence.transactionHash(), evidence.blockNumber());
                unexpected++;
            }
            if (entry.getValue().size() > 1 && !checked.contains(entry.getKey())) {
                ChainEventEvidence evidence = entry.getValue().getFirst();
                repository.finding(null, evidence.eventId(), "DUPLICATE_CHAIN_EVENT", evidence.transactionHash(), evidence.blockNumber());
                duplicates++;
            }
        }
        return new ReconciliationResult(chainEvents.size(), localEvents.size(), missing, mismatched, duplicates, unexpected, locallyPending);
    }

    private static boolean matches(OutboxEvent local, ChainEventEvidence chain) {
        return local.eventIdHex().equalsIgnoreCase(chain.eventId())
                && local.aggregateKeyHex().equalsIgnoreCase(chain.aggregateKey())
                && local.eventType().ordinal() == chain.eventType()
                && local.amountMinorUnits().equals(chain.amountMinorUnits())
                && local.occurredAt().getEpochSecond() == chain.occurredAt();
    }

    private static void requireAdministrator(UserPrincipal user) {
        if (!"ADMINISTRATOR".equals(user.role()) || !user.isEnabled() || user.passwordChangeRequired())
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Administrator access is required.");
    }

    public record ReconciliationResult(int chainEventsScanned, int localEventsScanned, int missingOnChain,
            int payloadMismatches, int duplicateChainEvents, int unexpectedChainEvents, int locallyUnrecordedChainEvents) {}
}
