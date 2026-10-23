package com.pesachain.auth;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Aggregated operational reporting; no borrower identity or free-text fields are returned. */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {
    private final JdbcTemplate jdbc;

    public AdminReportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/overview")
    ReportOverview overview(Authentication authentication, @RequestParam(defaultValue = "6") int months) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (!"ADMINISTRATOR".equals(principal.role()) || !principal.isEnabled() || principal.passwordChangeRequired())
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Administrator access is required.");
        if (months < 3 || months > 12) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Report period must be between 3 and 12 months.");

        ApplicationMetrics applications = jdbc.queryForObject("""
                SELECT count(*) AS total,
                    count(*) FILTER (WHERE status='PENDING') AS pending,
                    count(*) FILTER (WHERE status='APPROVED') AS approved,
                    count(*) FILTER (WHERE status='REJECTED') AS rejected
                FROM loan_applications
                """, (rs, n) -> new ApplicationMetrics(rs.getLong("total"), rs.getLong("pending"), rs.getLong("approved"), rs.getLong("rejected")));
        LoanMetrics loans = jdbc.queryForObject("""
                SELECT count(*) AS total,
                    count(*) FILTER (WHERE status='APPROVED') AS approved,
                    count(*) FILTER (WHERE status='ACTIVE') AS active,
                    count(*) FILTER (WHERE status='COMPLETED') AS completed,
                    coalesce(sum(principal),0) AS approved_principal,
                    coalesce(sum(total_repayable),0) AS contractual_total,
                    coalesce(sum(outstanding_balance),0) AS outstanding,
                    (SELECT count(*) FROM repayments) AS repayment_count,
                    (SELECT coalesce(sum(amount_applied),0) FROM repayments) AS amount_repaid
                FROM loans
                """, (rs, n) -> new LoanMetrics(rs.getLong("total"), rs.getLong("approved"), rs.getLong("active"),
                        rs.getLong("completed"), rs.getBigDecimal("approved_principal"), rs.getBigDecimal("contractual_total"),
                        rs.getBigDecimal("outstanding"), rs.getLong("repayment_count"), rs.getBigDecimal("amount_repaid")));
        ActivityMetrics activity = jdbc.queryForObject("""
                SELECT count(*) AS total,
                    count(*) FILTER (WHERE result='FAILED') AS failed
                FROM audit_log WHERE occurred_at >= now() - interval '30 days'
                """, (rs, n) -> new ActivityMetrics(rs.getLong("total"), rs.getLong("failed")));

        Map<String, Long> blockchain = new LinkedHashMap<>();
        for (String status : List.of("PENDING", "PROCESSING", "SUBMITTED", "RECORDED", "FAILED")) blockchain.put(status, 0L);
        jdbc.query("SELECT status,count(*) AS total FROM blockchain_outbox GROUP BY status",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> blockchain.put(rs.getString("status"), rs.getLong("total")));
        List<MonthlyTrend> trend = jdbc.query("""
                WITH months AS (
                    SELECT generate_series(date_trunc('month',current_date)::date - ((?::int - 1) * interval '1 month'),
                        date_trunc('month',current_date)::date, interval '1 month')::date AS month_start
                ), app AS (
                    SELECT date_trunc('month',submitted_at)::date AS month_start,count(*) AS total FROM loan_applications GROUP BY 1
                ), decisions AS (
                    SELECT date_trunc('month',decision_at)::date AS month_start,count(*) AS total FROM loan_applications WHERE decision_at IS NOT NULL GROUP BY 1
                ), disb AS (
                    SELECT date_trunc('month',recorded_at)::date AS month_start,count(*) AS total,coalesce(sum(amount),0) AS amount
                    FROM disbursements GROUP BY 1
                ), rep AS (
                    SELECT date_trunc('month',recorded_at)::date AS month_start,count(*) AS total,
                        coalesce(sum(amount_applied),0) AS amount FROM repayments GROUP BY 1
                )
                SELECT months.month_start,coalesce(app.total,0) AS applications,coalesce(decisions.total,0) AS decisions,
                    coalesce(disb.total,0) AS disbursements,coalesce(disb.amount,0) AS disbursed_amount,
                    coalesce(rep.total,0) AS repayments,coalesce(rep.amount,0) AS repaid_amount
                FROM months LEFT JOIN app USING(month_start) LEFT JOIN decisions USING(month_start)
                    LEFT JOIN disb USING(month_start) LEFT JOIN rep USING(month_start)
                ORDER BY months.month_start
                """, (rs, n) -> new MonthlyTrend(rs.getObject("month_start", LocalDate.class), rs.getLong("applications"),
                        rs.getLong("decisions"), rs.getLong("disbursements"), rs.getBigDecimal("disbursed_amount"),
                        rs.getLong("repayments"), rs.getBigDecimal("repaid_amount")), months);
        return new ReportOverview(Instant.now(), months, applications, loans, activity, blockchain, trend);
    }

    record ApplicationMetrics(long total, long pending, long approved, long rejected) {}
    record LoanMetrics(long total, long approved, long active, long completed, BigDecimal approvedPrincipal,
            BigDecimal contractualTotal, BigDecimal outstandingBalance, long repaymentCount, BigDecimal amountRepaid) {}
    record ActivityMetrics(long last30Days, long failedLast30Days) {}
    record MonthlyTrend(LocalDate month, long applications, long decisions, long disbursements,
            BigDecimal disbursedAmount, long repayments, BigDecimal repaidAmount) {}
    record ReportOverview(Instant generatedAt, int months, ApplicationMetrics applications, LoanMetrics loans,
            ActivityMetrics activity, Map<String, Long> blockchainEvents, List<MonthlyTrend> monthlyTrend) {}
}
