package com.pesachain.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements UserDetailsService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public AuthService(AccountRepository accounts, PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID register(String email, String password, String fullName, String phone) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = fullName.trim();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Password is too long.");
        }
        if (normalizedName.length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Name must contain at least two characters.");
        }
        if (accounts.emailExists(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with that email already exists.");
        }
        try {
            return accounts.createBorrower(
                    normalizedEmail,
                    passwordEncoder.encode(password),
                    normalizedName,
                    phone.trim());
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with that email already exists.");
        }
    }

    @Override
    public UserPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {
        return accounts.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    public UserPrincipal getAccount(UUID userId) {
        return accounts.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication is required."));
    }

    @Transactional
    public UserPrincipal changePassword(UUID userId, String current, String next) {
        UserPrincipal user=getAccount(userId);
        if(!passwordEncoder.matches(current,user.passwordHash())) throw new ApiException(HttpStatus.BAD_REQUEST,"PASSWORD_MISMATCH","Current password is incorrect.");
        if(next.getBytes(StandardCharsets.UTF_8).length>72) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Password is too long.");
        jdbc.update("UPDATE users SET password_hash=?,password_change_required=false WHERE id=?",passwordEncoder.encode(next),userId);
        jdbc.update("INSERT INTO audit_log(actor_id,actor_role,action,target_type,target_id) VALUES(?,?,'PASSWORD_CHANGED','USER',?)",userId,user.role(),userId);
        return getAccount(userId);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
