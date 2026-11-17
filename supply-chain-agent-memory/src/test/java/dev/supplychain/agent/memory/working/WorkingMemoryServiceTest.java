package dev.supplychain.agent.memory.working;

import dev.supplychain.agent.memory.MemoryFixtures;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.inmemory.InMemoryWorkingMemoryRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkingMemoryServiceTest {
    @Test
    void openCreatesAnEmptyVersionZeroSnapshotInTheRequestedScope() {
        var repository = new InMemoryWorkingMemoryRepository();
        var service = new WorkingMemoryService(repository, Duration.ofHours(2));
        var opened = service.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        assertEquals(0, opened.version());
        assertEquals(MemoryFixtures.SCOPE, opened.scopeId());
        assertTrue(opened.values().isEmpty());
        assertTrue(opened.hypotheses().isEmpty());
    }

    @Test
    void putStoresTypedValueAndEvidenceOnTheSpecificSlot() {
        var service = new WorkingMemoryService(new InMemoryWorkingMemoryRepository(), Duration.ofHours(2));
        var opened = service.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        var evidence = new EvidenceReference("inventory-observation", "INV-8");
        var changed = service.put(opened.id(), 0, "available-units",
                new MemoryValue.Decimal(new BigDecimal("42.5")), List.of(evidence), MemoryFixtures.NOW.plusSeconds(1));
        assertEquals(1, changed.version());
        var entry = changed.values().get("available-units");
        assertEquals(new BigDecimal("42.5"), ((MemoryValue.Decimal) entry.value()).value());
        assertEquals(List.of(evidence), entry.evidence());
        assertEquals(List.of(evidence), changed.sourceEvidence());
    }

    @Test
    void staleWriterCannotOverwriteNewerWorkingMemoryVersion() {
        var service = new WorkingMemoryService(new InMemoryWorkingMemoryRepository(), Duration.ofHours(2));
        var opened = service.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        service.put(opened.id(), 0, "status", new MemoryValue.Text("investigating"), List.of(), MemoryFixtures.NOW.plusSeconds(1));
        assertThrows(MemoryVersionConflictException.class, () -> service.put(opened.id(), 0,
                "status", new MemoryValue.Text("completed"), List.of(), MemoryFixtures.NOW.plusSeconds(2)));
    }

    @Test
    void replacingOrRemovingASlotRecomputesCurrentEvidenceReferences() {
        var service = new WorkingMemoryService(new InMemoryWorkingMemoryRepository(), Duration.ofHours(2));
        var opened = service.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        var first = new EvidenceReference("inventory-observation", "INV-OLD");
        var second = new EvidenceReference("inventory-observation", "INV-NEW");
        var withFirst = service.put(opened.id(), 0, "inventory.available", new MemoryValue.Decimal(new BigDecimal("10")),
                List.of(first), MemoryFixtures.NOW.plusSeconds(1));
        var replaced = service.put(withFirst.id(), 1, "inventory.available", new MemoryValue.Decimal(new BigDecimal("8")),
                List.of(second), MemoryFixtures.NOW.plusSeconds(2));
        assertEquals(List.of(second), replaced.sourceEvidence());
        var removed = service.remove(replaced.id(), 2, "inventory.available", MemoryFixtures.NOW.plusSeconds(3));
        assertTrue(removed.sourceEvidence().isEmpty());
        assertTrue(removed.values().isEmpty());
    }

    @Test
    void expiredWorkingMemoryCannotBeRevivedByAWrite() {
        var service = new WorkingMemoryService(new InMemoryWorkingMemoryRepository(), Duration.ofMinutes(5));
        var opened = service.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        assertThrows(IllegalStateException.class, () -> service.put(opened.id(), 0, "status",
                new MemoryValue.Text("resurrected"), List.of(), MemoryFixtures.NOW.plus(Duration.ofMinutes(5))));
    }

    @Test
    void hypothesesRemainProvisionalUntilEvidenceChangesTheirStatus() {
        var service = new WorkingMemoryService(new InMemoryWorkingMemoryRepository(), Duration.ofHours(2));
        var opened = service.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        var proposed = Hypothesis.proposed("Supplier SUP-01 may have reduced capacity", 0.7, MemoryFixtures.NOW);
        var changed = service.putHypothesis(opened.id(), 0, proposed, MemoryFixtures.NOW.plusSeconds(1));
        assertEquals(HypothesisStatus.PROPOSED, changed.hypotheses().get(proposed.id()).status());
        var supported = proposed.support(List.of(MemoryFixtures.SOURCE_EVIDENCE), MemoryFixtures.NOW.plusSeconds(2));
        var next = service.putHypothesis(opened.id(), 1, supported, MemoryFixtures.NOW.plusSeconds(2));
        assertEquals(HypothesisStatus.SUPPORTED, next.hypotheses().get(proposed.id()).status());
        assertTrue(next.sourceEvidence().contains(MemoryFixtures.SOURCE_EVIDENCE));
    }

    @Test
    void supportedHypothesisCannotExistWithoutSupportingEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new Hypothesis(
                HypothesisId.random(), "unsupported", HypothesisStatus.SUPPORTED, 0.5,
                List.of(), List.of(), MemoryFixtures.NOW, MemoryFixtures.NOW));
    }

    @Test
    void expiredWorkingMemoryIsRemovedByMaintenancePort() {
        var repository = new InMemoryWorkingMemoryRepository();
        var service = new WorkingMemoryService(repository, Duration.ofMinutes(5));
        var opened = service.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        assertEquals(1, repository.deleteExpired(MemoryFixtures.NOW.plus(Duration.ofMinutes(5))));
        assertTrue(repository.findById(opened.id()).isEmpty());
    }
}
