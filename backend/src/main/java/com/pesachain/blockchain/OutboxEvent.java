package com.pesachain.blockchain;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String sourceType,
        UUID sourceId,
        String aggregateType,
        UUID aggregateId,
        byte[] aggregateKey,
        BlockchainEventType eventType,
        BigInteger amountMinorUnits,
        Instant occurredAt,
        String previousStatus,
        String transactionHash,
        BigInteger blockNumber,
        int attemptCount,
        boolean retryable) {
    public String eventIdHex() { return BlockchainIds.toBytes32(eventId); }
    public String aggregateKeyHex() { return BlockchainIds.toHex(aggregateKey); }
}
