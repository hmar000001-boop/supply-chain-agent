package dev.supplychain.agent.memory.semantic;

import dev.supplychain.agent.memory.MemoryFixtures;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.inmemory.InMemorySemanticFactRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticMemoryServiceTest {
    private final InMemorySemanticFactRepository repository = new InMemorySemanticFactRepository();
    private final SemanticMemoryService service = new SemanticMemoryService(repository, SemanticAdmissionPolicy.conservativeDefault());

    @Test
    void modelDerivedFactCannotBeActivatedWithoutHumanReview() {
        var fact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.MODEL_DERIVED);
        service.propose(fact);
        assertThrows(SecurityException.class, () -> service.activateTrusted(fact.id(), 0, "agent", MemoryFixtures.NOW.plusSeconds(1)));
        assertEquals(FactStatus.PROPOSED, service.load(fact.id()).status());
    }

    @Test
    void humanReviewedFactRetainsTheApprovalEvidence() {
        var fact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.MODEL_DERIVED);
        service.propose(fact);
        var active = service.approveAndActivate(fact.id(), 0, "reviewer", MemoryFixtures.REVIEW_EVIDENCE,
                MemoryFixtures.NOW.plusSeconds(1));
        assertEquals(FactStatus.ACTIVE, active.status());
        assertEquals(MemoryFixtures.REVIEW_EVIDENCE, active.activationEvidence());
        assertEquals("reviewer", active.activatedBy());
    }

    @Test
    void authoritativeFactCanBeActivatedAndItsSourceIsRecordedAsActivationEvidence() {
        var fact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "500", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(fact);
        var active = service.activateTrusted(fact.id(), 0, "system-ingestor", MemoryFixtures.NOW.plusSeconds(1));
        assertEquals(new dev.supplychain.agent.memory.common.EvidenceReference("provenance", "SRC-CAP"),
                active.activationEvidence());
    }

    @Test
    void conflictingOverlappingFactCannotReplaceActiveTruthSilently() {
        var old = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "500", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(old);
        service.activateTrusted(old.id(), 0, "system", MemoryFixtures.NOW.plusSeconds(1));
        var conflict = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW.plusSeconds(2), ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(conflict);
        assertThrows(MemoryConflictException.class, () -> service.activateTrusted(conflict.id(), 0,
                "system", MemoryFixtures.NOW.plusSeconds(3)));
        assertEquals(FactStatus.ACTIVE, service.load(old.id()).status());
        assertEquals(FactStatus.PROPOSED, service.load(conflict.id()).status());
    }

    @Test
    void replacementIsAtomicAndHistoricalKnowledgeTimeReturnsTheFormerFact() {
        Instant oldStart = MemoryFixtures.NOW.minusSeconds(3600);
        var old = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "500", oldStart, null,
                oldStart, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(old);
        service.activateTrusted(old.id(), 0, "system", oldStart.plusSeconds(1));
        var newFact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", oldStart, null,
                MemoryFixtures.NOW, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(newFact);
        var active = service.replaceActive(old.id(), 1, newFact.id(), 0, "reviewer",
                MemoryFixtures.REVIEW_EVIDENCE, MemoryFixtures.NOW.plusSeconds(1));

        assertEquals(FactStatus.SUPERSEDED, service.load(old.id()).status());
        assertEquals(FactStatus.ACTIVE, active.status());
        var beforeChange = service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, "capacity.units", MemoryFixtures.NOW,
                MemoryFixtures.NOW.minusSeconds(10), 10));
        assertEquals(List.of(old.id()), beforeChange.stream().map(SemanticFact::id).toList());
        var afterChange = service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, "capacity.units", MemoryFixtures.NOW,
                MemoryFixtures.NOW.plusSeconds(2), 10));
        assertEquals(List.of(newFact.id()), afterChange.stream().map(SemanticFact::id).toList());
    }

    @Test
    void temporalAmendmentCanSplitAnOpenValidityIntervalWithoutLosingHistoricalTruth() {
        var old = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "500", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(old);
        service.activateTrusted(old.id(), 0, "system", MemoryFixtures.NOW.plusSeconds(1));
        Instant boundary = MemoryFixtures.NOW.plusSeconds(100);
        var historicalSegment = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "500", MemoryFixtures.NOW,
                boundary, MemoryFixtures.NOW.plusSeconds(2), ProvenanceSource.AUTHORITATIVE_SYSTEM);
        var futureSegment = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", boundary,
                null, MemoryFixtures.NOW.plusSeconds(2), ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(historicalSegment);
        service.propose(futureSegment);
        var activated = service.reviseTimeline(old.id(), 1, List.of(
                new SemanticFactRevision(historicalSegment.id(), 0),
                new SemanticFactRevision(futureSegment.id(), 0)), "reviewer", MemoryFixtures.REVIEW_EVIDENCE,
                MemoryFixtures.NOW.plusSeconds(3));

        assertEquals(2, activated.size());
        assertEquals(FactStatus.SUPERSEDED, service.load(old.id()).status());
        var beforeKnowledgeUpdate = service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, "capacity.units", MemoryFixtures.NOW.plusSeconds(150),
                MemoryFixtures.NOW.plusSeconds(2), 10));
        assertEquals(List.of(old.id()), beforeKnowledgeUpdate.stream().map(SemanticFact::id).toList());
        var afterUpdateInPastSegment = service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, "capacity.units", MemoryFixtures.NOW.plusSeconds(50),
                MemoryFixtures.NOW.plusSeconds(4), 10));
        var afterUpdateInFutureSegment = service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, "capacity.units", MemoryFixtures.NOW.plusSeconds(150),
                MemoryFixtures.NOW.plusSeconds(4), 10));
        assertEquals(List.of(historicalSegment.id()), afterUpdateInPastSegment.stream().map(SemanticFact::id).toList());
        assertEquals(List.of(futureSegment.id()), afterUpdateInFutureSegment.stream().map(SemanticFact::id).toList());
    }

    @Test
    void failedPartialValidityReplacementDoesNotSupersedeOldFact() {
        var old = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "500", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(old);
        service.activateTrusted(old.id(), 0, "system", MemoryFixtures.NOW.plusSeconds(1));
        var partial = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW.plusSeconds(10),
                null, MemoryFixtures.NOW.plusSeconds(2), ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(partial);
        assertThrows(IllegalArgumentException.class, () -> service.replaceActive(old.id(), 1, partial.id(), 0,
                "reviewer", MemoryFixtures.REVIEW_EVIDENCE, MemoryFixtures.NOW.plusSeconds(11)));
        assertEquals(FactStatus.ACTIVE, service.load(old.id()).status());
        assertEquals(FactStatus.PROPOSED, service.load(partial.id()).status());
    }

    @Test
    void validTimeFiltersFactsSeparatelyFromKnowledgeTime() {
        var fact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW.plusSeconds(100),
                null, MemoryFixtures.NOW, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(fact);
        service.activateTrusted(fact.id(), 0, "system", MemoryFixtures.NOW.plusSeconds(1));
        var beforeEffective = service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, "capacity.units", MemoryFixtures.NOW,
                MemoryFixtures.NOW.plusSeconds(2), 10));
        var effective = service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, "capacity.units", MemoryFixtures.NOW.plusSeconds(101),
                MemoryFixtures.NOW.plusSeconds(102), 10));
        assertTrue(beforeEffective.isEmpty());
        assertEquals(List.of(fact.id()), effective.stream().map(SemanticFact::id).toList());
    }

    @Test
    void scopeIsolationPreventsOtherScopeFactsFromLeaking() {
        var fact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(fact);
        service.activateTrusted(fact.id(), 0, "system", MemoryFixtures.NOW.plusSeconds(1));
        assertTrue(service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.OTHER_SCOPE,
                MemoryFixtures.SUPPLIER, null, MemoryFixtures.NOW, MemoryFixtures.NOW.plusSeconds(2), 10)).isEmpty());
    }

    @Test
    void rejectingProposedFactRecordsReviewerTimeAndEvidence() {
        var fact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.MODEL_DERIVED);
        service.propose(fact);
        var rejected = service.reject(fact.id(), 0, "reviewer-4", MemoryFixtures.REVIEW_EVIDENCE,
                MemoryFixtures.NOW.plusSeconds(5));
        assertEquals(FactStatus.REJECTED, rejected.status());
        assertEquals("reviewer-4", rejected.rejectedBy());
        assertEquals(MemoryFixtures.NOW.plusSeconds(5), rejected.rejectedAt());
        assertEquals(MemoryFixtures.REVIEW_EVIDENCE, rejected.rejectionEvidence());
        assertTrue(service.retrieveCurrent(new SemanticFactQuery(MemoryFixtures.SCOPE, MemoryFixtures.SUPPLIER,
                "capacity.units", MemoryFixtures.NOW, MemoryFixtures.NOW.plusSeconds(6), 10)).isEmpty());
    }

    @Test
    void staleFactVersionCannotBeActivated() {
        var fact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, ProvenanceSource.AUTHORITATIVE_SYSTEM);
        service.propose(fact);
        assertThrows(dev.supplychain.agent.memory.common.MemoryVersionConflictException.class,
                () -> service.activateTrusted(fact.id(), 4, "system", MemoryFixtures.NOW.plusSeconds(1)));
    }
}
