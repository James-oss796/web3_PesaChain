package com.pesachain.repayments;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.pesachain.blockchain.BlockchainOutboxWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRepaymentRepository implements RepaymentRepository {
    private final JdbcTemplate jdbc;
    private final BlockchainOutboxWriter blockchainOutbox;
    public JdbcRepaymentRepository(JdbcTemplate jdbc, BlockchainOutboxWriter blockchainOutbox) { this.jdbc = jdbc; this.blockchainOutbox=blockchainOutbox; }

    @Override public Optional<Event> findEvent(UUID id) { return events("id=?", id).stream().findFirst(); }
    @Override public Optional<Event> findBorrowerEvent(UUID borrowerId, UUID id) { return events("borrower_id=? AND id=?", borrowerId, id).stream().findFirst(); }
    @Override public Optional<Event> findByIdempotencyKey(UUID borrowerId, UUID key) { return events("borrower_id=? AND idempotency_key=?", borrowerId,key).stream().findFirst(); }
    @Override public List<Event> listBorrowerEvents(UUID borrowerId, UUID loanId) {
        return jdbc.query("SELECT * FROM simulated_repayment_events WHERE borrower_id=? AND loan_id=? ORDER BY created_at DESC,id", this::mapEvent, borrowerId, loanId);
    }
    @Override public List<Repayment> listBorrowerRepayments(UUID borrowerId, UUID loanId) {
        return jdbc.query("SELECT r.id,r.event_id,r.loan_id,r.amount_applied,r.resulting_balance,r.recorded_by,r.recorded_at FROM repayments r JOIN loans l ON l.id=r.loan_id WHERE l.borrower_id=? AND r.loan_id=? ORDER BY r.recorded_at,r.id",
                (rs,n)->new Repayment(rs.getObject("id",UUID.class),rs.getObject("event_id",UUID.class),rs.getObject("loan_id",UUID.class),rs.getBigDecimal("amount_applied"),rs.getBigDecimal("resulting_balance"),rs.getObject("recorded_by",UUID.class),rs.getTimestamp("recorded_at").toInstant()),borrowerId,loanId);
    }
    @Override public List<Event> listOfficerEvents(String status, int limit, int offset) {
        return jdbc.query("SELECT * FROM simulated_repayment_events WHERE status=? ORDER BY created_at,id LIMIT ? OFFSET ?", this::mapEvent, status, limit, offset);
    }
    @Override public Optional<Event> lockEvent(UUID id) {
        return jdbc.query("SELECT * FROM simulated_repayment_events WHERE id=? FOR UPDATE", this::mapEvent, id).stream().findFirst();
    }
    @Override public Optional<Loan> findLoan(UUID id) { return loan("id=?", id).stream().findFirst(); }
    @Override public Optional<Loan> lockLoan(UUID id) {
        return jdbc.query("SELECT id,borrower_id,outstanding_balance,status FROM loans WHERE id=? FOR UPDATE", this::mapLoan, id).stream().findFirst();
    }
    @Override public Optional<Event> createEvent(UUID id, UUID borrowerId, UUID loanId, BigDecimal amount, UUID key, String signature, Instant createdAt) {
        int inserted=jdbc.update("INSERT INTO simulated_repayment_events(id,loan_id,borrower_id,amount,idempotency_key,signature,created_at) VALUES(?,?,?,?,?,?,?) "
                        + "ON CONFLICT (borrower_id,idempotency_key) DO NOTHING",
                id, loanId, borrowerId, amount, key, signature, java.sql.Timestamp.from(createdAt));
        return inserted==1 ? findEvent(id) : findByIdempotencyKey(borrowerId,key);
    }
    @Override public boolean verify(UUID eventId, UUID officerId) {
        return jdbc.update("UPDATE simulated_repayment_events SET status='VERIFIED',verified_by=?,verified_at=now() WHERE id=? AND status='PENDING'",
                officerId,eventId)==1;
    }
    @Override public void auditEvent(UUID actorId,String actorRole,String action,UUID eventId) {
        jdbc.update("INSERT INTO audit_log(actor_id,actor_role,action,target_type,target_id) VALUES(?,?,?,'SIMULATED_REPAYMENT_EVENT',?)",actorId,actorRole,action,eventId);
    }
    @Override public Repayment record(UUID eventId, UUID officerId, BigDecimal amount, BigDecimal balance, boolean completed) {
        UUID id = jdbc.queryForObject("INSERT INTO repayments(event_id,loan_id,amount_applied,resulting_balance,recorded_by) "
                + "SELECT e.id,e.loan_id,?,?,? FROM simulated_repayment_events e WHERE e.id=? RETURNING id", UUID.class,
                amount,balance,officerId,eventId);
        jdbc.update("UPDATE simulated_repayment_events SET status='RECORDED',recorded_at=now() WHERE id=? AND status='VERIFIED'",eventId);
        UUID loanId = jdbc.queryForObject("SELECT loan_id FROM simulated_repayment_events WHERE id=?",UUID.class,eventId);
        if (completed) {
            jdbc.update("UPDATE loans SET outstanding_balance=?,status='COMPLETED',completed_at=now(),version=version+1 WHERE id=? AND status='ACTIVE'",balance,loanId);
            jdbc.update("INSERT INTO loan_status_history(loan_id,from_status,to_status,changed_by) VALUES(?,'ACTIVE','COMPLETED',?)",loanId,officerId);
        } else {
            jdbc.update("UPDATE loans SET outstanding_balance=?,version=version+1 WHERE id=? AND status='ACTIVE'",balance,loanId);
        }
        jdbc.update("INSERT INTO audit_log(actor_id,actor_role,action,target_type,target_id,metadata) VALUES(?,'LOAN_OFFICER','REPAYMENT_RECORDED','LOAN',?,?::jsonb)",
                officerId,loanId,"{\"repaymentId\":\""+id+"\",\"eventId\":\""+eventId+"\",\"amountApplied\":\""+amount.toPlainString()+"\",\"resultingBalance\":\""+balance.toPlainString()+"\"}");
        blockchainOutbox.loanEvent(loanId,"REPAYMENT",id,"REPAYMENT_RECORDED",amount);
        if (completed) blockchainOutbox.loanEvent(loanId,"LOAN",loanId,"LOAN_COMPLETED",null);
        var rows = jdbc.query("SELECT id,event_id,loan_id,amount_applied,resulting_balance,recorded_by,recorded_at FROM repayments WHERE id=?",
                (rs,n)->new Repayment(rs.getObject("id",UUID.class),rs.getObject("event_id",UUID.class),rs.getObject("loan_id",UUID.class),rs.getBigDecimal("amount_applied"),rs.getBigDecimal("resulting_balance"),rs.getObject("recorded_by",UUID.class),rs.getTimestamp("recorded_at").toInstant()),id);
        return rows.getFirst();
    }
    private List<Event> events(String where,Object... args) { return jdbc.query("SELECT * FROM simulated_repayment_events WHERE "+where,this::mapEvent,args); }
    private List<Loan> loan(String where,Object... args) { return jdbc.query("SELECT id,borrower_id,outstanding_balance,status FROM loans WHERE "+where,this::mapLoan,args); }
    private Event mapEvent(ResultSet rs,int n) throws SQLException {
        return new Event(rs.getObject("id",UUID.class),rs.getObject("loan_id",UUID.class),rs.getObject("borrower_id",UUID.class),
                rs.getBigDecimal("amount"),rs.getObject("idempotency_key",UUID.class),rs.getString("signature"),rs.getString("status"),
                instant(rs,"created_at"),instant(rs,"source_verified_at"),rs.getObject("verified_by",UUID.class),instant(rs,"verified_at"),instant(rs,"recorded_at"));
    }
    private Loan mapLoan(ResultSet rs,int n) throws SQLException { return new Loan(rs.getObject("id",UUID.class),rs.getObject("borrower_id",UUID.class),rs.getBigDecimal("outstanding_balance"),rs.getString("status")); }
    private static Instant instant(ResultSet rs,String column) throws SQLException { var t=rs.getTimestamp(column); return t==null?null:t.toInstant(); }
}
