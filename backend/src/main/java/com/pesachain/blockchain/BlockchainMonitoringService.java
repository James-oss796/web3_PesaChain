package com.pesachain.blockchain;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockchainMonitoringService {
    private final BlockchainOutboxRepository repository;
    private final BlockchainReconciliationService reconciliation;
    private final JdbcTemplate jdbc;
    private final BlockchainClient client;

    public BlockchainMonitoringService(BlockchainOutboxRepository repository,
            BlockchainReconciliationService reconciliation, JdbcTemplate jdbc, BlockchainClient client) {
        this.repository = repository;
        this.reconciliation = reconciliation;
        this.jdbc = jdbc;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public StatusPage administratorEvents(UserPrincipal user, String status, int page, int size) {
        requireRole(user, "ADMINISTRATOR");
        return statusPage(status, page, size);
    }

    @Transactional(readOnly = true)
    public StatusPage officerEvents(UserPrincipal user, String status, int page, int size) {
        requireRole(user, "LOAN_OFFICER");
        return statusPage(status, page, size);
    }

    @Transactional(readOnly = true)
    public List<EventStatus> borrowerLoanEvents(UserPrincipal user, UUID loanId) {
        requireRole(user, "BORROWER");
        if (jdbc.queryForObject("SELECT count(*) FROM loans WHERE id=? AND borrower_id=?", Integer.class, loanId, user.id()) != 1)
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Loan was not found.");
        return repository.listLoanStatus(loanId, user.id()).stream().map(EventStatus::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BlockchainOutboxRepository.FindingView> findings(UserPrincipal administrator, int limit) {
        requireRole(administrator, "ADMINISTRATOR");
        if (limit < 1 || limit > 200) throw invalid("Limit must be between 1 and 200.");
        return repository.findings(limit);
    }

    /** Performs a live RPC/chain/contract/signer check; no DB transaction is held during network calls. */
    public AdminHealth health(UserPrincipal administrator) {
        requireRole(administrator, "ADMINISTRATOR");
        BlockchainNodeHealth node = client.health();
        java.util.Map<String, Long> storedCounts = repository.statusCounts();
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (String status : List.of("PENDING", "PROCESSING", "SUBMITTED", "RECORDED", "FAILED"))
            counts.put(status, storedCounts.getOrDefault(status, 0L));
        return new AdminHealth(node, counts);
    }

    /** Explicit recovery action after the live check confirms the RPC and configured contract are usable. */
    public RetryQueued retryFailedEvent(UserPrincipal administrator, UUID eventId) {
        requireRole(administrator, "ADMINISTRATOR");
        BlockchainNodeHealth node = client.health();
        if (!"AVAILABLE".equals(node.status()))
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BLOCKCHAIN_UNAVAILABLE",
                    "Blockchain is not healthy. Restore the RPC, contract, and authorized signer before retrying delivery.");
        if (!repository.queueManualRetry(eventId, administrator.id())) {
            String status = repository.status(eventId).orElse(null);
            if (status == null) throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Blockchain event was not found.");
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_NOT_RETRYABLE", "Only a failed blockchain delivery can be queued for retry.");
        }
        return new RetryQueued(eventId, "Blockchain delivery retry queued. The operational loan record is unchanged.");
    }

    public BlockchainReconciliationService.ReconciliationResult reconcile(UserPrincipal administrator) {
        requireRole(administrator, "ADMINISTRATOR");
        try { return reconciliation.reconcile(administrator); }
        catch (BlockchainChainException unavailable) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BLOCKCHAIN_VERIFICATION_UNAVAILABLE",
                    "Blockchain reconciliation is temporarily unavailable.");
        }
    }

    private StatusPage statusPage(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid("Page must be nonnegative and size must be between 1 and 100.");
        if (status != null && !List.of("PENDING", "PROCESSING", "SUBMITTED", "RECORDED", "FAILED").contains(status))
            throw invalid("Status must be PENDING, PROCESSING, SUBMITTED, RECORDED, or FAILED.");
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) throw invalid("Page is out of range.");
        List<EventStatus> items = repository.listStatus(status, size, (int) offset).stream().map(EventStatus::from).toList();
        return new StatusPage(items, page, size);
    }

    private static void requireRole(UserPrincipal user, String role) {
        if (!role.equals(user.role()) || !user.isEnabled() || user.passwordChangeRequired())
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", role.replace('_', ' ') + " access is required.");
    }
    private static ApiException invalid(String detail) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail); }

    public record StatusPage(List<EventStatus> items, int page, int size) {}
    public record AdminHealth(BlockchainNodeHealth connection, java.util.Map<String, Long> outboxCounts) {}
    public record RetryQueued(UUID eventId, String message) {}
    public record EventStatus(UUID eventId, String eventType, BigDecimal amount, String status, String transactionHash,
            java.math.BigInteger blockNumber, int confirmations, int attemptCount, boolean retryable, String lastErrorCode,
            java.time.Instant occurredAt, java.time.Instant recordedAt) {
        static EventStatus from(BlockchainOutboxRepository.StatusView view) {
            BigDecimal amount = List.of("LOAN_DISBURSED", "REPAYMENT_RECORDED").contains(view.eventType())
                    ? view.amountMinorUnits().movePointLeft(2).setScale(2) : null;
            return new EventStatus(view.eventId(), view.eventType(), amount, view.status(), view.transactionHash(),
                    view.blockNumber(), view.confirmationCount(), view.attemptCount(), view.retryable(), view.lastErrorCode(),
                    view.occurredAt(), view.recordedAt());
        }
    }
}
