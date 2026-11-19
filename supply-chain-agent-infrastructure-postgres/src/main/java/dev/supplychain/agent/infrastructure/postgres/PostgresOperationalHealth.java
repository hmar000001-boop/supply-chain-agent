package dev.supplychain.agent.infrastructure.postgres;

import com.zaxxer.hikari.HikariDataSource;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Safe liveness/readiness and bounded-pool observations for deployment probes/metrics adapters. */
public final class PostgresOperationalHealth {
    public static final int REQUIRED_SCHEMA_VERSION = PostgresMigrationRunner.latestSupportedVersion();
    private final DataSource dataSource;

    public PostgresOperationalHealth(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Liveness is process-local; transient database outages must not create restart storms. */
    public boolean liveness() { return true; }

    /** Readiness requires a reachable database and a complete, contiguous migration history. */
    public HealthSnapshot readiness() {
        String query = "SELECT COUNT(*) AS migration_count,COUNT(DISTINCT version) AS distinct_count,"
                + "COALESCE(MIN(version),0) AS minimum_version,COALESCE(MAX(version),0) AS maximum_version "
                + "FROM agent_schema_history";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            if (!result.next()) return snapshot(false, 0, "SCHEMA_QUERY_EMPTY");
            int count = result.getInt("migration_count");
            int distinct = result.getInt("distinct_count");
            int minimum = result.getInt("minimum_version");
            int version = result.getInt("maximum_version");
            if (version > REQUIRED_SCHEMA_VERSION) return snapshot(false, version, "SCHEMA_NEWER_THAN_APPLICATION");
            if (version < REQUIRED_SCHEMA_VERSION) return snapshot(false, version, "SCHEMA_MIGRATION_REQUIRED");
            if (minimum != 1 || count != PostgresMigrationRunner.supportedMigrationCount() || distinct != count) {
                return snapshot(false, version, "SCHEMA_HISTORY_INCOMPLETE");
            }
            return snapshot(true, version, "READY");
        } catch (SQLException ex) {
            return snapshot(false, 0, "DATABASE_UNAVAILABLE");
        }
    }

    private HealthSnapshot snapshot(boolean ready, int schemaVersion, String code) {
        PoolSnapshot pool = null;
        if (dataSource instanceof HikariDataSource hikari) {
            var bean = hikari.getHikariPoolMXBean();
            if (bean != null) {
                pool = new PoolSnapshot(bean.getTotalConnections(), bean.getIdleConnections(),
                        bean.getActiveConnections(), bean.getThreadsAwaitingConnection(), hikari.getMaximumPoolSize());
            }
        }
        return new HealthSnapshot(true, ready, schemaVersion, REQUIRED_SCHEMA_VERSION, code, pool);
    }

    public record HealthSnapshot(boolean live, boolean ready, int schemaVersion,
                                 int requiredSchemaVersion, String statusCode, PoolSnapshot pool) { }
    public record PoolSnapshot(int totalConnections, int idleConnections, int activeConnections,
                               int threadsAwaitingConnection, int maximumPoolSize) { }
}
