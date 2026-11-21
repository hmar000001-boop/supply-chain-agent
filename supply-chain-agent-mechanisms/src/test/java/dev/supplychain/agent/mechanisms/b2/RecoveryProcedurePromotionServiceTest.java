package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.inmemory.InMemoryProceduralMemoryRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryWorkingMemoryRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryEpisodeRepository;
import dev.supplychain.agent.memory.inmemory.InMemorySemanticFactRepository;
import dev.supplychain.agent.memory.procedural.ProcedureStatus;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryService;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatures;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateFactory;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateKind;
import dev.supplychain.agent.mechanisms.b1.MaintenanceProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryProcedurePromotionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final MemoryScopeId SCOPE = new MemoryScopeId("tenant-r2");
    private static final ParentContextSnapshot PARENT = new ParentContextSnapshot(SCOPE,"request-r2",8,"b".repeat(64));

    private ActionRegistry actions(String description) {
        ActionDescriptor descriptor = new ActionDescriptor(new ActionId("inventory.reserve"), "Reserve inventory", description,
                ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true);
        return new ActionRegistry().register(new ActionHandler<String,String>() {
            public ActionDescriptor descriptor(){ return descriptor; }
            public Class<String> inputType(){ return String.class; }
            public Class<String> outputType(){ return String.class; }
            public String execute(ActionContext context, String input){ throw new AssertionError("promotion must not execute actions"); }
        });
    }

    private ImpasseResolutionService resolvedCase() {
        var service = new ImpasseResolutionService(new DeterministicImpasseDetector(), new InMemoryImpasseCaseRepository());
        var signal = new ImpasseSignal(ImpasseType.CONFLICTING_EVIDENCE, ImpasseSeverity.HIGH, "inventory:sku-1",
                "Inventory state conflicts", List.of(new EvidenceReference("erp","state-1")), NOW);
        var c = service.open("case-r2", SCOPE, "execution-r2", PARENT, signal,
                Set.of(Capability.MEMORY_RETRIEVE, Capability.REASONING_INVOKE, Capability.INVENTORY_READ, Capability.INVENTORY_RESERVE),
                Set.of(Capability.MEMORY_RETRIEVE, Capability.INVENTORY_READ), NOW, NOW.plusSeconds(600), 3);
        c = service.start(c.id(), c.version(), NOW.plusSeconds(1));
        var result = new ChildResolutionResult(c.id(), PARENT.requestId(), PARENT.workingMemoryVersion(), PARENT.objectiveFingerprint(),
                ResolutionDisposition.EVIDENCE_CONFIRMED,
                List.of(new ResolutionFinding("Inventory snapshot reconciled", List.of(new EvidenceReference("wms","reconciliation-7")))),
                List.of(), NOW.plusSeconds(10));
        service.resolve(c.id(), c.version(), result, PARENT, NOW.plusSeconds(11));
        return service;
    }

    private RecoveryProcedureDraftRequest request() {
        return new RecoveryProcedureDraftRequest("draft-r2","case-r2",2,SCOPE,PARENT,"inventory.shortage",
                List.of(new ActionId("inventory.reserve")), List.of("inventory.available"),
                List.of("reservation.confirmed"), List.of("reservation.rejected","inventory.version.changed"),
                NOW.plusSeconds(300));
    }

    private RecoveryProcedureDraftService drafts(InMemoryRecoveryProcedureDraftRepository repository,
            ActionRegistry actions, AtomicBoolean evidenceValid) {
        return new RecoveryProcedureDraftService(resolvedCase(), actions, repository,
                (scope,evidence,at) -> evidenceValid.get() && SCOPE.equals(scope) && !evidence.isEmpty(),
                (draft,approver,reference,at) -> SCOPE.equals(draft.scopeId())
                        && "ops-lead".equals(approver) && "approval-1".equals(reference));
    }

    private RecoveryProcedureDraft approvedDraft(RecoveryProcedureDraftService service) {
        var draft = service.createDraft(request(), NOW.plusSeconds(20));
        return service.approve(draft.id(), draft.version(), PARENT,
                new RecoveryProcedureApproval("ops-lead","approval-1",NOW.plusSeconds(30)), NOW.plusSeconds(31));
    }

    private RecoveryProcedurePromotionService promotion(RecoveryProcedureDraftService drafts,
            InMemoryProceduralMemoryRepository repository, InMemoryRecoveryProcedureArtifactStore artifacts,
            ActionRegistry actions, AtomicBoolean evidenceValid) {
        return new RecoveryProcedurePromotionService(drafts, new ProceduralMemoryService(repository), artifacts,
                actions, (scope,evidence,at) -> evidenceValid.get() && SCOPE.equals(scope) && !evidence.isEmpty());
    }

    @Test void b2ApprovalStagesOnlyADraftAndIndependentProceduralLifecycleIsRequired() {
        var draftRepository = new InMemoryRecoveryProcedureDraftRepository();
        var actionRegistry = actions("Registered inventory reservation");
        var evidenceValid = new AtomicBoolean(true);
        var draftService = drafts(draftRepository, actionRegistry, evidenceValid);
        var approved = approvedDraft(draftService);
        var memoryRepository = new InMemoryProceduralMemoryRepository();
        var artifacts = new InMemoryRecoveryProcedureArtifactStore();
        var service = promotion(draftService, memoryRepository, artifacts, actionRegistry, evidenceValid);

        var staged = service.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(40));
        assertEquals(ProcedureStatus.DRAFT, staged.status());
        assertEquals("b2.recovery.inventory.shortage", staged.procedureKey());
        assertEquals("draft-r2", staged.provenance().sourceId());
        assertEquals(approved.sourceFingerprint(), staged.provenance().sourceVersion());
        assertEquals("ops-lead", staged.createdBy());
        assertTrue(service.retrieveActive(SCOPE,"inventory.shortage",NOW.plusSeconds(41)).isEmpty(),
                "B2 approval must not automatically activate procedural memory");

        var validated = service.validate(staged.id(), staged.lifecycleRevision(), "procedure-reviewer", NOW.plusSeconds(50));
        assertEquals(ProcedureStatus.VALIDATED, validated.status());
        var active = service.activate(validated.id(), validated.lifecycleRevision(), "procedure-approver", NOW.plusSeconds(60));
        assertEquals(ProcedureStatus.ACTIVE, active.status());
        var retrieved = service.retrieveActive(SCOPE,"inventory.shortage",NOW.plusSeconds(70)).orElseThrow();
        assertEquals(active.id(), retrieved.procedure().id());
        assertEquals(List.of("inventory.reserve"), retrieved.artifact().actions().stream().map(RegisteredRecoveryActionReference::actionId).toList());
        assertEquals(List.of("inventory.available"), retrieved.artifact().preconditionKeys());
    }

    @Test void repeatedPromotionOfTheSameApprovedDraftIsIdempotent() {
        var actions = actions("Registered inventory reservation");
        var evidence = new AtomicBoolean(true);
        var draftService = drafts(new InMemoryRecoveryProcedureDraftRepository(), actions, evidence);
        var approved = approvedDraft(draftService);
        var repository = new InMemoryProceduralMemoryRepository();
        var service = promotion(draftService, repository, new InMemoryRecoveryProcedureArtifactStore(), actions, evidence);
        var first = service.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(40));
        var retry = service.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(41));
        assertEquals(first.id(), retry.id());
        assertEquals(1, repository.findVersions(SCOPE, first.procedureKey()).size());
    }

    @Test void unapprovedExpiredOrForeignScopeDraftCannotBePromoted() {
        var actions = actions("Registered inventory reservation");
        var evidence = new AtomicBoolean(true);
        var draftService = drafts(new InMemoryRecoveryProcedureDraftRepository(), actions, evidence);
        var unapproved = draftService.createDraft(request(), NOW.plusSeconds(20));
        var service = promotion(draftService, new InMemoryProceduralMemoryRepository(),
                new InMemoryRecoveryProcedureArtifactStore(), actions, evidence);
        assertThrows(IllegalStateException.class, () -> service.stageApprovedDraft(unapproved.id(), unapproved.version(), SCOPE, NOW.plusSeconds(21)));
        var approved = draftService.approve(unapproved.id(), unapproved.version(), PARENT,
                new RecoveryProcedureApproval("ops-lead","approval-1",NOW.plusSeconds(30)), NOW.plusSeconds(31));
        assertThrows(IllegalStateException.class, () -> service.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(301)));
        assertThrows(SecurityException.class, () -> service.stageApprovedDraft(approved.id(), approved.version(), new MemoryScopeId("tenant-b"), NOW.plusSeconds(40)));
    }

    @Test void staleEvidenceOrChangedActionDescriptorBlocksPromotion() {
        var initialActions = actions("Registered inventory reservation");
        var evidence = new AtomicBoolean(true);
        var draftService = drafts(new InMemoryRecoveryProcedureDraftRepository(), initialActions, evidence);
        var approved = approvedDraft(draftService);
        var artifacts = new InMemoryRecoveryProcedureArtifactStore();
        var memory = new InMemoryProceduralMemoryRepository();
        var changedActions = actions("Changed action contract");
        var changedActionService = promotion(draftService, memory, artifacts, changedActions, evidence);
        assertThrows(SecurityException.class, () -> changedActionService.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(40)));
        evidence.set(false);
        var staleEvidenceService = promotion(draftService, memory, artifacts, initialActions, evidence);
        assertThrows(SecurityException.class, () -> staleEvidenceService.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(40)));
    }

    @Test void laterRetrievalRejectsChangedActionDescriptorAndForeignScope() {
        var initialActions = actions("Registered inventory reservation");
        var evidence = new AtomicBoolean(true);
        var draftService = drafts(new InMemoryRecoveryProcedureDraftRepository(), initialActions, evidence);
        var approved = approvedDraft(draftService);
        var memory = new InMemoryProceduralMemoryRepository();
        var artifacts = new InMemoryRecoveryProcedureArtifactStore();
        var service = promotion(draftService, memory, artifacts, initialActions, evidence);
        var staged = service.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(40));
        var validated = service.validate(staged.id(), 0, "reviewer", NOW.plusSeconds(50));
        service.activate(validated.id(), 1, "approver", NOW.plusSeconds(60));

        var changed = promotion(draftService, memory, artifacts, actions("Changed action contract"), evidence);
        assertThrows(SecurityException.class, () -> changed.retrieveActive(SCOPE,"inventory.shortage",NOW.plusSeconds(70)));
        assertTrue(service.retrieveActive(new MemoryScopeId("tenant-b"),"inventory.shortage",NOW.plusSeconds(70)).isEmpty());
    }

    @Test void approvedRecoveryProcedureBecomesAStandardCoalaB1RetrievalCandidate() {
        var actionRegistry = actions("Registered inventory reservation");
        var evidenceValid = new AtomicBoolean(true);
        var draftService = drafts(new InMemoryRecoveryProcedureDraftRepository(), actionRegistry, evidenceValid);
        var approved = approvedDraft(draftService);
        var proceduralRepository = new InMemoryProceduralMemoryRepository();
        var promotion = promotion(draftService, proceduralRepository,
                new InMemoryRecoveryProcedureArtifactStore(), actionRegistry, evidenceValid);
        var staged = promotion.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(40));
        var validated = promotion.validate(staged.id(), staged.lifecycleRevision(), "procedure-reviewer", NOW.plusSeconds(50));
        promotion.activate(validated.id(), validated.lifecycleRevision(), "procedure-approver", NOW.plusSeconds(60));

        var retrieval = new MemoryRetrievalService(new InMemoryWorkingMemoryRepository(),
                new InMemoryEpisodeRepository(), new InMemorySemanticFactRepository(), proceduralRepository);
        var retrieved = retrieval.retrieve(new MemoryRetrievalQuery(SCOPE, null, null, null,
                NOW.plusSeconds(70), NOW.plusSeconds(70), 20));
        assertEquals(1, retrieved.activeProcedures().size());
        assertEquals("b2.recovery.inventory.shortage", retrieved.activeProcedures().getFirst().procedureKey());

        var candidates = new B1MemoryCandidateFactory().from(SCOPE, retrieved, NOW.plusSeconds(70), reference ->
                new B1CandidateFeatures(List.of(), Map.of("inventory.shortage", 0.9), 12.0, 3,
                        Duration.ofMillis(2), new MaintenanceProfile(0, 0, 0, Duration.ofDays(30), NOW)));
        assertEquals(1, candidates.size());
        assertEquals(B1MemoryCandidateKind.PROCEDURAL, candidates.getFirst().kind());
        assertEquals(staged.id().value(), candidates.getFirst().memoryReference().split(":")[1].split("@")[0]);

        var reusable = promotion.retrieveActive(SCOPE, "inventory.shortage", NOW.plusSeconds(70)).orElseThrow();
        assertEquals(List.of("inventory.available"), reusable.artifact().preconditionKeys());
        assertEquals(List.of("reservation.confirmed"), reusable.artifact().expectedPostconditionKeys());
    }

    @Test void proceduralReviewerAndApproverMustBeIndependentActors() {
        var actionRegistry = actions("Registered inventory reservation");
        var evidenceValid = new AtomicBoolean(true);
        var draftService = drafts(new InMemoryRecoveryProcedureDraftRepository(), actionRegistry, evidenceValid);
        var approved = approvedDraft(draftService);
        var promotion = promotion(draftService, new InMemoryProceduralMemoryRepository(),
                new InMemoryRecoveryProcedureArtifactStore(), actionRegistry, evidenceValid);
        var staged = promotion.stageApprovedDraft(approved.id(), approved.version(), SCOPE, NOW.plusSeconds(40));
        assertThrows(SecurityException.class, () -> promotion.validate(staged.id(), 0,
                "ops-lead", NOW.plusSeconds(50)), "B2 draft approver cannot self-review the reusable procedure");
        var validated = promotion.validate(staged.id(), 0, "procedure-reviewer", NOW.plusSeconds(51));
        assertThrows(SecurityException.class, () -> promotion.activate(validated.id(), 1,
                "procedure-reviewer", NOW.plusSeconds(60)), "procedural reviewer cannot self-approve activation");
        assertThrows(SecurityException.class, () -> promotion.activate(validated.id(), 1,
                "ops-lead", NOW.plusSeconds(60)), "original B2 draft approver cannot approve activation");
        var active = promotion.activate(validated.id(), 1, "independent-approver", NOW.plusSeconds(61));
        assertEquals(ProcedureStatus.ACTIVE, active.status());
    }

    @Test void immutableArtifactStoreRequiresTheExpectedTenantScopeForDereference() {
        var actionRegistry = actions("Registered inventory reservation");
        var evidenceValid = new AtomicBoolean(true);
        var draftService = drafts(new InMemoryRecoveryProcedureDraftRepository(), actionRegistry, evidenceValid);
        var approved = approvedDraft(draftService);
        var artifact = RecoveryProcedureArtifact.from(approved);
        var store = new InMemoryRecoveryProcedureArtifactStore();
        var reference = store.publish(artifact);
        assertEquals(artifact, store.retrieve(reference, SCOPE).orElseThrow());
        assertThrows(SecurityException.class, () -> store.retrieve(reference, new MemoryScopeId("tenant-other")));
    }

    @Test void artifactCodecIsDeterministicAndRejectsCorruption() {
        var actions = actions("Registered inventory reservation");
        var evidence = new AtomicBoolean(true);
        var draftService = drafts(new InMemoryRecoveryProcedureDraftRepository(), actions, evidence);
        var approved = approvedDraft(draftService);
        var artifact = RecoveryProcedureArtifact.from(approved);
        var codec = new RecoveryProcedureArtifactCodec();
        byte[] first = codec.encode(artifact);
        byte[] second = codec.encode(artifact);
        assertArrayEquals(first, second);
        assertEquals(artifact, codec.decode(first));
        byte[] corrupted = first.clone();
        corrupted[corrupted.length - 1] ^= 0x01;
        assertNotEquals(artifact, codec.decode(corrupted));
    }
}
