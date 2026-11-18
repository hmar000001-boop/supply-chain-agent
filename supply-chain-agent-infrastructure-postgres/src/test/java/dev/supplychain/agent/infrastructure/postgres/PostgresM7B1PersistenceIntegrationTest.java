package dev.supplychain.agent.infrastructure.postgres;

import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.memory.PostgresProceduralMemoryRepository;
import dev.supplychain.agent.infrastructure.postgres.memory.PostgresSemanticFactRepository;
import dev.supplychain.agent.infrastructure.postgres.planning.PostgresPlannerSessionStore;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.procedural.ProcedureArtifactReference;
import dev.supplychain.agent.memory.procedural.ProcedureKind;
import dev.supplychain.agent.memory.procedural.ProcedureStatus;
import dev.supplychain.agent.memory.procedural.ProcedureVersion;
import dev.supplychain.agent.memory.procedural.ProcedureVersionId;
import dev.supplychain.agent.memory.semantic.FactKey;
import dev.supplychain.agent.memory.semantic.FactStatus;
import dev.supplychain.agent.memory.semantic.FactValue;
import dev.supplychain.agent.memory.semantic.SemanticFact;
import dev.supplychain.agent.memory.semantic.SemanticFactQuery;
import dev.supplychain.agent.memory.semantic.SemanticFactRevision;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.session.PlannerCheckpointConflictException;
import dev.supplychain.agent.planning.session.PlannerNodeCheckpoint;
import dev.supplychain.agent.planning.session.PlannerPathCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class PostgresM7B1PersistenceIntegrationTest {
    private static DataSource dataSource;
    private static PostgresMemoryPersistence memory;
    private static PostgresSemanticFactRepository semantic;
    private static PostgresProceduralMemoryRepository procedural;
    private static PostgresPlannerSessionStore plannerSessions;

    @BeforeAll static void initialize() {
        dataSource = PostgresDataSourceFactory.create(PostgresDatabaseConfig.fromEnvironment(System.getenv()));
        memory = new PostgresMemoryPersistence(dataSource);
        assertEquals(6, memory.migrate().totalMigrations());
        semantic = new PostgresSemanticFactRepository(dataSource);
        procedural = new PostgresProceduralMemoryRepository(dataSource);
        plannerSessions = new PostgresPlannerSessionStore(dataSource);
    }

    @Test void semanticFactsPreserveBitemporalLifecycleAndAtomicTimelineReplacement() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemoryScopeId scope = new MemoryScopeId("m7b1-semantic-" + UUID.randomUUID());
        EntityReference subject = new EntityReference("supplier", "SUP-" + UUID.randomUUID());
        FactKey key = new FactKey(scope, subject, "leadTimeDays");
        Provenance provenance = provenance("semantic-origin", now);
        SemanticFact original = semantic.stage(SemanticFact.proposed(key, new FactValue.Decimal(new BigDecimal("5.0")),
                now, now.plus(10, ChronoUnit.DAYS), now, provenance));
        SemanticFact active = semantic.activate(original.id(), 0, "reviewer-1",
                new EvidenceReference("approval", "approval-1"), now.plusSeconds(1));
        assertEquals(1, active.version());
        assertEquals(List.of(active), semantic.findActive(new SemanticFactQuery(scope, subject, "leadTimeDays",
                now.plus(1, ChronoUnit.DAYS), now.plusSeconds(2), 10)));

        SemanticFact firstSegment = semantic.stage(SemanticFact.proposed(key, new FactValue.Decimal(new BigDecimal("6.0")),
                now, now.plus(5, ChronoUnit.DAYS), now.plusSeconds(3), provenance("segment-a", now.plusSeconds(3))));
        SemanticFact secondSegment = semantic.stage(SemanticFact.proposed(key, new FactValue.Decimal(new BigDecimal("7.0")),
                now.plus(5, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS), now.plusSeconds(4), provenance("segment-b", now.plusSeconds(4))));
        List<SemanticFact> replacements = semantic.supersedeAndActivateSegments(active.id(), active.version(),
                List.of(new SemanticFactRevision(firstSegment.id(), 0), new SemanticFactRevision(secondSegment.id(), 0)),
                "reviewer-2", new EvidenceReference("approval", "approval-2"), now.plusSeconds(5));
        assertEquals(List.of(FactStatus.ACTIVE, FactStatus.ACTIVE), replacements.stream().map(SemanticFact::status).toList());
        assertEquals(FactStatus.SUPERSEDED, semantic.findById(active.id()).orElseThrow().status());
        assertEquals(1, semantic.findActive(new SemanticFactQuery(scope, subject, null,
                now.plus(6, ChronoUnit.DAYS), now.plusSeconds(6), 10)).size());
        assertTrue(semantic.findActive(new SemanticFactQuery(scope, subject, null,
                now.plus(1, ChronoUnit.DAYS), now.plusSeconds(6), 10)).stream()
                .anyMatch(fact -> fact.id().equals(firstSegment.id())));
    }

    @Test void semanticConflictIsRejectedWithoutActivatingCandidate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemoryScopeId scope = new MemoryScopeId("m7b1-semantic-conflict-" + UUID.randomUUID());
        EntityReference subject = new EntityReference("product", "SKU-" + UUID.randomUUID());
        FactKey key = new FactKey(scope, subject, "approvedSubstitute");
        SemanticFact existing = semantic.stage(SemanticFact.proposed(key, new FactValue.Text("SKU-A"), now, null,
                now, provenance("semantic-a", now)));
        semantic.activate(existing.id(), 0, "reviewer", new EvidenceReference("review", "a"), now.plusSeconds(1));
        SemanticFact conflict = semantic.stage(SemanticFact.proposed(key, new FactValue.Text("SKU-B"), now, null,
                now.plusSeconds(2), provenance("semantic-b", now.plusSeconds(2))));
        assertThrows(MemoryConflictException.class, () -> semantic.activate(conflict.id(), 0, "reviewer",
                new EvidenceReference("review", "b"), now.plusSeconds(3)));
        assertEquals("PROPOSED", semantic.findById(conflict.id()).orElseThrow().status().name());
        assertThrows(MemoryVersionConflictException.class, () -> semantic.reject(conflict.id(), 4, "reviewer",
                new EvidenceReference("review", "c"), now.plusSeconds(4)));
    }

    @Test void proceduralVersionsAreAppendOnlyAndActivationRetiresPreviousVersionAtomically() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemoryScopeId scope = new MemoryScopeId("m7b1-procedure-" + UUID.randomUUID());
        String key = "supplier-recovery-" + UUID.randomUUID();
        ProcedureVersion v1 = draft(scope, key, 1, null, now, "artifact-v1");
        procedural.stage(v1);
        ProcedureVersion validated1 = procedural.compareAndSet(v1.id(), 0, v1.validate("validator", now.plusSeconds(1)));
        ProcedureVersion active1 = procedural.activate(v1.id(), validated1.lifecycleRevision(), "approver", now.plusSeconds(2));

        ProcedureVersion v2 = draft(scope, key, 2, active1.id(), now.plusSeconds(3), "artifact-v2");
        procedural.stage(v2);
        ProcedureVersion validated2 = procedural.compareAndSet(v2.id(), 0, v2.validate("validator", now.plusSeconds(4)));
        ProcedureVersion active2 = procedural.activate(v2.id(), validated2.lifecycleRevision(), "approver", now.plusSeconds(5));

        assertEquals(ProcedureStatus.RETIRED, procedural.findById(v1.id()).orElseThrow().status());
        assertEquals(active2, procedural.findActive(scope, key).orElseThrow());
        assertEquals(active1, procedural.findActiveAt(scope, now.plusSeconds(3), 10).getFirst());
        assertEquals(List.of(2, 1), procedural.findVersions(scope, key).stream().map(ProcedureVersion::versionNumber).toList());
        assertThrows(MemoryVersionConflictException.class, () -> procedural.activate(v2.id(), 0, "approver", now.plusSeconds(6)));
    }

    @Test void plannerSessionRoundTripsTreeAndFrontierAndRejectsStaleRevisionAcrossInstances() {
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        PlannerSessionCheckpoint first = new PlannerSessionCheckpoint(sessionId, PlannerId.TOT_BOUNDED_SEARCH,
                "a".repeat(64), "tot-v3|parameters=ToTParameters[x=1]|ranker=deterministic|world=v1",
                1, PlannerSessionStatus.ACTIVE,
                List.of(new PlannerPathCheckpoint("node-b", List.of("reserve-1", "reroute-2"), 0.82)),
                List.of(new PlannerNodeCheckpoint("root", null, List.of(), 2, 0.3, List.of("reserve-1"), false),
                        new PlannerNodeCheckpoint("node-b", "root", List.of("reserve-1", "reroute-2"), 1, 0.82,
                                List.of("reserve-1", "reroute-2"), true)),
                4, 6, 1, 0, 2, 1, Duration.ofMillis(9876), now);
        plannerSessions.save(first, 0);
        PlannerSessionCheckpoint loaded = new PostgresPlannerSessionStore(dataSource).find(sessionId).orElseThrow();
        assertEquals(first, loaded, "frontier and node arrays must survive another repository instance");
        PlannerSessionCheckpoint second = new PlannerSessionCheckpoint(sessionId, first.plannerId(), first.requestFingerprint(),
                first.algorithmConfigurationFingerprint(), 2, PlannerSessionStatus.COMPLETED, first.frontier(), first.treeNodes(),
                5, 7, 1, 0, 2, 1, Duration.ofMillis(10_000), now.plusSeconds(1));
        plannerSessions.save(second, 1);
        assertEquals(second, plannerSessions.find(sessionId).orElseThrow());
        assertThrows(PlannerCheckpointConflictException.class, () -> plannerSessions.save(second, 1));
    }

    private static ProcedureVersion draft(MemoryScopeId scope, String key, int number, ProcedureVersionId predecessor,
                                           Instant now, String artifactId) {
        return ProcedureVersion.draft(scope, key, number, ProcedureKind.RUNBOOK,
                "Validated supplier recovery runbook v" + number,
                new ProcedureArtifactReference(URI.create("s3://approved-procedure-artifacts/" + artifactId), "b".repeat(64)),
                provenance("procedure-" + artifactId, now), now, "author", predecessor);
    }

    private static Provenance provenance(String sourceId, Instant at) {
        return new Provenance(ProvenanceSource.HUMAN_VERIFIED, sourceId, at, at, "v1");
    }
}
