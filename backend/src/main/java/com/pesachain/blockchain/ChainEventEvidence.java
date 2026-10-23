package com.pesachain.blockchain;

import java.math.BigInteger;

public record ChainEventEvidence(String eventId, String aggregateKey, int eventType, BigInteger amountMinorUnits,
        long occurredAt, String recorderAddress, String transactionHash, BigInteger blockNumber, int confirmations) {}
