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
}
