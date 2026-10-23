package com.pesachain.auth;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AdminUserService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Transactional
    public StaffUser create(UserPrincipal actor, String email, String password, String role) {
        validateStaffRole(role);
        jdbc.execute("SELECT pg_advisory_xact_lock(82736411)");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Password is too long.");
        UUID id = jdbc.queryForObject("INSERT INTO users(email,password_hash,role,password_change_required) VALUES(?,?,?,true) RETURNING id", UUID.class, normalized, encoder.encode(password), role);
        audit(actor, "STAFF_ACCOUNT_CREATED", id, Map.of("role", role));
        return get(id);
    }

    public List<StaffUser> list() {
        return jdbc.query("SELECT id,email,role,status,created_at FROM users WHERE role IN ('ADMINISTRATOR','LOAN_OFFICER') ORDER BY created_at DESC", (rs, n) -> new StaffUser(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("role"), rs.getString("status"), rs.getTimestamp("created_at").toInstant()));
    }

    @Transactional
    public StaffUser update(UserPrincipal actor, UUID id, String role, String status) {
        jdbc.execute("SELECT pg_advisory_xact_lock(82736411)");
        var current = jdbc.query("SELECT role,status FROM users WHERE id=? FOR UPDATE", (rs,n) -> Map.entry(rs.getString(1),rs.getString(2)), id).stream().findFirst().orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"NOT_FOUND","Account was not found."));
        if (id.equals(actor.id()) && ((role != null && !role.equals(current.getKey())) || (status != null && !status.equals(current.getValue())))) throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"SELF_CHANGE_DENIED","You cannot change your own role or status.");
        if (role != null) validateStaffRole(role);
        if (status != null && !List.of("ACTIVE","SUSPENDED").contains(status)) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Status must be ACTIVE or SUSPENDED.");
        String nextRole = role == null ? current.getKey() : role;
        String nextStatus = status == null ? current.getValue() : status;
        if ("ADMINISTRATOR".equals(current.getKey()) && "ACTIVE".equals(current.getValue()) && (!"ADMINISTRATOR".equals(nextRole) || !"ACTIVE".equals(nextStatus))) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE role='ADMINISTRATOR' AND status='ACTIVE' AND id<>?", Integer.class, id);
            if (count == 0) throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"LAST_ADMINISTRATOR","At least one active administrator must remain.");
        }
        jdbc.update("UPDATE users SET role=?,status=? WHERE id=?", nextRole, nextStatus, id);
        if (!nextRole.equals(current.getKey())) audit(actor,"USER_ROLE_CHANGED",id,Map.of("before",current.getKey(),"after",nextRole));
        if (!nextStatus.equals(current.getValue())) audit(actor,"USER_STATUS_CHANGED",id,Map.of("before",current.getValue(),"after",nextStatus));
        return get(id);
    }

    private StaffUser get(UUID id) { return jdbc.queryForObject("SELECT id,email,role,status,created_at FROM users WHERE id=?",(rs,n)->new StaffUser(rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("role"),rs.getString("status"),rs.getTimestamp("created_at").toInstant()),id); }
    private void audit(UserPrincipal actor,String action,UUID id,Map<String,String> metadata) {
        jdbc.update("INSERT INTO audit_log(actor_id,actor_role,action,target_type,target_id,metadata) VALUES(?,?,?,'USER',?,?::jsonb)",actor.id(),actor.role(),action,id,new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(metadata).toString());
    }
    private static void validateRole(String role) { if (!List.of("ADMINISTRATOR","LOAN_OFFICER","BORROWER").contains(role)) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Role is invalid."); }
    private static void validateStaffRole(String role) { validateRole(role); if ("BORROWER".equals(role)) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","This endpoint provisions staff accounts only."); }
    public record StaffUser(UUID id,String email,String role,String status,java.time.Instant createdAt) {}
}
