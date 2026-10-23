package com.pesachain.loans;

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
public class JdbcLoanRepository implements LoanRepository {
    private final JdbcTemplate jdbc;
    private final BlockchainOutboxWriter blockchainOutbox;
    public JdbcLoanRepository(JdbcTemplate jdbc, BlockchainOutboxWriter blockchainOutbox) { this.jdbc=jdbc; this.blockchainOutbox=blockchainOutbox; }

    @Override public Optional<LoanView> findById(UUID loanId) { return find("id=?",loanId); }
    @Override public Optional<LoanView> findMineById(UUID borrowerId,UUID loanId) { return find("borrower_id=? AND id=?",borrowerId,loanId); }
    private Optional<LoanView> find(String where,Object... args) {
        return jdbc.query("SELECT * FROM loans WHERE "+where,(rs,n)->mapLoan(rs),args).stream().findFirst();
    }
    @Override public List<LoanView> findMine(UUID borrowerId,int limit,int offset) {
        return jdbc.query("SELECT * FROM loans WHERE borrower_id=? ORDER BY approved_at DESC,id LIMIT ? OFFSET ?",
                (rs,n)->mapLoan(rs),borrowerId,limit,offset);
    }
    @Override public long countMine(UUID borrowerId) {
        Long count=jdbc.queryForObject("SELECT count(*) FROM loans WHERE borrower_id=?",Long.class,borrowerId);
        return count==null?0:count;
    }
    @Override public List<LoanView> findAll(int limit,int offset) {
        return jdbc.query("SELECT * FROM loans ORDER BY approved_at DESC,id LIMIT ? OFFSET ?",
                (rs,n)->mapLoan(rs),limit,offset);
    }
    @Override public long countAll() {
        Long count=jdbc.queryForObject("SELECT count(*) FROM loans",Long.class);
        return count==null?0:count;
    }
    @Override public Optional<LockedLoan> lockById(UUID loanId) {
        return jdbc.query("SELECT id,principal,status FROM loans WHERE id=? FOR UPDATE",
                (rs,n)->new LockedLoan(rs.getObject("id",UUID.class),rs.getBigDecimal("principal"),rs.getString("status")),loanId).stream().findFirst();
    }
    @Override public boolean disburse(UUID loanId,UUID officerId,BigDecimal amount) {
        int changed=jdbc.update("INSERT INTO disbursements(loan_id,amount,recorded_by) "
                        + "SELECT id,?,? FROM loans WHERE id=? AND status='APPROVED' AND principal=?",
                amount,officerId,loanId,amount);
        if(changed!=1) return false;
        jdbc.update("UPDATE loans SET status='ACTIVE',disbursed_at=now(),activated_at=now(),version=version+1 WHERE id=? AND status='APPROVED'",loanId);
        jdbc.update("INSERT INTO loan_status_history(loan_id,from_status,to_status,changed_by) VALUES(?, 'APPROVED','DISBURSED',?)",loanId,officerId);
        jdbc.update("INSERT INTO loan_status_history(loan_id,from_status,to_status,changed_by) VALUES(?, 'DISBURSED','ACTIVE',?)",loanId,officerId);
        jdbc.update("INSERT INTO audit_log(actor_id,actor_role,action,target_type,target_id) VALUES(?,'LOAN_OFFICER','LOAN_DISBURSED','LOAN',?)",officerId,loanId);
        jdbc.update("INSERT INTO audit_log(actor_id,actor_role,action,target_type,target_id) VALUES(?,'LOAN_OFFICER','LOAN_ACTIVATED','LOAN',?)",officerId,loanId);
        blockchainOutbox.loanEvent(loanId,"DISBURSEMENT",loanId,"LOAN_DISBURSED",amount);
        return true;
    }

    private LoanView mapLoan(ResultSet rs) throws SQLException {
        UUID id=rs.getObject("id",UUID.class);
        return new LoanView(id,rs.getObject("application_id",UUID.class),rs.getObject("borrower_id",UUID.class),
                rs.getBigDecimal("principal"),rs.getBigDecimal("flat_rate_percent"),rs.getInt("term_months"),
                rs.getBigDecimal("interest_amount"),rs.getBigDecimal("total_repayable"),rs.getBigDecimal("outstanding_balance"),
                rs.getString("status"),instant(rs,"approved_at"),instant(rs,"disbursed_at"),instant(rs,"activated_at"),history(id));
    }
    private List<StatusEntry> history(UUID id) {
        return jdbc.query("SELECT from_status,to_status,changed_by,changed_at FROM loan_status_history WHERE loan_id=? ORDER BY sequence_id",
                (rs,n)->new StatusEntry(rs.getString("from_status"),rs.getString("to_status"),rs.getObject("changed_by",UUID.class),rs.getTimestamp("changed_at").toInstant()),id);
    }
    private static Instant instant(ResultSet rs,String field) throws SQLException {
        var timestamp=rs.getTimestamp(field); return timestamp==null?null:timestamp.toInstant();
    }
}
