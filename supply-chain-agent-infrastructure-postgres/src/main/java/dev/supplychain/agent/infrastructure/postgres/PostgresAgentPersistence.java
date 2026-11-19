package dev.supplychain.agent.infrastructure.postgres;

import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPooledDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPoolSettings;
import com.zaxxer.hikari.HikariDataSource;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.infrastructure.postgres.planning.PostgresPlannerSessionStore;
import dev.supplychain.agent.planning.session.PlannerSessionStore;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRuntimeCodecs;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRuntimePersistence;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;

/** Single-datasource composition root for durable CoALA memory and resumable planning sessions. */
public final class PostgresAgentPersistence implements AutoCloseable {
    private final DataSource dataSource;
    private final PostgresMigrationRunner migrations;
    private final PostgresMemoryPersistence memory;
    private final PlannerSessionStore plannerSessions;
    private final PostgresOperationalHealth operationalHealth;

    public static PostgresAgentPersistence connectAndMigrate(Map<String, String> environment) {
        PostgresDatabaseConfig config = PostgresDatabaseConfig.fromEnvironment(environment);
        PostgresAgentPersistence persistence = new PostgresAgentPersistence(
                PostgresPooledDataSourceFactory.create(config, PostgresPoolSettings.productionDefaults()));
        persistence.migrate();
        return persistence;
    }

    public PostgresAgentPersistence(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.migrations = new PostgresMigrationRunner(dataSource);
        this.memory = new PostgresMemoryPersistence(dataSource);
        this.plannerSessions = new PostgresPlannerSessionStore(dataSource);
        this.operationalHealth = new PostgresOperationalHealth(dataSource);
    }

    public PostgresMigrationRunner.MigrationResult migrate() { return migrations.migrate(); }
    public PostgresMemoryPersistence memory() { return memory; }
    public PlannerSessionStore plannerSessionStore() { return plannerSessions; }
    public DataSource dataSource() { return dataSource; }
    public PostgresOperationalHealth.HealthSnapshot readiness() { return operationalHealth.readiness(); }
    public boolean liveness() { return operationalHealth.liveness(); }
    /** Creates scope-bound M5/M6/B2/B4 durable stores over the same pooled DataSource. */
    public PostgresRuntimePersistence runtime(String scopeId, PostgresRuntimeCodecs codecs) {
        return new PostgresRuntimePersistence(dataSource, scopeId, codecs);
    }

    /** Closes the pool when this composition root owns a pooled DataSource. */
    @Override public void close() {
        if (dataSource instanceof HikariDataSource pool) pool.close();
    }
}
