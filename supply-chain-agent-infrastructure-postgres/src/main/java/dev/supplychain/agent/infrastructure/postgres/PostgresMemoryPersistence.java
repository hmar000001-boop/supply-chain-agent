package dev.supplychain.agent.infrastructure.postgres;

import dev.supplychain.agent.infrastructure.postgres.memory.PostgresEpisodeRepository;
import dev.supplychain.agent.infrastructure.postgres.memory.PostgresWorkingMemoryRepository;
import dev.supplychain.agent.infrastructure.postgres.memory.PostgresSemanticFactRepository;
import dev.supplychain.agent.infrastructure.postgres.memory.PostgresProceduralMemoryRepository;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.memory.episodic.EpisodeRepository;
import dev.supplychain.agent.memory.working.WorkingMemoryRepository;
import dev.supplychain.agent.memory.semantic.SemanticFactRepository;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryRepository;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Map;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;

/** Application composition for all four durable CoALA memory repositories. */
public final class PostgresMemoryPersistence {
    private final DataSource dataSource;
    private final PostgresMigrationRunner migrations;
    private final WorkingMemoryRepository workingMemory;
    private final EpisodeRepository episodicMemory;
    private final SemanticFactRepository semanticMemory;
    private final ProceduralMemoryRepository proceduralMemory;

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
        this.semanticMemory = new PostgresSemanticFactRepository(dataSource);
        this.proceduralMemory = new PostgresProceduralMemoryRepository(dataSource);
    }

    public PostgresMigrationRunner.MigrationResult migrate() { return migrations.migrate(); }
    public WorkingMemoryRepository workingMemoryRepository() { return workingMemory; }
    public EpisodeRepository episodeRepository() { return episodicMemory; }
    public SemanticFactRepository semanticFactRepository() { return semanticMemory; }
    public ProceduralMemoryRepository proceduralMemoryRepository() { return proceduralMemory; }
    public DataSource dataSource() { return dataSource; }
}
