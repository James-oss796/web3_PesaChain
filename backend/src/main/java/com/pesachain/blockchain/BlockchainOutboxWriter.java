package com.pesachain.blockchain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes selected business evidence in the caller's PostgreSQL transaction. */
@Component
public class BlockchainOutboxWriter {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public BlockchainOutboxWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void applicationDecision(UUID applicationId, boolean approved) {
        byte[] key = ensureAggregateKey(applicationId);
        enqueue("APPLICATION", applicationId, "APPLICATION", applicationId, key,
                approved ? "LOAN_APPROVED" : "LOAN_REJECTED", BigInteger.ZERO);
    }

    public void loanEvent(UUID loanId, String sourceType, UUID sourceId, String eventType, BigDecimal amount) {
        UUID applicationId = jdbc.queryForObject("SELECT application_id FROM loans WHERE id=?", UUID.class, loanId);
        byte[] key = ensureAggregateKey(applicationId);
        BigInteger minorUnits = amount == null ? BigInteger.ZERO : amount.setScale(2).movePointRight(2).toBigIntegerExact();
        enqueue(sourceType, sourceId, "LOAN", loanId, key, eventType, minorUnits);
    }

    private byte[] ensureAggregateKey(UUID applicationId) {
        byte[] generated = new byte[32];
        random.nextBytes(generated);
        jdbc.update("UPDATE loan_applications SET blockchain_aggregate_key=? WHERE id=? AND blockchain_aggregate_key IS NULL",
                generated, applicationId);
        return jdbc.queryForObject("SELECT blockchain_aggregate_key FROM loan_applications WHERE id=?", byte[].class, applicationId);
    }

    private void enqueue(String sourceType, UUID sourceId, String aggregateType, UUID aggregateId,
            byte[] aggregateKey, String eventType, BigInteger amountMinorUnits) {
        jdbc.update("INSERT INTO blockchain_outbox(event_id,source_type,source_id,aggregate_type,aggregate_id,aggregate_key,event_type,amount_minor_units) "
                        + "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(source_type,source_id,event_type) DO NOTHING",
                UUID.randomUUID(), sourceType, sourceId, aggregateType, aggregateId, aggregateKey, eventType, amountMinorUnits);
    }
}
