package dev.supplychain.agent.infrastructure.postgres;

import dev.supplychain.agent.infrastructure.postgres.memory.PostgresEpisodeRepository;
import dev.supplychain.agent.infrastructure.postgres.memory.PostgresWorkingMemoryRepository;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.memory.episodic.EpisodeRepository;
import dev.supplychain.agent.memory.working.WorkingMemoryRepository;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Map;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;

/** Application composition for durable CoALA stores delivered in M7-A. */
public final class PostgresMemoryPersistence {
    private final DataSource dataSource;
    private final PostgresMigrationRunner migrations;
    private final WorkingMemoryRepository workingMemory;
    private final EpisodeRepository episodicMemory;

    /** Creates the module from deployment environment variables and applies pending migrations. */
    public static PostgresMemoryPersistence connectAndMigrate(Map<String, String> environment) {
        PostgresDatabaseConfig config = PostgresDatabaseConfig.fromEnvironment(environment);
        PostgresMemoryPersistence persistence = new PostgresMemoryPersistence(PostgresDataSourceFactory.create(config));
        persistence.migrate();
        return persistence;
    }

    public PostgresMemoryPersistence(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.migrations = new PostgresMigrationRunner(dataSource);
        this.workingMemory = new PostgresWorkingMemoryRepository(dataSource);
        this.episodicMemory = new PostgresEpisodeRepository(dataSource);
    }

    public PostgresMigrationRunner.MigrationResult migrate() { return migrations.migrate(); }
    public WorkingMemoryRepository workingMemoryRepository() { return workingMemory; }
    public EpisodeRepository episodeRepository() { return episodicMemory; }
    public DataSource dataSource() { return dataSource; }
}
