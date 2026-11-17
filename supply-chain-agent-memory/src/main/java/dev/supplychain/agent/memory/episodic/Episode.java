package dev.supplychain.agent.memory.episodic;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Recorded experience. Only VERIFIED episodes are eligible for trusted experience retrieval. */
public record Episode(
        EpisodeId id,
        MemoryScopeId scopeId,
        long version,
        String title,
        String summary,
        EpisodeOutcome outcome,
        EpisodeStatus status,
        Instant startedAt,
        Instant completedAt,
        Instant recordedAt,
        Instant retentionUntil,
        List<EntityReference> entities,
        List<EpisodeEvent> events,
        List<EvidenceReference> evidence,
        Provenance provenance,
        String reviewedBy,
        Instant reviewedAt,
        EvidenceReference reviewEvidence,
        Instant supersededAt) {

    public Episode {
        id = Objects.requireNonNull(id, "id");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        title = require(title, "title");
        summary = require(summary, "summary");
        outcome = Objects.requireNonNull(outcome, "outcome");
        status = Objects.requireNonNull(status, "status");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        retentionUntil = retentionUntil == null ? null : retentionUntil;
        if (completedAt.isBefore(startedAt) || recordedAt.isBefore(completedAt)) {
            throw new IllegalArgumentException("invalid episode timestamps");
        }
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        var allEvidence = new java.util.ArrayList<>(Objects.requireNonNull(evidence, "evidence"));
        events.forEach(event -> allEvidence.addAll(event.evidence()));
        evidence = allEvidence.stream().distinct().toList();
        provenance = Objects.requireNonNull(provenance, "provenance");
        if (!provenance.recordedAt().equals(recordedAt)) {
            throw new IllegalArgumentException("episode recordedAt must match provenance recordedAt");
        }
        for (EpisodeEvent event : events) {
            if (event.occurredAt().isBefore(startedAt) || event.occurredAt().isAfter(completedAt)) {
                throw new IllegalArgumentException("episode event falls outside episode interval");
            }
        }
        boolean hasReview = reviewedBy != null || reviewedAt != null || reviewEvidence != null;
        if (status == EpisodeStatus.RECORDED) {
            if (hasReview || supersededAt != null || retentionUntil == null) {
                throw new IllegalArgumentException("recorded episode requires retention and cannot contain review/supersession metadata");
            }
        } else if (status == EpisodeStatus.VERIFIED || status == EpisodeStatus.REJECTED
                || status == EpisodeStatus.SUPERSEDED) {
            if (reviewedBy == null || reviewedBy.isBlank() || reviewedAt == null || reviewEvidence == null) {
                throw new IllegalArgumentException("reviewed episode requires reviewer, time and evidence");
            }
            if (reviewedAt.isBefore(recordedAt)) throw new IllegalArgumentException("review precedes recording");
        }
        if (status == EpisodeStatus.VERIFIED && (retentionUntil != null || supersededAt != null)) {
            throw new IllegalArgumentException("verified episode does not expire under unverified retention and is not superseded");
        }
        if ((status == EpisodeStatus.VERIFIED || status == EpisodeStatus.REJECTED) &&
                (retentionUntil != null || supersededAt != null)) {
            throw new IllegalArgumentException("reviewed episode is not eligible for unverified retention or supersession");
        }
        if (status == EpisodeStatus.SUPERSEDED && (supersededAt == null || supersededAt.isBefore(reviewedAt)
                || retentionUntil != null)) {
            throw new IllegalArgumentException("superseded episode requires a supersession time after verification and no unverified retention");
        }
    }

    public static Episode recorded(EpisodeId id, MemoryScopeId scope, String title, String summary,
                                   EpisodeOutcome outcome, Instant startedAt, Instant completedAt,
                                   Instant recordedAt, Instant retentionUntil,
                                   List<EntityReference> entities, List<EpisodeEvent> events,
                                   List<EvidenceReference> evidence, Provenance provenance) {
        return new Episode(id, scope, 0, title, summary, outcome, EpisodeStatus.RECORDED,
                startedAt, completedAt, recordedAt, retentionUntil, entities, events, evidence,
                provenance, null, null, null, null);
    }

    public Episode verify(String actor, EvidenceReference reviewRef, Instant at) {
        if (status != EpisodeStatus.RECORDED) throw new IllegalStateException("only recorded episodes can be verified");
        if (at.isBefore(recordedAt)) throw new IllegalArgumentException("verification cannot precede recording");
        return new Episode(id, scopeId, version + 1, title, summary, outcome, EpisodeStatus.VERIFIED,
                startedAt, completedAt, recordedAt, null, entities, events, merge(evidence, reviewRef), provenance,
                require(actor, "actor"), at, reviewRef, null);
    }

    public Episode reject(String actor, EvidenceReference reviewRef, Instant at) {
        if (status != EpisodeStatus.RECORDED) throw new IllegalStateException("only recorded episodes can be rejected");
        if (at.isBefore(recordedAt)) throw new IllegalArgumentException("review cannot precede recording");
        return new Episode(id, scopeId, version + 1, title, summary, outcome, EpisodeStatus.REJECTED,
                startedAt, completedAt, recordedAt, null, entities, events, evidence, provenance,
                require(actor, "actor"), at, Objects.requireNonNull(reviewRef, "reviewRef"), null);
    }

    public boolean knownAsVerifiedAt(Instant knownAt) {
        if (reviewedAt == null || reviewedAt.isAfter(knownAt)) return false;
        return status == EpisodeStatus.VERIFIED || (status == EpisodeStatus.SUPERSEDED && knownAt.isBefore(supersededAt));
    }

    public Episode supersede(Instant at) {
        if (status != EpisodeStatus.VERIFIED) throw new IllegalStateException("only verified episodes can be superseded");
        if (at.isBefore(reviewedAt)) throw new IllegalArgumentException("supersededAt precedes verification");
        return new Episode(id, scopeId, version + 1, title, summary, outcome, EpisodeStatus.SUPERSEDED,
                startedAt, completedAt, recordedAt, null, entities, events, evidence, provenance,
                reviewedBy, reviewedAt, reviewEvidence, at);
    }

    private static List<EvidenceReference> merge(List<EvidenceReference> existing, EvidenceReference added) {
        var refs = new java.util.ArrayList<>(existing);
        refs.add(Objects.requireNonNull(added, "reviewRef"));
        return refs.stream().distinct().toList();
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
