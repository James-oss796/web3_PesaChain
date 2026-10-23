package com.pesachain.applications;

import com.pesachain.auth.ApiException;
import com.pesachain.auth.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficerBorrowerHistoryService {
    private final JdbcTemplate jdbc;

    public OfficerBorrowerHistoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BorrowerHistory history(UserPrincipal officer, UUID borrowerId) {
        if (!"LOAN_OFFICER".equals(officer.role()) || !officer.isEnabled() || officer.passwordChangeRequired()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Loan Officer access is required.");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM borrower_profiles WHERE user_id=?)", Boolean.class, borrowerId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Borrower was not found.");
        }
        long applicationCount = jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE borrower_id=?", Long.class, borrowerId);
        List<LoanHistory> loans = jdbc.query("SELECT l.id,l.application_id,l.principal,l.total_repayable,l.outstanding_balance,l.status,l.approved_at,l.disbursed_at,"
                        + "coalesce(sum(r.amount_applied),0.00)::numeric(30,2) AS amount_repaid "
                        + "FROM loans l LEFT JOIN repayments r ON r.loan_id=l.id WHERE l.borrower_id=? "
                        + "GROUP BY l.id ORDER BY l.approved_at DESC,l.id",
                (rs, row) -> new LoanHistory(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                        rs.getBigDecimal("principal"), rs.getBigDecimal("total_repayable"), rs.getBigDecimal("amount_repaid"),
                        rs.getBigDecimal("outstanding_balance"), rs.getString("status"), rs.getTimestamp("approved_at").toInstant(),
                        rs.getTimestamp("disbursed_at") == null ? null : rs.getTimestamp("disbursed_at").toInstant()), borrowerId);
        List<RepaymentHistory> repayments = jdbc.query("SELECT r.id,r.loan_id,r.amount_applied,r.resulting_balance,r.recorded_at,"
                        + "o.status AS blockchain_status,o.transaction_hash,o.block_number,o.confirmation_count "
                        + "FROM repayments r JOIN loans l ON l.id=r.loan_id "
                        + "LEFT JOIN blockchain_outbox o ON o.source_type='REPAYMENT' AND o.source_id=r.id AND o.event_type='REPAYMENT_RECORDED' "
                        + "WHERE l.borrower_id=? ORDER BY r.recorded_at,r.id",
                (rs, row) -> new RepaymentHistory(rs.getObject("id", UUID.class), rs.getObject("loan_id", UUID.class),
                        rs.getBigDecimal("amount_applied"), rs.getBigDecimal("resulting_balance"), rs.getTimestamp("recorded_at").toInstant(),
                        rs.getString("blockchain_status"), rs.getString("transaction_hash"),
                        rs.getBigDecimal("block_number") == null ? null : rs.getBigDecimal("block_number").toBigIntegerExact().toString(),
                        rs.getInt("confirmation_count")), borrowerId);
        List<LoanHistoryEntry> lifecycle = jdbc.query("SELECT h.loan_id,h.from_status,h.to_status,h.changed_at "
                        + "FROM loan_status_history h JOIN loans l ON l.id=h.loan_id WHERE l.borrower_id=? ORDER BY h.sequence_id",
                (rs, row) -> new LoanHistoryEntry(rs.getObject("loan_id", UUID.class), rs.getString("from_status"),
                        rs.getString("to_status"), rs.getTimestamp("changed_at").toInstant()), borrowerId);
        BigDecimal totalRepaid = loans.stream().map(LoanHistory::amountRepaid).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        long activeLoans = loans.stream().filter(loan -> "ACTIVE".equals(loan.status())).count();
        BigDecimal outstanding = loans.stream().map(LoanHistory::outstandingBalance).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new BorrowerHistory(applicationCount, loans.size(), activeLoans, totalRepaid, outstanding, loans, repayments, lifecycle);
    }

    public record BorrowerHistory(long applicationCount, long loanCount, long activeLoanCount, BigDecimal amountRepaid,
            BigDecimal outstandingBalance, List<LoanHistory> loans, List<RepaymentHistory> repayments,
            List<LoanHistoryEntry> lifecycle) {}
    public record LoanHistory(UUID loanId, UUID applicationId, BigDecimal principal, BigDecimal totalRepayable,
            BigDecimal amountRepaid, BigDecimal outstandingBalance, String status, Instant approvedAt, Instant disbursedAt) {}
    public record RepaymentHistory(UUID repaymentId, UUID loanId, BigDecimal amountApplied, BigDecimal outstandingAfter,
            Instant recordedAt, String blockchainStatus, String transactionHash, String blockNumber, int confirmations) {}
    public record LoanHistoryEntry(UUID loanId, String fromStatus, String toStatus, Instant changedAt) {}
}
