package com.pesachain.blockchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Opt-in end-to-end test against a separately started, deployed local Hardhat node. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "PESACHAIN_RUN_LOCAL_CHAIN_TESTS", matches = "true")
class LocalHardhatBlockchainIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine3.24")
            .withDatabaseName("pesachain_chain_test").withUsername("pesachain").withPassword("test-only-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("pesachain.blockchain.worker.enabled", () -> true);
        registry.add("pesachain.blockchain.rpc-url", () -> requiredEnv("PESACHAIN_RPC_URL"));
        registry.add("pesachain.blockchain.chain-id", () -> requiredEnv("PESACHAIN_CHAIN_ID"));
        registry.add("pesachain.blockchain.contract-address", () -> requiredEnv("PESACHAIN_CONTRACT_ADDRESS"));
        registry.add("pesachain.blockchain.signer-private-key", () -> requiredEnv("PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY"));
        registry.add("pesachain.blockchain.required-confirmations", () -> 1);
        registry.add("pesachain.blockchain.worker-interval-ms", () -> 60_000);
        registry.add("pesachain.blockchain.retry-base-ms", () -> 100);
        registry.add("pesachain.blockchain.retry-max-ms", () -> 500);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private BlockchainClient blockchainClient;
    @Autowired private BlockchainOutboxWorker worker;

    @Test
    void committedOutboxEventIsSignedRecordedAndReadableFromTheRealContract() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        byte[] aggregateKey = new byte[32];
        aggregateKey[0] = 0x5a;
        Instant occurredAt = Instant.now().minusSeconds(1);
        jdbc.update("INSERT INTO blockchain_outbox(event_id,source_type,source_id,aggregate_type,aggregate_id,aggregate_key,event_type,amount_minor_units,occurred_at) "
                        + "VALUES(?,?,?,?,?,?,'LOAN_DISBURSED',1250000,?)",
                eventId, "DISBURSEMENT", UUID.randomUUID(), "LOAN", loanId, aggregateKey, java.sql.Timestamp.from(occurredAt));

        worker.processNext();
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        String status = null;
        while (System.nanoTime() < deadline) {
            status = jdbc.queryForObject("SELECT status FROM blockchain_outbox WHERE event_id=?", String.class, eventId);
            if ("RECORDED".equals(status) || "FAILED".equals(status)) break;
            Thread.sleep(100);
        }

        assertEquals("RECORDED", status, "the configured confirmation condition must be met");
        String transactionHash = jdbc.queryForObject("SELECT transaction_hash FROM blockchain_outbox WHERE event_id=?", String.class, eventId);
        assertNotNull(transactionHash);
        assertTrue(jdbc.queryForObject("SELECT confirmation_count FROM blockchain_outbox WHERE event_id=?", Integer.class, eventId) >= 1);
        List<ChainEventEvidence> chainEvents = blockchainClient.allEvents();
        assertTrue(chainEvents.stream().anyMatch(event -> event.eventId().equalsIgnoreCase(BlockchainIds.toBytes32(eventId))
                && event.amountMinorUnits().longValueExact() == 1_250_000L
                && event.transactionHash().equalsIgnoreCase(transactionHash)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM blockchain_event_attempts WHERE event_id=? AND outcome='RECORDED'",
                Integer.class, eventId));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing local-chain test configuration: " + name);
        return value;
    }
}
