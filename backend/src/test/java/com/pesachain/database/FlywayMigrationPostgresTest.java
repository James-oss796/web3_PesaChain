package com.pesachain.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine3.24")
            .withDatabaseName("pesachain_test")
            .withUsername("pesachain_test")
            .withPassword("local-test-only");

    @Test
    void appliesAllMigrationsToARealPostgresqlDatabase() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            try (var migrations = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
                assertTrue(migrations.next());
                assertEquals(10, migrations.getInt(1));
            }
            try (var tables = statement.executeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                    + "AND table_name IN ('users', 'borrower_profiles', 'audit_log', 'loan_applications', 'simulated_repayment_events', 'repayments', 'blockchain_outbox', 'blockchain_event_attempts', 'blockchain_reconciliation_findings')")) {
                assertTrue(tables.next());
                assertEquals(9, tables.getInt(1));
            }
            try (var columns = statement.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='loan_applications' AND column_name='idempotency_key'")) {
                assertTrue(columns.next());
                assertEquals(1, columns.getInt(1));
            }
        }
    }

    @Test
    void additiveV10UpgradePreservesRowsFromAnAlreadyMigratedV7Schema() throws Exception {
        String schema = "v7_upgrade_test";
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("7")
                .locations("classpath:db/migration").load().migrate();

        UUID retainedId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var insert = connection.prepareStatement("INSERT INTO " + schema + ".users(id,email,password_hash,role) VALUES(?,?,?,'BORROWER')")) {
            insert.setObject(1, retainedId);
            insert.setString(2, "retained-" + retainedId + "@example.test");
            insert.setString(3, "test-hash");
            insert.executeUpdate();
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            try (var user = statement.executeQuery("SELECT count(*) FROM " + schema + ".users WHERE id='" + retainedId + "'")) {
                assertTrue(user.next());
                assertEquals(1, user.getInt(1));
            }
            try (var version = statement.executeQuery("SELECT version FROM " + schema + ".flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")) {
                assertTrue(version.next());
                assertEquals("10", version.getString(1));
            }
            try (var column = statement.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='loan_applications' AND column_name='idempotency_key'")) {
                assertTrue(column.next());
                assertEquals(1, column.getInt(1));
            }
        }
    }
}
