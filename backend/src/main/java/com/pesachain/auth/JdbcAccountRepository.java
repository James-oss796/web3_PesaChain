package com.pesachain.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountRepository implements AccountRepository {
    private static final RowMapper<UserPrincipal> PRINCIPAL_MAPPER = (result, rowNum) ->
            new UserPrincipal(
                    result.getObject("id", UUID.class),
                    result.getString("email"),
                    result.getString("password_hash"),
                    result.getString("role"),
                    result.getString("status"),
                    result.getString("full_name"),
                    result.getString("phone"),
                    result.getBoolean("password_change_required"));

    private final JdbcTemplate jdbc;

    public JdbcAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean emailExists(String normalizedEmail) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE lower(email) = ?)",
                Boolean.class,
                normalizedEmail));
    }

    @Override
    public UUID createBorrower(String email, String passwordHash, String fullName, String phone) {
        UUID id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, role) VALUES (?, ?, 'BORROWER') RETURNING id",
                UUID.class,
                email,
                passwordHash);
        jdbc.update(
                "INSERT INTO borrower_profiles (user_id, full_name, phone) VALUES (?, ?, ?)",
                id,
                fullName,
                phone);
        recordAudit(id, "BORROWER", "BORROWER_REGISTERED", "USER", id);
        return id;
    }

    @Override
    public Optional<UserPrincipal> findByEmail(String email) {
        return queryPrincipal("u.email = ?", email);
    }

    @Override
    public Optional<UserPrincipal> findById(UUID id) {
        return queryPrincipal("u.id = ?", id);
    }

    private Optional<UserPrincipal> queryPrincipal(String predicate, Object value) {
        return jdbc.query(
                        "SELECT u.id, u.email, u.password_hash, u.role, u.status, "
                                + "p.full_name, p.phone, u.password_change_required "
                                + "FROM users u LEFT JOIN borrower_profiles p ON p.user_id = u.id "
                                + "WHERE " + predicate,
                        PRINCIPAL_MAPPER,
                        value)
                .stream()
                .findFirst();
    }

    @Override
    public void updateBorrowerProfile(UUID userId, String fullName, String phone) {
        jdbc.update(
                "UPDATE borrower_profiles SET full_name = ?, phone = ?, updated_at = now() WHERE user_id = ?",
                fullName,
                phone,
                userId);
        recordAudit(userId, "BORROWER", "BORROWER_PROFILE_UPDATED", "USER", userId);
    }

    @Override
    public void recordAudit(UUID actorId, String actorRole, String action, String targetType, UUID targetId) {
        jdbc.update(
                "INSERT INTO audit_log (actor_id, actor_role, action, target_type, target_id) "
                        + "VALUES (?, ?, ?, ?, ?)",
                actorId,
                actorRole,
                action,
                targetType,
                targetId);
    }
}
