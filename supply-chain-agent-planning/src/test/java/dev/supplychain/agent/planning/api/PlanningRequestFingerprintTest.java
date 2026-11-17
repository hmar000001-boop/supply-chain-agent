package dev.supplychain.agent.planning.api;

import static org.junit.jupiter.api.Assertions.*;

import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.planning.PlanningTestFixtures;
import dev.supplychain.agent.planning.session.InMemoryPlannerSessionStore;
import dev.supplychain.agent.planning.session.PlannerCheckpointConflictException;
import dev.supplychain.agent.planning.session.PlannerPathCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class PlanningRequestFingerprintTest {
    @org.junit.jupiter.api.Test
    void sameCompleteRequestProducesStableFingerprint() {
        var request = PlanningTestFixtures.request("fingerprint-a", PlanningTestFixtures.budget(10, 10, 0, 5, 3));
        assertEquals(request.fingerprint(), request.fingerprint());
    }

    @org.junit.jupiter.api.Test
    void budgetAndResumeSessionCanChangeWithoutChangingSearchInputFingerprint() {
        var original = PlanningTestFixtures.request("fingerprint-b", PlanningTestFixtures.budget(2, 2, 0, 2, 2));
        var enlarged = PlanningTestFixtures.withBudgetAndResume(original,
                PlanningTestFixtures.budget(20, 20, 0, 10, 2), "session-1");
        assertEquals(original.fingerprint(), enlarged.fingerprint());
    }

    @org.junit.jupiter.api.Test
    void sameSnapshotVersionWithDifferentInventoryContentChangesFingerprint() {
        var original = PlanningTestFixtures.request("fingerprint-c", PlanningTestFixtures.budget(10, 10, 0, 5, 3));
        InventoryLot originalLot = original.initialWorld().inventoryLot(PlanningTestFixtures.LOT_A);
        InventoryLot changedLot = new InventoryLot(originalLot.id(), originalLot.productId(), originalLot.locationId(),
                PlanningTestFixtures.q("9"), LotQualityStatus.AVAILABLE, originalLot.expiresAt(), null, null,
                originalLot.version());
        SupplyChainSnapshot changedWorld = original.initialWorld().toBuilder().replaceInventoryLot(changedLot).build();
        PlanningRequest changed = copy(original, changedWorld, original.workingMemory(), original.evidence(), null);
        assertEquals(original.initialWorld().version(), changed.initialWorld().version());
        assertFalse(original.fingerprint().equals(changed.fingerprint()));
    }

    @org.junit.jupiter.api.Test
    void workingMemoryContentAndEvidenceArePartOfFingerprint() {
        var original = PlanningTestFixtures.request("fingerprint-d", PlanningTestFixtures.budget(10, 10, 0, 5, 3));
        var memory = original.workingMemory().put("supplier-status", new MemoryValue.Text("capacity reduced"),
                List.of(new EvidenceReference("supplier-notice", "notice-1")), PlanningTestFixtures.NOW,
                PlanningTestFixtures.NOW.plus(Duration.ofDays(1)));
        PlanningRequest changedMemory = copy(original, original.initialWorld(), memory, original.evidence(), null);
        PlanningRequest changedEvidence = copy(original, original.initialWorld(), original.workingMemory(),
                List.of(new EvidenceReference("inventory-snapshot", "SNAP-1")), null);
        assertFalse(original.fingerprint().equals(changedMemory.fingerprint()));
        assertFalse(original.fingerprint().equals(changedEvidence.fingerprint()));
    }


    @org.junit.jupiter.api.Test
    void regeneratedStorageIdentityDoesNotChangeEquivalentWorkingMemoryContentFingerprint() {
        var original = PlanningTestFixtures.request("fingerprint-wm-identity", PlanningTestFixtures.budget(10, 10, 0, 5, 3));
        var equivalentMemory = dev.supplychain.agent.memory.working.WorkingMemorySnapshot.empty(
                original.workingMemory().scopeId(), original.workingMemory().createdAt(),
                original.workingMemory().expiresAt());
        PlanningRequest equivalent = copy(original, original.initialWorld(), equivalentMemory, original.evidence(), null);
        assertFalse(original.workingMemory().id().equals(equivalentMemory.id()));
        assertEquals(original.fingerprint(), equivalent.fingerprint());
    }

    @org.junit.jupiter.api.Test
    void resumedSessionRejectsChangedWorldEvenWhenVersionAndObjectiveVersionMatch() {
        var original = PlanningTestFixtures.request("fingerprint-e", PlanningTestFixtures.budget(10, 10, 0, 5, 3));
        var store = new InMemoryPlannerSessionStore();
        store.save(new PlannerSessionCheckpoint("session-e", PlannerId.TOT_BOUNDED_SEARCH,
                original.fingerprint(), "tot-v2|test", 1, PlannerSessionStatus.ACTIVE,
                List.of(new PlannerPathCheckpoint("root", List.of(), 0.0)), List.of(),
                0, 0, 0, 0, 0, 0, java.time.Duration.ZERO, PlanningTestFixtures.NOW), 0);
        var changedLot = original.initialWorld().inventoryLot(PlanningTestFixtures.LOT_A);
        SupplyChainSnapshot changedWorld = original.initialWorld().toBuilder().replaceInventoryLot(
                new InventoryLot(changedLot.id(), changedLot.productId(), changedLot.locationId(),
                        PlanningTestFixtures.q("8"), changedLot.qualityStatus(), changedLot.expiresAt(), null, null,
                        changedLot.version())).build();
        PlanningRequest resumed = copy(original, changedWorld, original.workingMemory(), original.evidence(), "session-e");
        assertThrows(PlannerCheckpointConflictException.class, () ->
                dev.supplychain.agent.planning.session.PlannerSessionSupport.loadIfRequested(
                        resumed, PlannerId.TOT_BOUNDED_SEARCH, "tot-v2|test", store));
    }

    private static PlanningRequest copy(PlanningRequest source, SupplyChainSnapshot world,
                                        dev.supplychain.agent.memory.working.WorkingMemorySnapshot memory,
                                        List<EvidenceReference> evidence, String resumeId) {
        RecoveryObjective objective = world.recoveryObjectives().get(source.objective().id());
        return new PlanningRequest(source.executionId(), objective, world, memory, evidence,
                source.availableActionDefinitions(), source.hardConstraints(), source.scoringProfile(),
                source.riskPolicy(), source.authorityPolicy(), source.complexity(), source.budget(),
                source.requestedAt(), resumeId);
    }
}
