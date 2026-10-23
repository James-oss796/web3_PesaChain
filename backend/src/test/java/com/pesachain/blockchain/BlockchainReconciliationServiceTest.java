package com.pesachain.blockchain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainReconciliationServiceTest {
    @Mock private BlockchainOutboxRepository repository;
    @Mock private BlockchainClient client;
    private BlockchainReconciliationService service;
    private OutboxEvent local;

    @BeforeEach
    void setUp() {
        service = new BlockchainReconciliationService(repository, client, 5000, true);
        byte[] key = new byte[32]; key[0] = 5;
        local = new OutboxEvent(UUID.randomUUID(), "DISBURSEMENT", UUID.randomUUID(), "LOAN", UUID.randomUUID(), key,
                BlockchainEventType.LOAN_DISBURSED, BigInteger.valueOf(50000), Instant.ofEpochSecond(1_800_000_000),
                "RECORDED", "0x" + "11".repeat(32), BigInteger.TEN, 1, false);
    }

    @Test
    void matchingChainEvidenceResolvesPriorFindingWithoutChangingLoanState() {
        when(client.allEvents()).thenReturn(List.of(evidence()));
        when(repository.reconciliationCandidates(5000)).thenReturn(List.of(local));
        when(repository.allEventIds()).thenReturn(List.of(local.eventIdHex()));

        service.reconcile(admin());

        verify(repository).resolve(local.eventId(), local.eventIdHex(), "MISSING_ON_CHAIN");
        verify(repository, never()).finding(local.eventId(), local.eventIdHex(), "PAYLOAD_MISMATCH", evidence().transactionHash(), evidence().blockNumber());
    }

    @Test
    void missingRecordedEvidenceAndUnexpectedOrDuplicateEventsAreReported() {
        ChainEventEvidence unknown = new ChainEventEvidence("0x" + "22".repeat(32), "0x" + "33".repeat(32),
                0, BigInteger.ZERO, 1_800_000_000, "0x0000000000000000000000000000000000000001",
                "0x" + "44".repeat(32), BigInteger.ONE, 1);
        when(client.allEvents()).thenReturn(List.of(unknown, unknown));
        when(repository.reconciliationCandidates(5000)).thenReturn(List.of(local));
        when(repository.allEventIds()).thenReturn(List.of(local.eventIdHex()));

        service.reconcile(admin());

        verify(repository).finding(local.eventId(), local.eventIdHex(), "MISSING_ON_CHAIN", local.transactionHash(), local.blockNumber());
        verify(repository).finding(null, unknown.eventId(), "UNEXPECTED_CHAIN_EVENT", unknown.transactionHash(), unknown.blockNumber());
        verify(repository).finding(null, unknown.eventId(), "DUPLICATE_CHAIN_EVENT", unknown.transactionHash(), unknown.blockNumber());
    }

    @Test
    void payloadMismatchIsRecordedAndNonAdministratorCannotRunReconciliation() {
        ChainEventEvidence wrong = new ChainEventEvidence(local.eventIdHex(), local.aggregateKeyHex(),
                BlockchainEventType.LOAN_DISBURSED.ordinal(), BigInteger.ONE, local.occurredAt().getEpochSecond(),
                "0x0000000000000000000000000000000000000001", "0x" + "55".repeat(32), BigInteger.ONE, 1);
        when(client.allEvents()).thenReturn(List.of(wrong));
        when(repository.reconciliationCandidates(5000)).thenReturn(List.of(local));
        when(repository.allEventIds()).thenReturn(List.of(local.eventIdHex()));

        service.reconcile(admin());
        verify(repository).finding(local.eventId(), local.eventIdHex(), "PAYLOAD_MISMATCH", wrong.transactionHash(), wrong.blockNumber());

        clearInvocations(client);
        assertThrows(ApiException.class, () -> service.reconcile(new UserPrincipal(UUID.randomUUID(), "staff@example.test",
                "hash", "LOAN_OFFICER", "ACTIVE", "Officer", "+254700000000", false)));
        verify(client, never()).allEvents();
    }

    private ChainEventEvidence evidence() {
        return new ChainEventEvidence(local.eventIdHex(), local.aggregateKeyHex(), local.eventType().ordinal(),
                local.amountMinorUnits(), local.occurredAt().getEpochSecond(), "0x0000000000000000000000000000000000000001",
                local.transactionHash(), local.blockNumber(), 1);
    }

    private static UserPrincipal admin() {
        return new UserPrincipal(UUID.randomUUID(), "admin@example.test", "hash", "ADMINISTRATOR", "ACTIVE", "Admin", "+254700000000", false);
    }
}
