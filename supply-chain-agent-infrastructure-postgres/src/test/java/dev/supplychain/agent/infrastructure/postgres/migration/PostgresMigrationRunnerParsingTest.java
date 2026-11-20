package dev.supplychain.agent.infrastructure.postgres.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresMigrationRunnerParsingTest {
    @Test void splitsStatementsWithoutBreakingQuotedSemicolonsOrComments() {
        String sql = """
                -- ignored ; comment
                CREATE TABLE sample (id text PRIMARY KEY, value text);
                INSERT INTO sample(id, value) VALUES ('a;''b', 'it is fine'); /* ; ignored */
                UPDATE sample SET value = 'x;y' WHERE id = 'a;''b';
                """;
        List<String> statements = PostgresMigrationRunner.splitStatements(sql);
        assertEquals(3, statements.size());
        assertTrue(statements.get(1).contains("'a;''b'"));
        assertTrue(statements.get(2).contains("'x;y'"));
        assertFalse(statements.get(0).contains("ignored"));
    }

    @Test void rejectsUnterminatedSqlLiteralOrBlockComment() {
        assertThrows(IllegalArgumentException.class,
                () -> PostgresMigrationRunner.splitStatements("INSERT INTO x VALUES ('unterminated);"));
        assertThrows(IllegalArgumentException.class,
                () -> PostgresMigrationRunner.splitStatements("SELECT 1 /* unterminated"));
    }

    @Test void plannerSessionMigrationIsRegisteredAndSplitsIntoExpectedStatements() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V006__planner_session_checkpoints.sql")) {
            assertNotNull(stream, "V006 planner-session migration must be packaged in the infrastructure module");
            String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(5, PostgresMigrationRunner.splitStatements(sql).size());
        }
    }
    @Test void runtimeScopeMigrationIsRegisteredAndSplitsIntoExpectedStatements() throws Exception {
        assertEquals(10, PostgresMigrationRunner.latestSupportedVersion());
        assertEquals(10, PostgresMigrationRunner.supportedMigrationCount());
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V008__scope_runtime_operation_and_messaging_keys.sql")) {
            assertNotNull(stream, "V008 runtime scope migration must be packaged in the infrastructure module");
            String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            List<String> statements = PostgresMigrationRunner.splitStatements(sql);
            assertEquals(24, statements.size());
            assertTrue(sql.contains("PRIMARY KEY (scope_id, principal_id, action_id, idempotency_key)"));
            assertTrue(sql.contains("PRIMARY KEY (scope_id, consumer_id, message_id)"));
            assertTrue(sql.contains("PRIMARY KEY (scope_id, message_id)"));
        }
    }


    @Test void m8b4DurableBudgetMigrationIsRegisteredAndSplitsIntoExpectedStatements() throws Exception {
        assertEquals(10, PostgresMigrationRunner.latestSupportedVersion());
        assertEquals(10, PostgresMigrationRunner.supportedMigrationCount());
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V009__durable_b6_resource_budget_ledger.sql")) {
            assertNotNull(stream, "V009 M8-B4 durable B6 budget migration must be packaged");
            String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(8, PostgresMigrationRunner.splitStatements(sql).size());
            assertTrue(sql.contains("PRIMARY KEY (scope_id, execution_id)"));
            assertTrue(sql.contains("b6_resource_budget_reservation_item"));
            assertTrue(sql.contains("b6_resource_budget_actual_item"));
            assertTrue(sql.contains("ON DELETE RESTRICT"));
        }
    }

    @Test void r1DefFencedBudgetLeaseMigrationIsRegisteredAndSplitsIntoExpectedStatements() throws Exception {
        assertEquals(10, PostgresMigrationRunner.latestSupportedVersion());
        assertEquals(10, PostgresMigrationRunner.supportedMigrationCount());
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V010__fenced_b6_reservation_leases.sql")) {
            assertNotNull(stream, "V010 R1-DEF fenced B6 reservation migration must be packaged");
            String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(3, PostgresMigrationRunner.splitStatements(sql).size());
            assertTrue(sql.contains("lease_expires_at"));
            assertTrue(sql.contains("fencing_token"));
            assertTrue(sql.contains("started_at"));
            assertTrue(sql.contains("started_at IS NULL OR owner_id IS NOT NULL"));
            assertTrue(sql.contains("EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED"));
        }
    }

}
