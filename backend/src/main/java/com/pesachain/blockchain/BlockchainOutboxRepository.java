package com.pesachain.blockchain;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BlockchainOutboxRepository {
    private final JdbcTemplate jdbc;

    public BlockchainOutboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Optional<OutboxEvent> claim(UUID workerId, int maxAttempts, long leaseMillis) {
        String sql = "WITH candidate AS ("
                + " SELECT event_id,status AS previous_status FROM blockchain_outbox"
                + " WHERE ((status IN ('PENDING','SUBMITTED') AND next_attempt_at<=now())"
                + " OR (status='FAILED' AND retryable AND next_attempt_at<=now())"
                + " OR (status='PROCESSING' AND lease_until<now()))"
                + " AND (transaction_hash IS NOT NULL OR attempt_count < ?)"
                + " ORDER BY next_attempt_at,created_at,event_id FOR UPDATE SKIP LOCKED LIMIT 1"
                + ") UPDATE blockchain_outbox b SET status='PROCESSING',lease_owner=?,"
                + " lease_until=now()+(? * interval '1 millisecond'),"
                + " attempt_count=b.attempt_count+CASE WHEN b.transaction_hash IS NULL THEN 1 ELSE 0 END,"
                + " updated_at=now(),version=version+1 FROM candidate c WHERE b.event_id=c.event_id"
                + " RETURNING b.*,c.previous_status";
        List<OutboxEvent> claimed = jdbc.query(sql, this::map, maxAttempts, workerId, leaseMillis);
        if (claimed.isEmpty()) return Optional.empty();
        OutboxEvent event = claimed.getFirst();
        if (event.transactionHash() == null) {
            jdbc.update("INSERT INTO blockchain_event_attempts(event_id,attempt_number) VALUES(?,?) ON CONFLICT DO NOTHING",
                    event.eventId(), event.attemptCount());
        }
        return Optional.of(event);
    }

    public void submitted(OutboxEvent event, UUID workerId, String transactionHash, long pollMillis) {
        int changed = jdbc.update("UPDATE blockchain_outbox SET status='SUBMITTED',transaction_hash=?,submitted_at=COALESCE(submitted_at,now()),"
                        + "next_attempt_at=now()+(? * interval '1 millisecond'),lease_owner=NULL,lease_until=NULL,last_error_code=NULL,updated_at=now(),version=version+1 "
                        + "WHERE event_id=? AND status='PROCESSING' AND lease_owner=?",
                transactionHash, pollMillis, event.eventId(), workerId);
        if (changed == 1 && event.attemptCount() > 0) {
            jdbc.update("UPDATE blockchain_event_attempts SET outcome='SUBMITTED',transaction_hash=?,finished_at=now() WHERE event_id=? AND attempt_number=?",
                    transactionHash, event.eventId(), event.attemptCount());
        }
    }

    public void recorded(OutboxEvent event, UUID workerId, ChainEventEvidence evidence) {
        int changed = jdbc.update("UPDATE blockchain_outbox SET status='RECORDED',transaction_hash=?,block_number=?,confirmation_count=?,"
                        + "recorded_at=now(),lease_owner=NULL,lease_until=NULL,retryable=false,last_error_code=NULL,updated_at=now(),version=version+1 "
                        + "WHERE event_id=? AND ((status='PROCESSING' AND lease_owner=?) OR (status='SUBMITTED' AND transaction_hash=?))",
                evidence.transactionHash(), evidence.blockNumber(), evidence.confirmations(), event.eventId(), workerId, evidence.transactionHash());
        if (changed == 1 && event.attemptCount() > 0) {
            jdbc.update("UPDATE blockchain_event_attempts SET outcome='RECORDED',transaction_hash=?,block_number=?,confirmation_count=?,finished_at=now() "
                            + "WHERE event_id=? AND attempt_number=?",
                    evidence.transactionHash(), evidence.blockNumber(), evidence.confirmations(), event.eventId(), event.attemptCount());
        }
    }

    public void waitingForConfirmation(OutboxEvent event, UUID workerId, ChainEventEvidence evidence, long pollMillis) {
        jdbc.update("UPDATE blockchain_outbox SET status='SUBMITTED',transaction_hash=?,block_number=?,confirmation_count=?,"
                        + "next_attempt_at=now()+(? * interval '1 millisecond'),lease_owner=NULL,lease_until=NULL,updated_at=now(),version=version+1 "
                        + "WHERE event_id=? AND ((status='PROCESSING' AND lease_owner=?) OR (status='SUBMITTED' AND transaction_hash=?))",
                evidence.transactionHash(), evidence.blockNumber(), evidence.confirmations(), pollMillis, event.eventId(), workerId, evidence.transactionHash());
    }

    public void waitingForReceipt(OutboxEvent event, UUID workerId, long pollMillis) {
        jdbc.update("UPDATE blockchain_outbox SET status='SUBMITTED',next_attempt_at=now()+(? * interval '1 millisecond'),"
                        + "lease_owner=NULL,lease_until=NULL,updated_at=now(),version=version+1 "
                        + "WHERE event_id=? AND status='PROCESSING' AND lease_owner=?",
                pollMillis, event.eventId(), workerId);
    }

    public void failed(OutboxEvent event, UUID workerId, String activeTransactionHash, String code,
            boolean transientFailure, long retryMillis, int maxAttempts) {
        boolean retryable = transientFailure && event.attemptCount() < maxAttempts;
        jdbc.update("UPDATE blockchain_outbox SET status='FAILED',retryable=?,next_attempt_at=now()+(? * interval '1 millisecond'),"
                        + "lease_owner=NULL,lease_until=NULL,last_error_code=?,updated_at=now(),version=version+1 "
                        + "WHERE event_id=? AND ((status='PROCESSING' AND lease_owner=?) OR (status='SUBMITTED' AND transaction_hash=?))",
                retryable, retryMillis, safeCode(code), event.eventId(), workerId, activeTransactionHash);
        if (event.attemptCount() > 0) {
            jdbc.update("UPDATE blockchain_event_attempts SET outcome=?,error_code=?,finished_at=now() WHERE event_id=? AND attempt_number=?",
                    retryable ? "RETRY" : "FAILED", safeCode(code), event.eventId(), event.attemptCount());
        }
    }

    @Transactional
    public List<StatusView> listStatus(String status, int limit, int offset) {
        if (status == null) {
            return jdbc.query("SELECT event_id,event_type,amount_minor_units,status,transaction_hash,block_number,confirmation_count,"
                            + "attempt_count,last_error_code,occurred_at,recorded_at FROM blockchain_outbox ORDER BY created_at DESC,event_id LIMIT ? OFFSET ?",
                    this::mapStatus, limit, offset);
        }
        return jdbc.query("SELECT event_id,event_type,amount_minor_units,status,transaction_hash,block_number,confirmation_count,"
                        + "attempt_count,last_error_code,occurred_at,recorded_at FROM blockchain_outbox WHERE status=? ORDER BY created_at DESC,event_id LIMIT ? OFFSET ?",
                this::mapStatus, status, limit, offset);
    }

    @Transactional
    public List<OutboxEvent> reconciliationCandidates(int limit) {
        return jdbc.query("SELECT *,status AS previous_status FROM blockchain_outbox ORDER BY updated_at LIMIT ?",
                this::map, limit);
    }

    @Transactional(readOnly = true)
    public List<String> allEventIds() {
        return jdbc.query("SELECT event_id FROM blockchain_outbox", (rs, n) -> BlockchainIds.toBytes32(rs.getObject(1, UUID.class)));
    }

    @Transactional(readOnly = true)
    public List<StatusView> listLoanStatus(UUID loanId, UUID borrowerId) {
        return jdbc.query("SELECT o.event_id,o.event_type,o.amount_minor_units,o.status,o.transaction_hash,o.block_number,o.confirmation_count,"
                        + "o.attempt_count,o.last_error_code,o.occurred_at,o.recorded_at FROM blockchain_outbox o "
                        + "JOIN loans l ON ((o.aggregate_type='LOAN' AND l.id=o.aggregate_id) OR (o.aggregate_type='APPLICATION' AND l.application_id=o.aggregate_id)) "
                        + "WHERE l.id=? AND l.borrower_id=? "
                        + "ORDER BY o.occurred_at,o.event_id",
                this::mapStatus, loanId, borrowerId);
    }

    public void finding(UUID eventId, String chainEventId, String code, String transactionHash, BigInteger blockNumber) {
        int inserted = jdbc.update("INSERT INTO blockchain_reconciliation_findings(event_id,chain_event_id,finding_code,transaction_hash,block_number) "
                        + "VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",
                eventId, chainEventId, code, transactionHash, blockNumber);
        if (inserted == 0) jdbc.update("UPDATE blockchain_reconciliation_findings SET last_seen_at=now(),occurrence_count=occurrence_count+1,"
                        + "transaction_hash=COALESCE(?,transaction_hash),block_number=COALESCE(?,block_number) "
                        + "WHERE resolved_at IS NULL AND finding_code=? AND event_id IS NOT DISTINCT FROM ? AND chain_event_id IS NOT DISTINCT FROM ?",
                transactionHash, blockNumber, code, eventId, chainEventId);
    }

    public void resolve(UUID eventId, String chainEventId, String code) {
        jdbc.update("UPDATE blockchain_reconciliation_findings SET resolved_at=now(),last_seen_at=now() "
                        + "WHERE resolved_at IS NULL AND finding_code=? AND event_id IS NOT DISTINCT FROM ? AND chain_event_id IS NOT DISTINCT FROM ?",
                code, eventId, chainEventId);
    }

    @Transactional(readOnly = true)
    public List<FindingView> findings(int limit) {
        return jdbc.query("SELECT id,event_id,chain_event_id,finding_code,transaction_hash,block_number,first_seen_at,last_seen_at,occurrence_count,resolved_at "
                        + "FROM blockchain_reconciliation_findings ORDER BY resolved_at NULLS FIRST,last_seen_at DESC LIMIT ?",
                (rs, n) -> new FindingView(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getString("chain_event_id"), rs.getString("finding_code"), rs.getString("transaction_hash"),
                        bigInt(rs, "block_number"), instant(rs, "first_seen_at"), instant(rs, "last_seen_at"),
                        rs.getInt("occurrence_count"), instant(rs, "resolved_at")), limit);
    }

    private OutboxEvent map(ResultSet rs, int row) throws SQLException {
        BigInteger amount = rs.getBigDecimal("amount_minor_units").toBigIntegerExact();
        return new OutboxEvent(rs.getObject("event_id", UUID.class), rs.getString("source_type"), rs.getObject("source_id", UUID.class),
                rs.getString("aggregate_type"), rs.getObject("aggregate_id", UUID.class), rs.getBytes("aggregate_key"),
                BlockchainEventType.valueOf(rs.getString("event_type")), amount, instant(rs, "occurred_at"),
                rs.getString("previous_status"), rs.getString("transaction_hash"), bigInt(rs, "block_number"),
                rs.getInt("attempt_count"), rs.getBoolean("retryable"));
    }

    private StatusView mapStatus(ResultSet rs, int row) throws SQLException {
        return new StatusView(rs.getObject("event_id", UUID.class), rs.getString("event_type"),
                rs.getBigDecimal("amount_minor_units"), rs.getString("status"), rs.getString("transaction_hash"),
                bigInt(rs, "block_number"), rs.getInt("confirmation_count"), rs.getInt("attempt_count"),
                rs.getString("last_error_code"), instant(rs, "occurred_at"), instant(rs, "recorded_at"));
    }

    private static String safeCode(String code) {
        if (code == null || !code.matches("[A-Z0-9_]{1,64}")) return "BLOCKCHAIN_PROCESSING_FAILED";
        return code;
    }
    private static BigInteger bigInt(ResultSet rs, String field) throws SQLException {
        var value = rs.getBigDecimal(field); return value == null ? null : value.toBigIntegerExact();
    }
    private static Instant instant(ResultSet rs, String field) throws SQLException {
        var value = rs.getTimestamp(field); return value == null ? null : value.toInstant();
    }

    public record StatusView(UUID eventId, String eventType, java.math.BigDecimal amountMinorUnits,
            String status, String transactionHash, BigInteger blockNumber, int confirmationCount,
            int attemptCount, String lastErrorCode, Instant occurredAt, Instant recordedAt) {}
    public record FindingView(UUID id, UUID eventId, String chainEventId, String findingCode, String transactionHash,
            BigInteger blockNumber, Instant firstSeenAt, Instant lastSeenAt, int occurrenceCount, Instant resolvedAt) {}
}
