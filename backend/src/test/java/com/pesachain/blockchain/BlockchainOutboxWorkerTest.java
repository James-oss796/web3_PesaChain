package com.pesachain.blockchain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainOutboxWorkerTest {
    @Mock private BlockchainOutboxRepository repository;
    @Mock private BlockchainClient client;
    private BlockchainOutboxWorker worker;
    private OutboxEvent pending;

    @BeforeEach
    void setUp() {
        worker = new BlockchainOutboxWorker(repository, client, 1, 4, 60_000, 100, 100, 10_000);
        byte[] key = new byte[32]; key[0] = 1;
        pending = new OutboxEvent(UUID.randomUUID(), "REPAYMENT", UUID.randomUUID(), "LOAN", UUID.randomUUID(), key,
                BlockchainEventType.REPAYMENT_RECORDED, BigInteger.valueOf(4250), Instant.ofEpochSecond(1_800_000_000),
                "PENDING", null, null, 1, true);
    }

    @Test
    void transientSubmissionFailureLeavesOperationToRetry() {
        when(repository.claim(any(), eq(4), eq(60_000L))).thenReturn(Optional.of(pending));
        when(client.find(pending)).thenReturn(Optional.empty());
        when(client.submit(pending)).thenThrow(new BlockchainChainException("RPC_SUBMISSION_UNAVAILABLE", true));

        worker.processNext();

        verify(repository).failed(eq(pending), any(), eq(null), eq("RPC_SUBMISSION_UNAVAILABLE"), eq(true), any(Long.class), eq(4));
        verify(repository, never()).recorded(any(), any(), any());
    }

    @Test
    void acceptedTransactionCanBeRecoveredByEventIdAfterLocalPersistenceFailure() {
        UUID workerId = UUID.randomUUID();
        when(repository.claim(any(), eq(4), eq(60_000L))).thenReturn(Optional.of(pending));
        when(client.find(pending)).thenReturn(Optional.empty(), Optional.of(evidence(1)));
        when(client.submit(pending)).thenReturn("0x" + "ab".repeat(32));
        doThrow(new IllegalStateException("simulated local persistence interruption"))
                .doNothing().when(repository).submitted(eq(pending), any(), any(), eq(100L));

        worker.processNext();
        worker.processNext();

        verify(client).submit(pending);
        verify(client, org.mockito.Mockito.times(2)).find(pending);
        verify(repository).recorded(eq(pending), any(), any());
    }

    @Test
    void onlyRequiredConfirmationCanMarkEventRecorded() {
        OutboxEvent submitted = new OutboxEvent(pending.eventId(), pending.sourceType(), pending.sourceId(), pending.aggregateType(),
                pending.aggregateId(), pending.aggregateKey(), pending.eventType(), pending.amountMinorUnits(), pending.occurredAt(),
                "SUBMITTED", "0x" + "cd".repeat(32), BigInteger.TEN, 1, true);
        when(repository.claim(any(), eq(4), eq(60_000L))).thenReturn(Optional.of(submitted));
        when(client.receipt(submitted, submitted.transactionHash())).thenReturn(Optional.of(evidence(0)));

        worker.processNext();

        verify(repository).waitingForConfirmation(eq(submitted), any(), any(), eq(100L));
        verify(repository, never()).recorded(any(), any(), any());
    }

    private ChainEventEvidence evidence(int confirmations) {
        return new ChainEventEvidence(pending.eventIdHex(), pending.aggregateKeyHex(), pending.eventType().ordinal(),
                pending.amountMinorUnits(), pending.occurredAt().getEpochSecond(), "0x0000000000000000000000000000000000000001",
                "0x" + "ef".repeat(32), BigInteger.TEN, confirmations);
    }
}
