package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureActionReferences;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureArtifact;
import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.procedural.ProcedureArtifactReference;
import dev.supplychain.agent.memory.procedural.ProcedureKind;
import dev.supplychain.agent.memory.procedural.ProcedureVersion;
import dev.supplychain.agent.planning.PlanningTestFixtures;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReusableRecoveryProcedurePlanningTest {
    @Test void activeB2ProcedureBecomesProvenanceAndSoftActionPriorityInTheNextM5Request() {
        PlanningRequest base = PlanningTestFixtures.request("b2-reuse", PlanningTestFixtures.budget(20, 20, 0, 5, 3));
        ReusableRecoveryProcedure procedure = activeProcedure(base,
                base.availableActionDefinitions().getFirst(), PlanningTestFixtures.NOW);

        PlanningRequest nextCycle = base.withReusableRecoveryProcedures(List.of(procedure));

        assertEquals(List.of(procedure), nextCycle.reusableRecoveryProcedures());
        assertTrue(nextCycle.evidence().contains(new EvidenceReference("coala-procedural-recovery",
                procedure.procedure().id().value() + "@" + procedure.procedure().versionNumber())));
        assertTrue(nextCycle.evidence().containsAll(procedure.artifact().evidence()));
        var initial = PlanningTestFixtures.worldModel().initialState(nextCycle);
        var available = PlanningTestFixtures.worldModel().availableActions(nextCycle, initial);
        assertFalse(available.isEmpty());
        assertTrue(nextCycle.isReusableRecoveryAction(available.getFirst()));
        assertTrue(available.stream().allMatch(nextCycle::isReusableRecoveryAction),
                "the recovered procedure may prioritize only actions already emitted by the trusted world model");
        assertEquals(base.fingerprint().equals(nextCycle.fingerprint()), false,
                "reused procedural knowledge must be part of the next planning input identity");
    }

    @Test void requestRejectsAProcedureWhoseRegisteredActionDescriptorHasChanged() {
        PlanningRequest base = PlanningTestFixtures.request("b2-descriptor-change", PlanningTestFixtures.budget(20, 20, 0, 5, 3));
        ReusableRecoveryProcedure procedure = activeProcedure(base, base.availableActionDefinitions().getFirst(), PlanningTestFixtures.NOW);
        ActionDescriptor old = base.availableActionDefinitions().getFirst();
        ActionDescriptor changed = new ActionDescriptor(old.id(), old.name(), old.description() + " changed",
                old.category(), old.effect(), old.requiredCapability(), old.idempotencyRequired());
        PlanningRequest changedActions = new PlanningRequest(base.executionId(), base.objective(), base.initialWorld(),
                base.workingMemory(), base.evidence(), List.of(changed), base.hardConstraints(), base.scoringProfile(),
                base.riskPolicy(), base.authorityPolicy(), base.complexity(), base.budget(), base.requestedAt(), null);

        assertThrows(SecurityException.class, () -> changedActions.withReusableRecoveryProcedures(List.of(procedure)));
    }

    private static ReusableRecoveryProcedure activeProcedure(PlanningRequest request, ActionDescriptor descriptor, Instant at) {
        String sourceFingerprint = sha("approved-b2-source-" + request.executionId());
        var actionReference = RecoveryProcedureActionReferences.current(descriptor);
        var artifact = new RecoveryProcedureArtifact(1, "draft-" + request.executionId(), request.workingMemory().scopeId(),
                "inventory.shortage", dev.supplychain.agent.mechanisms.b4.ObjectiveFingerprint.from(request.objective()).value(),
                List.of(actionReference), List.of("inventory.available"), List.of("reservation.confirmed"),
                List.of("reservation.rejected"), List.of(new EvidenceReference("erp", "source-" + request.executionId())),
                sourceFingerprint, "b2-approver", "approval-" + request.executionId(), at);
        var artifactReference = new ProcedureArtifactReference(URI.create("urn:sha256:" + sha("artifact-" + request.executionId())),
                sha("artifact-" + request.executionId()));
        var provenance = new Provenance(ProvenanceSource.HUMAN_VERIFIED, artifact.sourceDraftId(), at, at, sourceFingerprint);
        ProcedureVersion draft = ProcedureVersion.draft(request.workingMemory().scopeId(),
                "b2.recovery.inventory.shortage", 1, ProcedureKind.RUNBOOK, "Approved recovery procedure",
                artifactReference, provenance, at, "b2-approver", null);
        ProcedureVersion active = draft.validate("independent-reviewer", at.plusSeconds(1))
                .activate("independent-approver", at.plusSeconds(2));
        return new ReusableRecoveryProcedure(active, artifact);
    }

    private static String sha(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
