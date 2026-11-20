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
        try {
            persistence.migrate();
            return persistence;
        } catch (RuntimeException failure) {
            persistence.close();
            throw failure;
        }
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

    /** Scope-bound durable B2 typed recovery procedure drafts and version history. */
    public dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRecoveryProcedureDraftRepository recoveryProcedureDrafts(
            String scopeId, dev.supplychain.agent.infrastructure.postgres.runtime.JsonCodec<dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraft> codec) {
        return new dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRecoveryProcedureDraftRepository(dataSource, scopeId, codec);
    }

    /** Scope-bound append-only B3 consumer receipts and separately verified downstream outcomes. */
    public dev.supplychain.agent.infrastructure.postgres.runtime.PostgresB3OutcomeEvidenceRepository b3OutcomeEvidence(
            String scopeId,
            dev.supplychain.agent.infrastructure.postgres.runtime.JsonCodec<dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence> receiptCodec,
            dev.supplychain.agent.infrastructure.postgres.runtime.JsonCodec<dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence> outcomeCodec) {
        return new dev.supplychain.agent.infrastructure.postgres.runtime.PostgresB3OutcomeEvidenceRepository(dataSource, scopeId, receiptCodec, outcomeCodec);
    }

    /** Closes the pool when this composition root owns a pooled DataSource. */
    @Override public void close() {
        if (dataSource instanceof HikariDataSource pool) pool.close();
    }
}
