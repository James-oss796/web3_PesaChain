package com.pesachain.blockchain;

import java.time.Instant;

/** Safe operational snapshot; never contains the RPC URL or signing credentials. */
public record BlockchainNodeHealth(
        String status,
        long expectedChainId,
        String observedChainId,
        String latestBlock,
        Instant checkedAt,
        String issueCode) {

    static BlockchainNodeHealth available(long expectedChainId, String observedChainId, String latestBlock, Instant checkedAt) {
        return new BlockchainNodeHealth("AVAILABLE", expectedChainId, observedChainId, latestBlock, checkedAt, null);
    }

    static BlockchainNodeHealth unavailable(long expectedChainId, Instant checkedAt, String issueCode) {
        return new BlockchainNodeHealth("UNAVAILABLE", expectedChainId, null, null, checkedAt, issueCode);
    }
}
