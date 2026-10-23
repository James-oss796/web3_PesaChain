package com.pesachain.auth;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    boolean emailExists(String normalizedEmail);

    UUID createBorrower(String normalizedEmail, String passwordHash, String fullName, String phone);

    Optional<UserPrincipal> findByEmail(String normalizedEmail);

    Optional<UserPrincipal> findById(UUID id);

    void updateBorrowerProfile(UUID userId, String fullName, String phone);

    void recordAudit(UUID actorId, String actorRole, String action, String targetType, UUID targetId);
}
