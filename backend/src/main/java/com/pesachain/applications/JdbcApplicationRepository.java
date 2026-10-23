package com.pesachain.applications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.pesachain.blockchain.BlockchainOutboxWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcApplicationRepository implements ApplicationRepository {
    private static final RowMapper<ApplicationView> APPLICATION_MAPPER = (result, rowNum) ->
            new ApplicationView(
                    result.getObject("id", UUID.class),
                    result.getBigDecimal("requested_amount"),
                    result.getString("purpose"),
                    result.getString("status"),
                    result.getTimestamp("submitted_at").toInstant(),
                    result.getTimestamp("decision_at") == null ? null : result.getTimestamp("decision_at").toInstant(),
                    result.getString("rejection_reason"),
                    result.getObject("requested_term_months", Integer.class),
                    result.getBigDecimal("approved_principal"),
                    result.getObject("approved_term_months", Integer.class),
                    result.getBigDecimal("approved_flat_rate_percent"),
                    result.getBigDecimal("calculated_interest"),
                    result.getBigDecimal("total_repayable"));

    private static final String FIELDS = "id, requested_amount, purpose, status, submitted_at, decision_at, rejection_reason, "
            + "requested_term_months, approved_principal, approved_term_months, approved_flat_rate_percent, calculated_interest, total_repayable";

    private final JdbcTemplate jdbc;
    private final BlockchainOutboxWriter blockchainOutbox;

    public JdbcApplicationRepository(JdbcTemplate jdbc, BlockchainOutboxWriter blockchainOutbox) {
        this.jdbc = jdbc;
        this.blockchainOutbox = blockchainOutbox;
    }

    @Override
    public ApplicationView create(UUID borrowerId, BigDecimal amount, String purpose, int requestedTermMonths, UUID idempotencyKey) {
        UUID id = jdbc.query(
                "INSERT INTO loan_applications (borrower_id, requested_amount, purpose, requested_term_months, idempotency_key) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (borrower_id, idempotency_key) "
                        + "WHERE idempotency_key IS NOT NULL DO NOTHING RETURNING id",
                (result, row) -> result.getObject("id", UUID.class),
                borrowerId,
                amount,
                purpose,
                requestedTermMonths,
                idempotencyKey).stream().findFirst().orElse(null);
        if (id == null) {
            return jdbc.query(
                            "SELECT " + FIELDS + " FROM loan_applications WHERE borrower_id = ? AND idempotency_key = ?",
                            APPLICATION_MAPPER,
                            borrowerId,
                            idempotencyKey)
                    .stream()
                    .findFirst()
                    .orElseThrow();
        }
        jdbc.update(
                "INSERT INTO audit_log (actor_id, actor_role, action, target_type, target_id) "
                        + "VALUES (?, 'BORROWER', 'APPLICATION_SUBMITTED', 'APPLICATION', ?)",
                borrowerId,
                id);
        return findMineById(borrowerId, id).orElseThrow();
    }

    @Override
    public List<ApplicationView> findMine(UUID borrowerId, int limit, int offset) {
        return jdbc.query(
                "SELECT " + FIELDS + " FROM loan_applications WHERE borrower_id = ? "
                        + "ORDER BY submitted_at DESC, id DESC LIMIT ? OFFSET ?",
                APPLICATION_MAPPER,
                borrowerId,
                limit,
                offset);
    }

    @Override
    public long countMine(UUID borrowerId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM loan_applications WHERE borrower_id = ?",
                Long.class,
                borrowerId);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<ApplicationView> findMineById(UUID borrowerId, UUID applicationId) {
        return jdbc.query(
                        "SELECT " + FIELDS + " FROM loan_applications WHERE borrower_id = ? AND id = ?",
                        APPLICATION_MAPPER,
                        borrowerId,
                        applicationId)
                .stream()
                .findFirst();
    }

    @Override
    public List<OfficerApplicationView> findPendingForOfficer(int limit, int offset, String borrowerNamePrefix) {
        String sql="SELECT " + prefixedFields("a") + ", a.borrower_id, p.full_name, a.decision_rationale "
                + "FROM loan_applications a JOIN borrower_profiles p ON p.user_id=a.borrower_id ";
        var mapper=(RowMapper<OfficerApplicationView>)(rs,n) -> new OfficerApplicationView(
                APPLICATION_MAPPER.mapRow(rs,n),rs.getObject("borrower_id",UUID.class),rs.getString("full_name"),rs.getString("decision_rationale"));
        if (borrowerNamePrefix == null) return jdbc.query(sql+"WHERE a.status='PENDING' ORDER BY a.submitted_at,a.id LIMIT ? OFFSET ?",mapper,limit,offset);
        return jdbc.query(sql+"WHERE a.status='PENDING' AND lower(p.full_name) LIKE ? ORDER BY a.submitted_at,a.id LIMIT ? OFFSET ?",
                mapper,borrowerNamePrefix+"%",limit,offset);
    }

    @Override
    public long countPendingForOfficer(String borrowerNamePrefix) {
        Long count=borrowerNamePrefix == null
                ? jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE status='PENDING'",Long.class)
                : jdbc.queryForObject("SELECT count(*) FROM loan_applications a JOIN borrower_profiles p ON p.user_id=a.borrower_id "
                        + "WHERE a.status='PENDING' AND lower(p.full_name) LIKE ?",Long.class,borrowerNamePrefix+"%");
        return count == null ? 0 : count;
    }

    @Override
    public Optional<OfficerApplicationView> findForOfficer(UUID applicationId) {
        return jdbc.query("SELECT " + prefixedFields("a") + ", a.borrower_id, p.full_name, a.decision_rationale "
                        + "FROM loan_applications a JOIN borrower_profiles p ON p.user_id=a.borrower_id WHERE a.id=?",
                (rs,n) -> new OfficerApplicationView(APPLICATION_MAPPER.mapRow(rs,n),rs.getObject("borrower_id",UUID.class),rs.getString("full_name"),rs.getString("decision_rationale")),applicationId)
                .stream().findFirst();
    }

    @Override
    public boolean recordDecision(UUID applicationId, UUID officerId, boolean approved, String rationale,
            String rejectionReason, BigDecimal principal, int termMonths, BigDecimal flatRate,
            BigDecimal interest, BigDecimal totalRepayable) {
        int changed=jdbc.update("UPDATE loan_applications SET status=?, decision_by=?, decision_at=now(), decision_rationale=?, rejection_reason=?, "
                        + "approved_principal=?, approved_term_months=?, approved_flat_rate_percent=?, calculated_interest=?, total_repayable=? "
                        + "WHERE id=? AND status='PENDING'",
                approved ? "APPROVED":"REJECTED",officerId,rationale,rejectionReason,principal,
                approved ? termMonths:null,approved ? flatRate:null,interest,totalRepayable,applicationId);
        if(changed==1) {
            jdbc.update("INSERT INTO audit_log(actor_id,actor_role,action,target_type,target_id) VALUES(?,'LOAN_OFFICER',?,'APPLICATION',?)",
                    officerId,approved ? "APPLICATION_APPROVED":"APPLICATION_REJECTED",applicationId);
            if(approved) {
                UUID loanId=jdbc.queryForObject("INSERT INTO loans(application_id,borrower_id,principal,flat_rate_percent,term_months,interest_amount,total_repayable,outstanding_balance) "
                                + "SELECT id,borrower_id,approved_principal,approved_flat_rate_percent,approved_term_months,calculated_interest,total_repayable,total_repayable "
                                + "FROM loan_applications WHERE id=? RETURNING id",UUID.class,applicationId);
                jdbc.update("INSERT INTO loan_status_history(loan_id,from_status,to_status,changed_by) VALUES(?,NULL,'APPROVED',?)",loanId,officerId);
            }
            blockchainOutbox.applicationDecision(applicationId, approved);
        }
        return changed==1;
    }

    private static String prefixedFields(String alias) {
        return java.util.Arrays.stream(FIELDS.split(", ")).map(field -> alias+"."+field).collect(java.util.stream.Collectors.joining(", "));
    }
}
