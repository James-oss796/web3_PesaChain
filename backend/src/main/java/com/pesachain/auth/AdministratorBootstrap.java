package com.pesachain.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AdministratorBootstrap implements ApplicationRunner {
    private final Environment environment;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    public AdministratorBootstrap(Environment environment, JdbcTemplate jdbc, PasswordEncoder encoder, org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.environment=environment; this.jdbc=jdbc; this.encoder=encoder; this.transactions=new TransactionTemplate(transactionManager);
    }
    @Override public void run(ApplicationArguments args) {
        String email=environment.getProperty("PESACHAIN_BOOTSTRAP_ADMIN_EMAIL");
        String password=environment.getProperty("PESACHAIN_BOOTSTRAP_ADMIN_PASSWORD");
        if ((email == null || email.isBlank()) && (password == null || password.isBlank())) return;
        if (email == null || email.isBlank() || password == null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("Bootstrap administrator configuration is incomplete or invalid.");
        transactions.executeWithoutResult(status -> bootstrap(email.trim().toLowerCase(Locale.ROOT), password));
    }
    void bootstrap(String email,String password) {
        jdbc.execute("SELECT pg_advisory_xact_lock(82736411)");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE role='ADMINISTRATOR')",Boolean.class))) return;
        UUID id=jdbc.queryForObject("INSERT INTO users(email,password_hash,role,password_change_required) VALUES(?,?,'ADMINISTRATOR',true) RETURNING id",UUID.class,email,encoder.encode(password));
        // Bootstrap is a system-initiated action rather than an authenticated role. The audit
        // schema intentionally permits a null actor and restricts actor_role to user roles.
        jdbc.update("INSERT INTO audit_log(actor_id,actor_role,action,target_type,target_id,metadata) VALUES(NULL,NULL,'ADMINISTRATOR_BOOTSTRAPPED','USER',?,'{}'::jsonb)",id);
    }
}
