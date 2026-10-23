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

    public BlockchainMonitoringService(BlockchainOutboxRepository repository,
            BlockchainReconciliationService reconciliation, JdbcTemplate jdbc) {
        this.repository = repository;
        this.reconciliation = reconciliation;
        this.jdbc = jdbc;
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
    public record EventStatus(UUID eventId, String eventType, BigDecimal amount, String status, String transactionHash,
            java.math.BigInteger blockNumber, int confirmations, int attemptCount, String lastErrorCode,
            java.time.Instant occurredAt, java.time.Instant recordedAt) {
        static EventStatus from(BlockchainOutboxRepository.StatusView view) {
            BigDecimal amount = List.of("LOAN_DISBURSED", "REPAYMENT_RECORDED").contains(view.eventType())
                    ? view.amountMinorUnits().movePointLeft(2).setScale(2) : null;
            return new EventStatus(view.eventId(), view.eventType(), amount, view.status(), view.transactionHash(),
                    view.blockNumber(), view.confirmationCount(), view.attemptCount(), view.lastErrorCode(),
                    view.occurredAt(), view.recordedAt());
        }
    }
}
