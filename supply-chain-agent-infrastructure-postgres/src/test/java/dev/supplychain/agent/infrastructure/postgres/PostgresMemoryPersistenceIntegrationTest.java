package dev.supplychain.agent.infrastructure.postgres;

import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodeEvent;
import dev.supplychain.agent.memory.episodic.EpisodeId;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.episodic.EpisodeQuery;
import dev.supplychain.agent.memory.working.Hypothesis;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class PostgresMemoryPersistenceIntegrationTest {
    private static DataSource dataSource;
    private static PostgresMemoryPersistence persistence;

    @BeforeAll static void initializeSchema() {
        Map<String, String> env = System.getenv();
        PostgresDatabaseConfig config = PostgresDatabaseConfig.fromEnvironment(env);
        dataSource = PostgresDataSourceFactory.create(config);
        persistence = new PostgresMemoryPersistence(dataSource);
        PostgresMigrationRunner.MigrationResult result = persistence.migrate();
        assertEquals(6, result.totalMigrations());
        PostgresMigrationRunner.MigrationResult second = persistence.migrate();
        assertEquals(0, second.appliedMigrations(), "migration rerun must be idempotent");
        assertEquals(6, second.skippedMigrations());
    }

    @Test void workingMemoryRoundTripsTypedValuesEvidenceOrderingAndHypothesesAcrossRepositoryInstances() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemoryScopeId scope = new MemoryScopeId("ci-working-" + UUID.randomUUID());
        WorkingMemoryId id = WorkingMemoryId.random();
        WorkingMemorySnapshot snapshot = WorkingMemorySnapshot.empty(id, scope, now, now.plus(2, ChronoUnit.HOURS));
        EvidenceReference zEvidence = new EvidenceReference("shipment-event", "evt-z");
        EvidenceReference aEvidence = new EvidenceReference("supplier-update", "evt-a");
        EvidenceReference decimalEvidence = new EvidenceReference("inventory-snapshot", "inv-1");
        EvidenceReference hypothesisEvidence = new EvidenceReference("quality-report", "qa-3");
        snapshot = snapshot.put("z-first", new MemoryValue.Text("late shipment \"north\" \\ dock"),
                List.of(zEvidence, aEvidence, zEvidence), now.plusSeconds(1), now.plus(3, ChronoUnit.HOURS));
        snapshot = snapshot.put("a-second", new MemoryValue.TextList(List.of("alpha", "b\"eta", "line\nbreak", "slash\\")),
                List.of(aEvidence), now.plusSeconds(2), now.plus(3, ChronoUnit.HOURS));
        snapshot = snapshot.put("decimal", new MemoryValue.Decimal(new BigDecimal("1234.5600")),
                List.of(decimalEvidence), now.plusSeconds(3), now.plus(3, ChronoUnit.HOURS));
        snapshot = snapshot.put("flag", new MemoryValue.Flag(true), List.of(), now.plusSeconds(4), now.plus(3, ChronoUnit.HOURS));
        snapshot = snapshot.put("timestamp", new MemoryValue.Timestamp(now.plusSeconds(4)), List.of(),
                now.plusSeconds(5), now.plus(3, ChronoUnit.HOURS));
        snapshot = snapshot.put("entity", new MemoryValue.EntityLink(new EntityReference("supplier", "SUP-77")),
                List.of(hypothesisEvidence), now.plusSeconds(6), now.plus(3, ChronoUnit.HOURS));
        Hypothesis hypothesis = Hypothesis.proposed("Supplier capacity may be below commitment", 0.72, now.plusSeconds(1))
                .support(List.of(hypothesisEvidence, zEvidence, hypothesisEvidence), now.plusSeconds(7));
        snapshot = snapshot.putHypothesis(hypothesis, now.plusSeconds(7), now.plus(3, ChronoUnit.HOURS));

        persistence.workingMemoryRepository().create(snapshot);
        WorkingMemorySnapshot loaded = new PostgresMemoryPersistence(dataSource).workingMemoryRepository()
                .findById(id).orElseThrow();
        assertEquals(snapshot, loaded, "durable reload must preserve all typed values and collection order");
        assertEquals(List.of("z-first", "a-second", "decimal", "flag", "timestamp", "entity"), List.copyOf(loaded.values().keySet()));

        WorkingMemorySnapshot replacement = loaded.put("active-plan", new MemoryValue.Text("candidate-42"),
                List.of(new EvidenceReference("candidate-set", "set-42")), now.plusSeconds(7), now.plus(4, ChronoUnit.HOURS));
        persistence.workingMemoryRepository().compareAndSet(id, loaded.version(), replacement);
        assertEquals(replacement, persistence.workingMemoryRepository().findById(id).orElseThrow());
        assertThrows(MemoryVersionConflictException.class,
                () -> persistence.workingMemoryRepository().compareAndSet(id, loaded.version(), replacement));
    }

    @Test void workingMemoryRepositoryPreservesTheDomainImmediateExpiryBoundary() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemoryScopeId scope = new MemoryScopeId("ci-working-expired-" + UUID.randomUUID());
        WorkingMemoryId id = WorkingMemoryId.random();
        WorkingMemorySnapshot immediatelyExpired = WorkingMemorySnapshot.empty(id, scope, now, now);
        persistence.workingMemoryRepository().create(immediatelyExpired);
        assertEquals(immediatelyExpired, persistence.workingMemoryRepository().findById(id).orElseThrow());
        assertEquals(1, persistence.workingMemoryRepository().deleteExpired(now));
        assertTrue(persistence.workingMemoryRepository().findById(id).isEmpty());
    }

    @Test void episodicMemorySupportsAtomicIdempotencyReviewHistoricalQueriesAndRetention() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemoryScopeId scope = new MemoryScopeId("ci-episode-" + UUID.randomUUID());
        EvidenceReference idempotency = new EvidenceReference("operation", "op-" + UUID.randomUUID());
        EvidenceReference eventEvidence = new EvidenceReference("carrier-event", "carrier-1");
        EvidenceReference secondEvidence = new EvidenceReference("supplier-confirmation", "conf-2");
        Provenance provenance = new Provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM, "wms-event-1",
                now, now.plusSeconds(120), "wms-v4");
        EpisodeEvent event = new EpisodeEvent("SHIPMENT_DELAYED", "Carrier reported a terminal delay",
                now.plusSeconds(30), List.of(eventEvidence, secondEvidence, eventEvidence));
        Episode proposed = Episode.recorded(EpisodeId.random(), scope, "Carrier delay", "Order SO-100 shipment delayed",
                EpisodeOutcome.PARTIAL_SUCCESS, now, now.plusSeconds(90), provenance.recordedAt(),
                now.plus(7, ChronoUnit.DAYS), List.of(new EntityReference("order", "SO-100"),
                        new EntityReference("shipment", "SH-100"), new EntityReference("order", "SO-100")),
                List.of(event), List.of(idempotency), provenance);

        Episode stored = persistence.episodeRepository().appendIdempotent(proposed, idempotency);
        Episode duplicateRequest = new Episode(new EpisodeId("different-retry-id"), proposed.scopeId(), 0,
                proposed.title(), proposed.summary(), proposed.outcome(), proposed.status(), proposed.startedAt(),
                proposed.completedAt(), proposed.recordedAt(), proposed.retentionUntil(), proposed.entities(),
                proposed.events(), proposed.evidence(), proposed.provenance(), null, null, null, null);
        Episode same = new PostgresMemoryPersistence(dataSource).episodeRepository()
                .appendIdempotent(duplicateRequest, idempotency);
        assertEquals(stored.id(), same.id(), "same idempotency payload must return the first committed episode");
        assertEquals(proposed.evidence(), same.evidence());
        assertEquals(proposed.entities(), same.entities());
        assertEquals(proposed.events(), same.events(), "event evidence order and duplicate references must survive reload");

        Episode conflictingRetry = new Episode(EpisodeId.random(), proposed.scopeId(), 0,
                proposed.title(), "different outcome payload", EpisodeOutcome.FAILURE, proposed.status(),
                proposed.startedAt(), proposed.completedAt(), proposed.recordedAt(), proposed.retentionUntil(),
                proposed.entities(), proposed.events(), proposed.evidence(), proposed.provenance(), null, null, null, null);
        assertThrows(MemoryConflictException.class,
                () -> persistence.episodeRepository().appendIdempotent(conflictingRetry, idempotency));

        EvidenceReference review = new EvidenceReference("human-review", "review-" + UUID.randomUUID());
        Episode verified = stored.verify("reviewer-1", review, now.plusSeconds(180));
        persistence.episodeRepository().compareAndSet(stored.id(), stored.version(), verified);
        Episode reloaded = persistence.episodeRepository().findById(stored.id()).orElseThrow();
        assertEquals(verified, reloaded);
        Episode retryAfterReview = persistence.episodeRepository().appendIdempotent(duplicateRequest, idempotency);
        assertEquals(reloaded, retryAfterReview, "a retry after review must return the existing lifecycle state");
        assertEquals(List.of(reloaded), persistence.episodeRepository().findVerified(
                new EpisodeQuery(scope, new EntityReference("order", "SO-100"), now.plusSeconds(200), 10)));

        Episode superseded = reloaded.supersede(now.plusSeconds(240));
        persistence.episodeRepository().compareAndSet(reloaded.id(), reloaded.version(), superseded);
        assertEquals(List.of(superseded), persistence.episodeRepository().findVerified(
                new EpisodeQuery(scope, new EntityReference("order", "SO-100"), now.plusSeconds(210), 10)));
        assertTrue(persistence.episodeRepository().findVerified(
                new EpisodeQuery(scope, new EntityReference("order", "SO-100"), now.plusSeconds(250), 10)).isEmpty());
    }

    @Test void unverifiedEpisodeRetentionPurgesOnlyEligibleRows() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemoryScopeId scope = new MemoryScopeId("ci-retention-" + UUID.randomUUID());
        Provenance provenance = new Provenance(ProvenanceSource.SIMULATOR, "retention-fixture", now,
                now.plusSeconds(1), null);
        EvidenceReference evidence = new EvidenceReference("retention", UUID.randomUUID().toString());
        Episode expiring = Episode.recorded(EpisodeId.random(), scope, "Temporary run", "Unverified temporary run",
                EpisodeOutcome.UNKNOWN, now, now, now.plusSeconds(1), now.plusSeconds(10), List.of(), List.of(),
                List.of(evidence), provenance);
        persistence.episodeRepository().append(expiring);
        assertEquals(1, persistence.episodeRepository().deleteExpiredUnverified(now.plusSeconds(11)));
        assertTrue(persistence.episodeRepository().findById(expiring.id()).isEmpty());
    }
}
