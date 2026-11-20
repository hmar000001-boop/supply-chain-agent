package dev.supplychain.agent.infrastructure.postgres.migration;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Small PostgreSQL-specific migration runner for the repository-owned, ordered SQL scripts.
 * Migrations are checksummed, transactionally applied and serialized across application instances
 * with a PostgreSQL advisory lock. It intentionally supports plain DDL/DML scripts, not procedural
 * dollar-quoted function bodies.
 */
public final class PostgresMigrationRunner {
    private static final int LOCK_NAMESPACE = 0x534341; // "SCA"
    private static final int LOCK_KEY = 7;
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "coala_memory_schema", "db/migration/V001__coala_memory_schema.sql"),
            new Migration(2, "episode_idempotency", "db/migration/V002__episode_idempotency.sql"),
            new Migration(3, "memory_collection_order", "db/migration/V003__memory_collection_order.sql"),
            new Migration(4, "episode_evidence_order", "db/migration/V004__episode_evidence_order.sql"),
            new Migration(5, "ordered_memory_evidence_primary_keys", "db/migration/V005__ordered_memory_evidence_primary_keys.sql"),
            new Migration(6, "planner_session_checkpoints", "db/migration/V006__planner_session_checkpoints.sql"),
            new Migration(7, "durable_runtime_transactions_and_operations", "db/migration/V007__durable_runtime_transactions_and_operations.sql"),
            new Migration(8, "scope_runtime_operation_and_messaging_keys", "db/migration/V008__scope_runtime_operation_and_messaging_keys.sql"),
            new Migration(9, "durable_b6_resource_budget_ledger", "db/migration/V009__durable_b6_resource_budget_ledger.sql"),
            new Migration(10, "fenced_b6_reservation_leases", "db/migration/V010__fenced_b6_reservation_leases.sql"));

    private final DataSource dataSource;

    public PostgresMigrationRunner(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Highest schema version understood by this binary. */
    public static int latestSupportedVersion() { return MIGRATIONS.get(MIGRATIONS.size() - 1).version(); }

    /** Number of ordered migrations bundled with this binary. */
    public static int supportedMigrationCount() { return MIGRATIONS.size(); }

    public MigrationResult migrate() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            acquireLock(connection);
            try {
                // Serialize initial history-table creation as well as subsequent migrations.
                ensureHistoryTable(connection);
                rejectNewerSchema(connection);
                int applied = 0;
                int skipped = 0;
                for (Migration migration : MIGRATIONS) {
                    String checksum = checksum(readResource(migration.resource()));
                    String appliedChecksum = findChecksum(connection, migration.version());
                    if (appliedChecksum != null) {
                        if (!appliedChecksum.equals(checksum)) {
                            throw new IllegalStateException("migration checksum mismatch for version " + migration.version());
                        }
                        skipped++;
                        continue;
                    }
                    apply(connection, migration, checksum);
                    applied++;
                }
                return new MigrationResult(supportedMigrationCount(), applied, skipped);
            } finally {
                releaseLock(connection);
            }
        } catch (SQLException | IOException ex) {
            throw new PostgresPersistenceException("PostgreSQL schema migration failed", ex);
        }
    }

    private static void ensureHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS agent_schema_history (
                        version          integer PRIMARY KEY,
                        description      varchar(256) NOT NULL,
                        script           varchar(512) NOT NULL,
                        checksum_sha256  char(64) NOT NULL,
                        installed_at     timestamptz NOT NULL,
                        execution_ms     bigint NOT NULL CHECK (execution_ms >= 0)
                    )
                    """);
        }
    }

    private static void acquireLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_lock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_KEY);
            statement.execute();
        }
    }

    private static void releaseLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_KEY);
            statement.execute();
        }
    }

    private static void rejectNewerSchema(Connection connection) throws SQLException {
        int latestSupported = latestSupportedVersion();
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM agent_schema_history")) {
            if (row.next() && row.getInt(1) > latestSupported) {
                throw new IllegalStateException("database schema is newer than this application supports");
            }
        }
    }

    private static String findChecksum(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT checksum_sha256 FROM agent_schema_history WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1).trim() : null;
            }
        }
    }

    private static void apply(Connection connection, Migration migration, String checksum) throws SQLException, IOException {
        String sql = readResource(migration.resource());
        long started = System.nanoTime();
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String command : splitStatements(sql)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(command);
                }
            }
            long elapsedMillis = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
            try (PreparedStatement history = connection.prepareStatement("""
                    INSERT INTO agent_schema_history(version, description, script, checksum_sha256, installed_at, execution_ms)
                    VALUES (?, ?, ?, ?, clock_timestamp(), ?)
                    """)) {
                history.setInt(1, migration.version());
                history.setString(2, migration.description());
                history.setString(3, migration.resource());
                history.setString(4, checksum);
                history.setLong(5, elapsedMillis);
                history.executeUpdate();
            }
            connection.commit();
        } catch (SQLException | RuntimeException ex) {
            try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream input = PostgresMigrationRunner.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("migration resource not found: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String checksum(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Splits the current migration language while respecting SQL strings and comments. */
    static List<String> splitStatements(String script) {
        Objects.requireNonNull(script, "script");
        List<String> commands = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (lineComment) {
                if (ch == '\n') { lineComment = false; current.append(ch); }
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') { blockComment = false; i++; }
                continue;
            }
            if (!singleQuoted && !doubleQuoted && ch == '-' && next == '-') {
                lineComment = true; i++; continue;
            }
            if (!singleQuoted && !doubleQuoted && ch == '/' && next == '*') {
                blockComment = true; i++; continue;
            }
            if (ch == '\'' && !doubleQuoted) {
                current.append(ch);
                if (singleQuoted && next == '\'') { current.append(next); i++; }
                else singleQuoted = !singleQuoted;
                continue;
            }
            if (ch == '"' && !singleQuoted) { doubleQuoted = !doubleQuoted; current.append(ch); continue; }
            if (ch == ';' && !singleQuoted && !doubleQuoted) {
                addCommand(commands, current);
                continue;
            }
            current.append(ch);
        }
        if (singleQuoted || doubleQuoted || blockComment) throw new IllegalArgumentException("unterminated SQL literal/comment in migration");
        addCommand(commands, current);
        return List.copyOf(commands);
    }

    private static void addCommand(List<String> commands, StringBuilder current) {
        String command = current.toString().trim();
        current.setLength(0);
        if (!command.isEmpty()) commands.add(command);
    }

    private record Migration(int version, String description, String resource) { }
    public record MigrationResult(int totalMigrations, int appliedMigrations, int skippedMigrations) { }
}
