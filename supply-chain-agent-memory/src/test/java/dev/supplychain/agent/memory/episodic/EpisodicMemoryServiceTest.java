package dev.supplychain.agent.memory.episodic;

import dev.supplychain.agent.memory.MemoryFixtures;
import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.inmemory.InMemoryEpisodeRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EpisodicMemoryServiceTest {
    private final InMemoryEpisodeRepository repository = new InMemoryEpisodeRepository();
    private final EpisodicMemoryService service = new EpisodicMemoryService(repository, Duration.ofDays(7));

    private Episode record() {
        var start = MemoryFixtures.NOW.minusSeconds(300);
        var completed = MemoryFixtures.NOW.minusSeconds(60);
        var event = new EpisodeEvent("supplier-confirmation", "Supplier confirmed reduced capacity",
                completed, List.of(MemoryFixtures.SOURCE_EVIDENCE));
        return service.record(MemoryFixtures.SCOPE, "Supplier capacity reduction", "Order impact was contained",
                EpisodeOutcome.SUCCESS, start, completed, MemoryFixtures.NOW,
                List.of(MemoryFixtures.SUPPLIER), List.of(event), List.of(),
                MemoryFixtures.provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM, "erp-event-44", MemoryFixtures.NOW));
    }

    @Test
    void newlyRecordedEpisodeIsNotReturnedAsTrustedExperience() {
        var episode = record();
        assertEquals(EpisodeStatus.RECORDED, episode.status());
        assertEquals(1, episode.evidence().size(), "event evidence is included in episode provenance");
        assertTrue(service.retrieveVerified(new EpisodeQuery(MemoryFixtures.SCOPE, MemoryFixtures.SUPPLIER, MemoryFixtures.NOW.plusSeconds(10), 10)).isEmpty());
    }

    @Test
    void onlyExplicitlyReviewedEpisodeBecomesRetrievableExperience() {
        var episode = record();
        var verified = service.verify(episode.id(), 0, "reviewer-1", MemoryFixtures.REVIEW_EVIDENCE,
                MemoryFixtures.NOW.plusSeconds(10));
        assertEquals(EpisodeStatus.VERIFIED, verified.status());
        assertEquals("reviewer-1", verified.reviewedBy());
        assertTrue(verified.evidence().contains(MemoryFixtures.REVIEW_EVIDENCE));
        assertEquals(1, service.retrieveVerified(new EpisodeQuery(MemoryFixtures.SCOPE, MemoryFixtures.SUPPLIER, MemoryFixtures.NOW.plusSeconds(10), 10)).size());
    }

    @Test
    void idempotentRecordRetryAfterVerificationReturnsTheExistingReviewedEpisode() {
        var start = MemoryFixtures.NOW.minusSeconds(300);
        var completed = MemoryFixtures.NOW.minusSeconds(60);
        var idempotency = new EvidenceReference("observation", "OBS-IDEMPOTENT-77");
        var event = new EpisodeEvent("shipment-update", "Shipment delayed", completed,
                List.of(MemoryFixtures.SOURCE_EVIDENCE));
        var provenance = MemoryFixtures.provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM,
                "shipment-feed-77", MemoryFixtures.NOW);

        Episode first = service.recordIdempotent(MemoryFixtures.SCOPE, "Shipment delayed", "Carrier delay",
                EpisodeOutcome.PARTIAL_SUCCESS, start, completed, MemoryFixtures.NOW,
                List.of(MemoryFixtures.SUPPLIER), List.of(event), List.of(), provenance, idempotency);
        Episode verified = service.verify(first.id(), first.version(), "reviewer-77",
                MemoryFixtures.REVIEW_EVIDENCE, MemoryFixtures.NOW.plusSeconds(10));
        Episode retry = service.recordIdempotent(MemoryFixtures.SCOPE, "Shipment delayed", "Carrier delay",
                EpisodeOutcome.PARTIAL_SUCCESS, start, completed, MemoryFixtures.NOW,
                List.of(MemoryFixtures.SUPPLIER), List.of(event), List.of(), provenance, idempotency);

        assertEquals(verified, retry, "retry after review must return the already reviewed episode");
    }

    @Test
    void unverifiedEpisodeCannotBePromotedWithoutReviewEvidence() {
        var episode = record();
        assertThrows(NullPointerException.class, () -> service.verify(episode.id(), 0, "reviewer-1", null,
                MemoryFixtures.NOW.plusSeconds(10)));
        assertEquals(EpisodeStatus.RECORDED, service.load(episode.id()).status());
    }

    @Test
    void scopeIsolationPreventsCrossTenantEpisodeRetrieval() {
        var episode = record();
        service.verify(episode.id(), 0, "reviewer-1", MemoryFixtures.REVIEW_EVIDENCE,
                MemoryFixtures.NOW.plusSeconds(10));
        assertTrue(service.retrieveVerified(new EpisodeQuery(MemoryFixtures.OTHER_SCOPE, MemoryFixtures.SUPPLIER, MemoryFixtures.NOW.plusSeconds(10), 10)).isEmpty());
    }

    @Test
    void rejectionIsAuditedAndNeverReturnedAsVerifiedExperience() {
        var episode = record();
        var rejected = service.reject(episode.id(), 0, "reviewer-2", MemoryFixtures.REVIEW_EVIDENCE,
                MemoryFixtures.NOW.plusSeconds(10));
        assertEquals(EpisodeStatus.REJECTED, rejected.status());
        assertEquals("reviewer-2", rejected.reviewedBy());
        assertTrue(service.retrieveVerified(new EpisodeQuery(MemoryFixtures.SCOPE, MemoryFixtures.SUPPLIER, MemoryFixtures.NOW.plusSeconds(10), 10)).isEmpty());
    }

    @Test
    void verifiedEpisodesAreRetainedWhileExpiredUnverifiedRecordsCanBePurged() {
        var oldStart = MemoryFixtures.NOW.minus(Duration.ofDays(10));
        var oldCompleted = MemoryFixtures.NOW.minus(Duration.ofDays(9));
        var oldRecorded = MemoryFixtures.NOW.minus(Duration.ofDays(9));
        var old = service.record(MemoryFixtures.SCOPE, "Old episode", "Unverified", EpisodeOutcome.UNKNOWN,
                oldStart, oldCompleted, oldRecorded, List.of(MemoryFixtures.SUPPLIER), List.of(), List.of(),
                MemoryFixtures.provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM, "old-1", oldRecorded));
        var fresh = record();
        service.verify(fresh.id(), 0, "reviewer-1", MemoryFixtures.REVIEW_EVIDENCE, MemoryFixtures.NOW.plusSeconds(10));
        assertEquals(1, repository.deleteExpiredUnverified(MemoryFixtures.NOW));
        assertTrue(repository.findById(old.id()).isEmpty());
        assertEquals(EpisodeStatus.VERIFIED, service.load(fresh.id()).status());
    }

    @Test
    void modelDerivedEpisodeMayUseEvidenceAttachedToItsEvents() {
        var start = MemoryFixtures.NOW.minusSeconds(300);
        var completed = MemoryFixtures.NOW.minusSeconds(60);
        var eventEvidence = new EvidenceReference("supplier-notification", "MAIL-44");
        var event = new EpisodeEvent("supplier-notification", "Supplier notice interpreted", completed,
                List.of(eventEvidence));
        var recorded = service.record(MemoryFixtures.SCOPE, "Supplier notice", "Model-derived summary",
                EpisodeOutcome.UNKNOWN, start, completed, MemoryFixtures.NOW,
                List.of(MemoryFixtures.SUPPLIER), List.of(event), List.of(),
                MemoryFixtures.provenance(ProvenanceSource.MODEL_DERIVED, "model-summary-1", MemoryFixtures.NOW));

        assertTrue(recorded.evidence().contains(eventEvidence));
    }

    @Test
    void modelDerivedEpisodeWithoutAnyEventOrDirectEvidenceIsRejected() {
        var start = MemoryFixtures.NOW.minusSeconds(300);
        var completed = MemoryFixtures.NOW.minusSeconds(60);
        assertThrows(IllegalArgumentException.class, () -> service.record(
                MemoryFixtures.SCOPE, "Unsupported summary", "No source evidence", EpisodeOutcome.UNKNOWN,
                start, completed, MemoryFixtures.NOW, List.of(), List.of(), List.of(),
                MemoryFixtures.provenance(ProvenanceSource.MODEL_DERIVED, "model-summary-2", MemoryFixtures.NOW)));
    }

    @Test
    void supersededEpisodeRemainsHistoricallyRetrievableOnlyBeforeSupersession() {
        var episode = record();
        var verifiedAt = MemoryFixtures.NOW.plusSeconds(10);
        service.verify(episode.id(), 0, "reviewer-1", MemoryFixtures.REVIEW_EVIDENCE, verifiedAt);
        var supersededAt = verifiedAt.plusSeconds(10);
        service.supersede(episode.id(), 1, supersededAt);

        assertEquals(1, service.retrieveVerified(new EpisodeQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, supersededAt.minusSeconds(1), 10)).size());
        assertTrue(service.retrieveVerified(new EpisodeQuery(MemoryFixtures.SCOPE,
                MemoryFixtures.SUPPLIER, supersededAt, 10)).isEmpty());
    }

    @Test
    void verifiedEpisodeCannotBeSupersededBeforeItsVerificationTime() {
        var episode = record();
        var verifiedAt = MemoryFixtures.NOW.plusSeconds(10);
        service.verify(episode.id(), 0, "reviewer-1", MemoryFixtures.REVIEW_EVIDENCE, verifiedAt);
        assertThrows(IllegalArgumentException.class, () -> service.load(episode.id()).supersede(
                MemoryFixtures.NOW.plusSeconds(9)));
    }

    @Test
    void eventCannotBeRecordedOutsideEpisodeTimeInterval() {
        var start = MemoryFixtures.NOW.minusSeconds(300);
        var completed = MemoryFixtures.NOW.minusSeconds(60);
        assertThrows(IllegalArgumentException.class, () -> service.record(MemoryFixtures.SCOPE, "Invalid", "Invalid event",
                EpisodeOutcome.UNKNOWN, start, completed, MemoryFixtures.NOW,
                List.of(new EntityReference("supplier", "SUP-01")),
                List.of(new EpisodeEvent("late", "outside interval", MemoryFixtures.NOW.plusSeconds(2), List.of())),
                List.of(), MemoryFixtures.provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM, "event", MemoryFixtures.NOW)));
    }
}
