package dev.supplychain.agent.infrastructure.postgres.memory;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodeEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable digest of the immutable outcome payload used by the episodic append-idempotency contract. */
final class EpisodePayloadFingerprint {
    private EpisodePayloadFingerprint() { }

    static String sha256(Episode episode) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                write(out, episode.scopeId().value());
                write(out, episode.title());
                write(out, episode.summary());
                write(out, episode.outcome().name());
                write(out, episode.startedAt());
                write(out, episode.completedAt());
                out.writeInt(episode.entities().size());
                for (EntityReference entity : episode.entities()) {
                    write(out, entity.entityType()); write(out, entity.entityId());
                }
                out.writeInt(episode.events().size());
                for (EpisodeEvent event : episode.events()) {
                    write(out, event.eventType()); write(out, event.description()); write(out, event.occurredAt());
                    writeEvidence(out, event.evidence());
                }
                // Episode.verify() adds reviewEvidence to Episode.evidence(); that is lifecycle metadata,
                // not part of the immutable payload reserved by appendIdempotent().
                boolean verificationAddedReviewEvidence = (episode.status() == dev.supplychain.agent.memory.episodic.EpisodeStatus.VERIFIED
                        || episode.status() == dev.supplychain.agent.memory.episodic.EpisodeStatus.SUPERSEDED)
                        && episode.reviewEvidence() != null && episode.evidence().contains(episode.reviewEvidence());
                java.util.List<EvidenceReference> payloadEvidence = verificationAddedReviewEvidence
                        ? episode.evidence().stream().filter(ref -> !ref.equals(episode.reviewEvidence())).toList()
                        : episode.evidence();
                writeEvidence(out, payloadEvidence);
                write(out, episode.provenance().source().name());
                write(out, episode.provenance().sourceId());
                write(out, episode.provenance().observedAt());
                writeNullable(out, episode.provenance().sourceVersion());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory episode fingerprint encoding failed", impossible);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void writeEvidence(DataOutputStream out, java.util.List<EvidenceReference> evidence) throws IOException {
        out.writeInt(evidence.size());
        for (EvidenceReference reference : evidence) {
            write(out, reference.evidenceType()); write(out, reference.evidenceId());
        }
    }

    private static void write(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length); out.write(encoded);
    }

    private static void writeNullable(DataOutputStream out, String value) throws IOException {
        if (value == null) out.writeInt(-1); else write(out, value);
    }

    private static void write(DataOutputStream out, java.time.Instant value) throws IOException {
        out.writeLong(value.getEpochSecond()); out.writeInt(value.getNano());
    }
}
