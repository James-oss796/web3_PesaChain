package com.pesachain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "pesachain.blockchain.worker.enabled=false")
class AdministratorBootstrapIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine3.24")
            .withDatabaseName("pesachain_bootstrap_test")
            .withUsername("pesachain_test")
            .withPassword("local-test-only");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AdministratorBootstrap bootstrap;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void bootstrapCreatesAdministratorAndSchemaValidSystemAuditEntry() {
        String email = "bootstrap-" + java.util.UUID.randomUUID() + "@example.test";
        bootstrap.bootstrap(email, "test-only-bootstrap-password-42");

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email=? AND role='ADMINISTRATOR' AND password_change_required=true", Integer.class, email));
        String actorRole = jdbc.queryForObject("SELECT actor_role FROM audit_log WHERE action='ADMINISTRATOR_BOOTSTRAPPED' AND target_id=(SELECT id FROM users WHERE email=?)", String.class, email);
        assertNull(actorRole);
        assertTrue(jdbc.queryForObject("SELECT actor_id IS NULL FROM audit_log WHERE action='ADMINISTRATOR_BOOTSTRAPPED' AND target_id=(SELECT id FROM users WHERE email=?)", Boolean.class, email));
    }
}
