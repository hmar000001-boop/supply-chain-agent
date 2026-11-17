package dev.supplychain.agent.memory.procedural;

import dev.supplychain.agent.memory.MemoryFixtures;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.inmemory.InMemoryProceduralMemoryRepository;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralMemoryServiceTest {
    private final InMemoryProceduralMemoryRepository repository = new InMemoryProceduralMemoryRepository();
    private final ProceduralMemoryService service = new ProceduralMemoryService(repository);

    private ProcedureVersion stage(int version, ProcedureVersionId predecessor, String digest) {
        return service.stage(MemoryFixtures.SCOPE, "supplier-disruption.runbook", version,
                ProcedureKind.RUNBOOK, "Supplier disruption investigation runbook",
                new ProcedureArtifactReference(URI.create("/docs/runbooks/supplier-disruption-v" + version + ".md"), digest),
                MemoryFixtures.provenance(ProvenanceSource.HUMAN_VERIFIED, "approved-runbook", MemoryFixtures.NOW),
                MemoryFixtures.NOW, "author", predecessor);
    }

    @Test
    void draftProcedureIsNotAvailableAsAnActiveCapability() {
        var draft = stage(1, null, "a".repeat(64));
        assertEquals(ProcedureStatus.DRAFT, draft.status());
        assertTrue(service.active(MemoryFixtures.SCOPE, draft.procedureKey()).isEmpty());
    }

    @Test
    void activationRequiresValidationAndExplicitApprover() {
        var draft = stage(1, null, "a".repeat(64));
        assertThrows(IllegalStateException.class, () -> service.activate(draft.id(), 0, "approver",
                MemoryFixtures.NOW.plusSeconds(2)));
        var validated = service.validate(draft.id(), 0, "reviewer", MemoryFixtures.NOW.plusSeconds(1));
        var active = service.activate(validated.id(), 1, "approver", MemoryFixtures.NOW.plusSeconds(2));
        assertEquals(ProcedureStatus.ACTIVE, active.status());
        assertEquals("approver", active.activatedBy());
    }

    @Test
    void activatingNewVersionRetiresOldVersionAtomically() {
        var v1 = stage(1, null, "a".repeat(64));
        service.validate(v1.id(), 0, "reviewer", MemoryFixtures.NOW.plusSeconds(1));
        service.activate(v1.id(), 1, "approver", MemoryFixtures.NOW.plusSeconds(2));
        var v2 = stage(2, v1.id(), "b".repeat(64));
        service.validate(v2.id(), 0, "reviewer", MemoryFixtures.NOW.plusSeconds(3));
        var activeV2 = service.activate(v2.id(), 1, "approver", MemoryFixtures.NOW.plusSeconds(4));
        assertEquals(ProcedureStatus.RETIRED, service.load(v1.id()).status());
        assertEquals(ProcedureStatus.ACTIVE, activeV2.status());
        assertEquals(v2.id(), service.active(MemoryFixtures.SCOPE, v2.procedureKey()).orElseThrow().id());
        assertEquals(2, service.versions(MemoryFixtures.SCOPE, v2.procedureKey()).size());
    }

    @Test
    void versionSequenceMustNotSkipOrRewritePriorVersion() {
        var v1 = stage(1, null, "a".repeat(64));
        assertThrows(MemoryConflictException.class, () -> stage(3, v1.id(), "c".repeat(64)));
        assertEquals(1, service.versions(MemoryFixtures.SCOPE, v1.procedureKey()).size());
    }

    @Test
    void failedActivationDoesNotRetireExistingActiveProcedure() {
        var v1 = stage(1, null, "a".repeat(64));
        service.validate(v1.id(), 0, "reviewer", MemoryFixtures.NOW.plusSeconds(1));
        service.activate(v1.id(), 1, "approver", MemoryFixtures.NOW.plusSeconds(2));
        var v2 = stage(2, v1.id(), "b".repeat(64));
        service.validate(v2.id(), 0, "reviewer", MemoryFixtures.NOW.plusSeconds(3));
        assertThrows(IllegalArgumentException.class, () -> service.activate(v2.id(), 1, "approver", MemoryFixtures.NOW));
        assertEquals(ProcedureStatus.ACTIVE, service.load(v1.id()).status());
        assertEquals(ProcedureStatus.VALIDATED, service.load(v2.id()).status());
    }

    @Test
    void rejectedDraftRemainsAuditableButCannotBeActivated() {
        var draft = stage(1, null, "a".repeat(64));
        var rejected = service.reject(draft.id(), 0, "reviewer-3", MemoryFixtures.NOW.plusSeconds(1));
        assertEquals(ProcedureStatus.REJECTED, rejected.status());
        assertEquals("reviewer-3", rejected.rejectedBy());
        assertEquals(MemoryFixtures.NOW.plusSeconds(1), rejected.rejectedAt());
        assertThrows(IllegalStateException.class, () -> service.validate(draft.id(), 1,
                "reviewer", MemoryFixtures.NOW.plusSeconds(1)));
    }

    @Test
    void artifactDigestMustBeSha256() {
        assertThrows(IllegalArgumentException.class, () -> new ProcedureArtifactReference(
                URI.create("/runbooks/test.md"), "not-a-digest"));
    }

    @Test
    void activeProceduresAreIsolatedByScope() {
        var draft = stage(1, null, "a".repeat(64));
        service.validate(draft.id(), 0, "reviewer", MemoryFixtures.NOW.plusSeconds(1));
        service.activate(draft.id(), 1, "approver", MemoryFixtures.NOW.plusSeconds(2));
        assertTrue(service.active(MemoryFixtures.OTHER_SCOPE, draft.procedureKey()).isEmpty());
    }
}
