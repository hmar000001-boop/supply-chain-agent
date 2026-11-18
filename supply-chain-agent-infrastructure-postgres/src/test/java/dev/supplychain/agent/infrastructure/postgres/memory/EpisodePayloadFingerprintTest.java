package dev.supplychain.agent.infrastructure.postgres.memory;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodeId;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EpisodePayloadFingerprintTest {
    @Test
    void lifecycleMetadataAndGeneratedIdDoNotChangeIdempotencyFingerprint() {
        Instant started = Instant.parse("2026-11-17T12:00:00Z");
        Instant completed = started.plusSeconds(90);
        Instant recorded = completed.plusSeconds(1);
        Provenance provenance = new Provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM,
                "shipment-feed", started, recorded, "v1");
        EvidenceReference evidence = new EvidenceReference("operation", "op-1");

        Episode original = Episode.recorded(EpisodeId.random(),
                new dev.supplychain.agent.memory.common.MemoryScopeId("scope-a"),
                "Shipment delayed", "Carrier reported a delay", EpisodeOutcome.PARTIAL_SUCCESS,
                started, completed, recorded, recorded.plusSeconds(3600), List.of(), List.of(),
                List.of(evidence), provenance);
        Episode retry = new Episode(new EpisodeId("different-generated-id"), original.scopeId(), 0,
                original.title(), original.summary(), original.outcome(), original.status(),
                original.startedAt(), original.completedAt(), original.recordedAt(), original.retentionUntil(),
                original.entities(), original.events(), original.evidence(), original.provenance(),
                null, null, null, null);

        assertEquals(EpisodePayloadFingerprint.sha256(original), EpisodePayloadFingerprint.sha256(retry));

        Episode verified = original.verify("reviewer",
                new EvidenceReference("review", "review-1"), recorded.plusSeconds(20));
        assertEquals(EpisodePayloadFingerprint.sha256(original), EpisodePayloadFingerprint.sha256(verified),
                "verification metadata must not change the immutable idempotency payload");

        Episode changed = new Episode(new EpisodeId("different-generated-id-2"), original.scopeId(), 0,
                original.title(), "A different outcome", EpisodeOutcome.FAILURE, original.status(),
                original.startedAt(), original.completedAt(), original.recordedAt(), original.retentionUntil(),
                original.entities(), original.events(), original.evidence(), original.provenance(),
                null, null, null, null);
        assertNotEquals(EpisodePayloadFingerprint.sha256(original), EpisodePayloadFingerprint.sha256(changed));
    }
}
