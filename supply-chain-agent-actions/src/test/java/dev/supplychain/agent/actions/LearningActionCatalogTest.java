package dev.supplychain.agent.actions;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.actions.internal.learning.LearningActionCatalog;
import dev.supplychain.agent.actions.external.grounding.InMemorySupplyChainSimulationWorld;
import dev.supplychain.agent.actions.external.perception.SnapshotSupplyChainPerceptionAdapter;
import dev.supplychain.agent.memory.common.*;
import dev.supplychain.agent.memory.episodic.*;
import dev.supplychain.agent.memory.inmemory.*;
import dev.supplychain.agent.memory.procedural.*;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.semantic.*;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LearningActionCatalogTest {
    private static final MemoryScopeId SCOPE = new MemoryScopeId("tenant-learning");
    private static final MemoryScopeId OTHER_SCOPE = new MemoryScopeId("tenant-other");

    @Test
    void fullCatalogRegistersVersionedLearningActionsWithoutLegacyDuplicateEpisodeWriter() {
        var fixture = fixture();
        var registry = catalog(fixture);

        var learning = registry.descriptors().stream()
                .filter(descriptor -> descriptor.category() == ActionCategory.LEARNING).toList();
        assertEquals(10, learning.size());
        assertTrue(learning.stream().allMatch(descriptor -> descriptor.effect() == ActionEffect.MEMORY_WRITE));
        assertTrue(learning.stream().allMatch(ActionDescriptor::idempotencyRequired));
        assertFalse(registry.descriptors().stream().anyMatch(d -> d.id().value().equals("coala.memory.episode.record")),
                "the complete catalog must expose one governed episodic recording action");
        assertTrue(registry.descriptors().stream().anyMatch(d -> d.id().value().equals("memory.episodic.record.v1")));
    }

    @Test
    void episodicLearningRecordsUnverifiedExperienceThenSeparateReviewerCanVerifyIt() {
        var fixture = fixture();
        var registry = catalog(fixture);
        var dispatcher = dispatcher(registry);
        var evidence = new EvidenceReference("simulator-result", "SIM-RESULT-17");
        var event = new EpisodeEvent("supplier-capacity-change", "Capacity fell below the committed quantity",
                ActionTestFixtures.NOW.minusSeconds(1), List.of(evidence));
        var input = new LearningActionCatalog.EpisodeDraft(1, SCOPE, "Supplier capacity disruption",
                "Supplier capacity fell below the required quantity", EpisodeOutcome.PARTIAL_SUCCESS,
                ActionTestFixtures.NOW.minusSeconds(10), ActionTestFixtures.NOW.minusSeconds(1),
                List.of(new EntityReference("supplier", "SUP-17")), List.of(event),
                provenance(ProvenanceSource.SIMULATOR, "SIM-RESULT-17"), List.of(evidence));

        var recorded = dispatcher.dispatch(new ActionId("memory.episodic.record.v1"),
                LearningActionCatalog.EpisodeDraft.class, Episode.class,
                context("agent", Capability.MEMORY_RECORD_EPISODE, "record-episode-1", SCOPE), input);

        assertEquals(ActionStatus.SUCCEEDED, recorded.status());
        assertEquals(EpisodeStatus.RECORDED, recorded.value().status());
        assertEquals(List.of(event), recorded.value().events());
        assertEquals(List.of(new EntityReference("supplier", "SUP-17")), recorded.value().entities());
        assertNull(recorded.value().reviewedBy());

        var review = new LearningActionCatalog.EpisodeReview(1, SCOPE,
                new LearningActionCatalog.EpisodeIdRef(recorded.value().id().value()), 0,
                new EvidenceReference("independent-review", "REVIEW-EP-17"));
        var verified = dispatcher.dispatch(new ActionId("memory.episodic.verify.v1"),
                LearningActionCatalog.EpisodeReview.class, Episode.class,
                context("reviewer", Capability.MEMORY_VERIFY_EPISODE, "verify-episode-1", SCOPE), review);

        assertEquals(ActionStatus.SUCCEEDED, verified.status());
        assertEquals(EpisodeStatus.VERIFIED, verified.value().status());
        assertEquals("reviewer", verified.value().reviewedBy());
        assertEquals(review.reviewEvidence(), verified.value().reviewEvidence());
    }

    @Test
    void modelDerivedEpisodeCannotBeRecordedWithoutCallerEvidence() {
        var fixture = fixture();
        var dispatcher = dispatcher(catalog(fixture));
        var input = new LearningActionCatalog.EpisodeDraft(1, SCOPE, "Model hypothesis",
                "An ungrounded model assertion", EpisodeOutcome.UNKNOWN,
                ActionTestFixtures.NOW.minusSeconds(2), ActionTestFixtures.NOW.minusSeconds(1),
                List.of(), List.of(), provenance(ProvenanceSource.MODEL_DERIVED, "MODEL-RUN-1"), List.of());

        var result = dispatcher.dispatch(new ActionId("memory.episodic.record.v1"),
                LearningActionCatalog.EpisodeDraft.class, Episode.class,
                context("agent", Capability.MEMORY_RECORD_EPISODE, "record-ungrounded", SCOPE), input);

        assertEquals(ActionStatus.FAILED, result.status());
        assertEquals(ActionFailureCode.INVALID_INPUT, result.failure().code());
        assertTrue(fixture.episodes().findVerified(new EpisodeQuery(SCOPE, null, ActionTestFixtures.NOW, 10)).isEmpty());
    }

    @Test
    void semanticFactStaysProposedUntilASeparateReviewCapabilityActivatesIt() {
        var fixture = fixture();
        var dispatcher = dispatcher(catalog(fixture));
        var proposal = new LearningActionCatalog.SemanticProposal(1, SCOPE,
                new EntityReference("supplier", "SUP-21"), "capacity.units",
                new FactValue.Decimal(new java.math.BigDecimal("240")), ActionTestFixtures.NOW,
                null, provenance(ProvenanceSource.MODEL_DERIVED, "MODEL-SUMMARY-21"));

        var staged = dispatcher.dispatch(new ActionId("memory.semantic.propose.v1"),
                LearningActionCatalog.SemanticProposal.class, SemanticFact.class,
                context("agent", Capability.MEMORY_PROPOSE_SEMANTIC, "propose-fact-1", SCOPE), proposal);

        assertEquals(ActionStatus.SUCCEEDED, staged.status());
        assertEquals(FactStatus.PROPOSED, staged.value().status());
        var review = new LearningActionCatalog.SemanticReview(1, SCOPE, staged.value().id().value(), 0,
                new EvidenceReference("independent-review", "REVIEW-FACT-21"));
        var activated = dispatcher.dispatch(new ActionId("memory.semantic.approve.v1"),
                LearningActionCatalog.SemanticReview.class, SemanticFact.class,
                context("semantic-reviewer", Capability.MEMORY_REVIEW_SEMANTIC, "approve-fact-1", SCOPE), review);

        assertEquals(ActionStatus.SUCCEEDED, activated.status());
        assertEquals(FactStatus.ACTIVE, activated.value().status());
        assertEquals("semantic-reviewer", activated.value().activatedBy());
        assertEquals(review.reviewEvidence(), activated.value().activationEvidence());
    }

    @Test
    void proceduralArtifactMustPassDistinctAuthorValidatorAndApproverStages() {
        var fixture = fixture();
        var dispatcher = dispatcher(catalog(fixture));
        var draft = new LearningActionCatalog.ProcedureDraft(1, SCOPE, "supplier-delay-recovery", 1,
                ProcedureKind.RUNBOOK, "A reviewed procedure for supplier delay recovery",
                URI.create("https://example.invalid/runbooks/supplier-delay-v1"), "a".repeat(64),
                provenance(ProvenanceSource.VERIFIED_DOCUMENT, "DOC-RUNBOOK-1"), null);
        var staged = dispatcher.dispatch(new ActionId("memory.procedural.stage.v1"),
                LearningActionCatalog.ProcedureDraft.class, ProcedureVersion.class,
                context("author", Capability.MEMORY_STAGE_PROCEDURE, "stage-procedure-1", SCOPE), draft);

        assertEquals(ActionStatus.SUCCEEDED, staged.status());
        assertEquals(ProcedureStatus.DRAFT, staged.value().status());
        var reviewDraft = new LearningActionCatalog.ProcedureReview(1, SCOPE, staged.value().id().value(), 0);
        var validated = dispatcher.dispatch(new ActionId("memory.procedural.validate.v1"),
                LearningActionCatalog.ProcedureReview.class, ProcedureVersion.class,
                context("validator", Capability.MEMORY_REVIEW_PROCEDURE, "validate-procedure-1", SCOPE), reviewDraft);

        assertEquals(ActionStatus.SUCCEEDED, validated.status());
        assertEquals(ProcedureStatus.VALIDATED, validated.value().status());
        var activate = new LearningActionCatalog.ProcedureReview(1, SCOPE,
                validated.value().id().value(), validated.value().lifecycleRevision());
        var selfApproved = dispatcher.dispatch(new ActionId("memory.procedural.activate.v1"),
                LearningActionCatalog.ProcedureReview.class, ProcedureVersion.class,
                context("validator", Capability.MEMORY_ACTIVATE_PROCEDURE, "activate-procedure-self", SCOPE), activate);
        assertEquals(ActionStatus.REJECTED, selfApproved.status(),
                "the validator cannot also activate the same immutable procedure version");

        var active = dispatcher.dispatch(new ActionId("memory.procedural.activate.v1"),
                LearningActionCatalog.ProcedureReview.class, ProcedureVersion.class,
                context("approver", Capability.MEMORY_ACTIVATE_PROCEDURE, "activate-procedure-1", SCOPE), activate);

        assertEquals(ActionStatus.SUCCEEDED, active.status());
        assertEquals(ProcedureStatus.ACTIVE, active.value().status());
        assertEquals("author", active.value().createdBy());
        assertEquals("validator", active.value().validatedBy());
        assertEquals("approver", active.value().activatedBy());
    }

    @Test
    void durableLearningCatalogCannotEscapeItsHostBoundMemoryScopeEvenWithGlobalCapability() {
        var fixture = fixture();
        var dispatcher = dispatcher(catalog(fixture));
        var input = new LearningActionCatalog.EpisodeDraft(1, OTHER_SCOPE, "Cross-scope attempt",
                "Must be rejected by the host boundary", EpisodeOutcome.UNKNOWN,
                ActionTestFixtures.NOW.minusSeconds(2), ActionTestFixtures.NOW.minusSeconds(1),
                List.of(), List.of(), provenance(ProvenanceSource.HUMAN_VERIFIED, "HUMAN-1"), List.of());

        var result = dispatcher.dispatch(new ActionId("memory.episodic.record.v1"),
                LearningActionCatalog.EpisodeDraft.class, Episode.class,
                ActionContext.create("exec", "corr", new ActionPrincipal("global-agent",
                        Set.of(CapabilityGrant.all(Capability.MEMORY_RECORD_EPISODE))),
                        ActionTestFixtures.NOW, "cross-scope-key"), input);

        assertEquals(ActionStatus.REJECTED, result.status());
        assertEquals(ActionFailureCode.PRECONDITION_FAILED, result.failure().code());
        assertTrue(fixture.episodes().findVerified(new EpisodeQuery(OTHER_SCOPE, null, ActionTestFixtures.NOW, 10)).isEmpty());
    }

    @Test
    void actionContractVersionMismatchFailsBeforeAnyMemoryMutation() {
        var fixture = fixture();
        var dispatcher = dispatcher(catalog(fixture));
        assertThrows(IllegalArgumentException.class, () -> new LearningActionCatalog.EpisodeDraft(2, SCOPE,
                "Unsupported", "Future schema", EpisodeOutcome.UNKNOWN,
                ActionTestFixtures.NOW.minusSeconds(2), ActionTestFixtures.NOW.minusSeconds(1),
                List.of(), List.of(), provenance(ProvenanceSource.HUMAN_VERIFIED, "HUMAN-2"), List.of()));
    }

    private static Fixture fixture() {
        var working = new InMemoryWorkingMemoryRepository();
        var episodes = new InMemoryEpisodeRepository();
        var semantic = new InMemorySemanticFactRepository();
        var procedures = new InMemoryProceduralMemoryRepository();
        var retrieval = new MemoryRetrievalService(working, episodes, semantic, procedures);
        var workingService = new WorkingMemoryService(working, Duration.ofHours(1));
        var episodic = new EpisodicMemoryService(episodes, Duration.ofDays(7));
        var semanticService = new SemanticMemoryService(semantic, SemanticAdmissionPolicy.conservativeDefault());
        var proceduralService = new ProceduralMemoryService(procedures);
        var world = ActionTestFixtures.world();
        var perception = new SnapshotSupplyChainPerceptionAdapter(() -> world);
        var simulation = new InMemorySupplyChainSimulationWorld(world, new SupplyChainSimulator());
        return new Fixture(working, episodes, procedures, SupplyChainActionCatalog.create(retrieval, workingService,
                episodic, semanticService, proceduralService, SCOPE,
                request -> new dev.supplychain.agent.actions.internal.ReasoningAssessment("bounded", List.of(), List.of(),
                        List.of(), List.of(), List.of(), dev.supplychain.agent.actions.internal.ReasoningConfidence.UNASSESSED,
                        "test provider"), perception, simulation));
    }

    private static ActionRegistry catalog(Fixture fixture) { return fixture.registry(); }

    private static ActionDispatcher dispatcher(ActionRegistry registry) {
        return new ActionDispatcher(registry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
    }

    private static ActionContext context(String principal, Capability capability, String key, MemoryScopeId scope) {
        return ActionContext.create("execution-1", "correlation-1",
                new ActionPrincipal(principal, Set.of(CapabilityGrant.scoped(capability,
                        Set.of(new ResourceReference("memory-scope", scope.value()))))),
                ActionTestFixtures.NOW, key);
    }

    private static Provenance provenance(ProvenanceSource source, String sourceId) {
        return new Provenance(source, sourceId, ActionTestFixtures.NOW.minusSeconds(5),
                ActionTestFixtures.NOW, "source-v1");
    }

    private record Fixture(InMemoryWorkingMemoryRepository working, InMemoryEpisodeRepository episodes,
                           InMemoryProceduralMemoryRepository procedures, ActionRegistry registry) { }
}
