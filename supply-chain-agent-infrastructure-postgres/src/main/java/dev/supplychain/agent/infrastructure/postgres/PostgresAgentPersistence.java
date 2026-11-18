package dev.supplychain.agent.infrastructure.postgres;

import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.infrastructure.postgres.planning.PostgresPlannerSessionStore;
import dev.supplychain.agent.planning.session.PlannerSessionStore;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;

/** Single-datasource composition root for durable CoALA memory and resumable planning sessions. */
public final class PostgresAgentPersistence {
    private final DataSource dataSource;
    private final PostgresMigrationRunner migrations;
    private final PostgresMemoryPersistence memory;
    private final PlannerSessionStore plannerSessions;

    public static PostgresAgentPersistence connectAndMigrate(Map<String, String> environment) {
        PostgresDatabaseConfig config = PostgresDatabaseConfig.fromEnvironment(environment);
        PostgresAgentPersistence persistence = new PostgresAgentPersistence(PostgresDataSourceFactory.create(config));
        persistence.migrate();
        return persistence;
    }

    public PostgresAgentPersistence(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.migrations = new PostgresMigrationRunner(dataSource);
        this.memory = new PostgresMemoryPersistence(dataSource);
        this.plannerSessions = new PostgresPlannerSessionStore(dataSource);
    }

    public PostgresMigrationRunner.MigrationResult migrate() { return migrations.migrate(); }
    public PostgresMemoryPersistence memory() { return memory; }
    public PlannerSessionStore plannerSessionStore() { return plannerSessions; }
    public DataSource dataSource() { return dataSource; }
}
