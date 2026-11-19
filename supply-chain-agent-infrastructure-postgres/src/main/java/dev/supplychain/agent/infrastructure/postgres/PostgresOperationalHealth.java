package dev.supplychain.agent.infrastructure.postgres;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Safe liveness/readiness and bounded-pool observations for deployment probes/metrics adapters. */
public final class PostgresOperationalHealth {
    public static final int REQUIRED_SCHEMA_VERSION = 7;
    private final DataSource dataSource;

    public PostgresOperationalHealth(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Liveness is process-local; transient database outages must not create restart storms. */
    public boolean liveness() { return true; }

    /** Readiness requires a reachable database and the exact migration level supported by this build. */
    public HealthSnapshot readiness() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version),0) FROM agent_schema_history")) {
            if (!result.next()) return snapshot(false, 0, "SCHEMA_QUERY_EMPTY");
            int version = result.getInt(1);
            if (version < REQUIRED_SCHEMA_VERSION) return snapshot(false, version, "SCHEMA_MIGRATION_REQUIRED");
            if (version > REQUIRED_SCHEMA_VERSION) return snapshot(false, version, "SCHEMA_NEWER_THAN_APPLICATION");
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
