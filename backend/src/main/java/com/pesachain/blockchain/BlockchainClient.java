package com.pesachain.blockchain;

import java.util.List;
import java.util.Optional;

public interface BlockchainClient {
    String submit(OutboxEvent event);
    Optional<ChainEventEvidence> find(OutboxEvent event);
    Optional<ChainEventEvidence> receipt(OutboxEvent event, String transactionHash);
    List<ChainEventEvidence> allEvents();
    BlockchainNodeHealth health();
}
