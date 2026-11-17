package dev.supplychain.agent.memory.inmemory;

import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.semantic.FactStatus;
import dev.supplychain.agent.memory.semantic.SemanticFact;
import dev.supplychain.agent.memory.semantic.SemanticFactId;
import dev.supplychain.agent.memory.semantic.SemanticFactRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic reference adapter; durable adapters must preserve the same conflict and replacement semantics. */
public final class InMemorySemanticFactRepository implements SemanticFactRepository {
    private final ConcurrentHashMap<SemanticFactId, SemanticFact> facts = new ConcurrentHashMap<>();

    @Override
    public SemanticFact stage(SemanticFact fact) {
        if (fact.status() != FactStatus.PROPOSED || facts.putIfAbsent(fact.id(), fact) != null) {
            throw new MemoryConflictException("Only new proposed facts can be staged: " + fact.id().value());
        }
        return fact;
    }

    @Override
    public synchronized Optional<SemanticFact> findById(SemanticFactId id) { return Optional.ofNullable(facts.get(id)); }

    @Override
    public synchronized SemanticFact activate(SemanticFactId id, long expectedVersion, String actor,
                                               dev.supplychain.agent.memory.common.EvidenceReference approvalEvidence, Instant at) {
        SemanticFact current = require(id);
        checkVersion(current, expectedVersion);
        SemanticFact active = current.activate(actor, approvalEvidence, at, null);
        assertNoConflictingActiveFact(active, null);
        facts.put(id, active);
        return active;
    }

    @Override
    public synchronized SemanticFact supersedeAndActivate(SemanticFactId oldId, long expectedOldVersion,
                                                            SemanticFactId newId, long expectedNewVersion,
                                                            String actor, dev.supplychain.agent.memory.common.EvidenceReference approvalEvidence, Instant at) {
        return supersedeAndActivateSegments(oldId, expectedOldVersion,
                List.of(new dev.supplychain.agent.memory.semantic.SemanticFactRevision(newId, expectedNewVersion)),
                actor, approvalEvidence, at).getFirst();
    }

    @Override
    public synchronized List<SemanticFact> supersedeAndActivateSegments(
            SemanticFactId oldId, long expectedOldVersion,
            List<dev.supplychain.agent.memory.semantic.SemanticFactRevision> replacements,
            String actor, dev.supplychain.agent.memory.common.EvidenceReference approvalEvidence, Instant at) {
        SemanticFact oldFact = require(oldId);
        checkVersion(oldFact, expectedOldVersion);
        if (oldFact.status() != FactStatus.ACTIVE) throw new IllegalStateException("only an active fact can be temporally amended");
        if (replacements == null || replacements.isEmpty()) throw new IllegalArgumentException("at least one replacement segment is required");

        var proposed = new java.util.ArrayList<SemanticFact>();
        var seenReplacementIds = new java.util.HashSet<SemanticFactId>();
        for (var revision : replacements) {
            if (!seenReplacementIds.add(revision.factId())) {
                throw new IllegalArgumentException("replacement fact ids must be unique");
            }
            SemanticFact candidate = require(revision.factId());
            checkVersion(candidate, revision.expectedVersion());
            if (candidate.status() != FactStatus.PROPOSED || !candidate.key().equals(oldFact.key())) {
                throw new IllegalStateException("every replacement must be a proposed fact for the same semantic key");
            }
            proposed.add(candidate);
        }
        proposed.sort(Comparator.comparing(SemanticFact::validFrom));
        Instant expectedStart = oldFact.validFrom();
        for (SemanticFact segment : proposed) {
            if (!segment.validFrom().equals(expectedStart)) {
                throw new IllegalArgumentException("replacement segments must be contiguous and cover the old validity interval");
            }
            expectedStart = segment.validUntil();
            if (expectedStart == null && segment != proposed.getLast()) {
                throw new IllegalArgumentException("an unbounded replacement segment must be last");
            }
        }
        if (!java.util.Objects.equals(expectedStart, oldFact.validUntil())) {
            throw new IllegalArgumentException("replacement segments must preserve the complete old validity interval");
        }
        var replacementIds = proposed.stream().map(SemanticFact::id).collect(java.util.stream.Collectors.toSet());
        var activated = proposed.stream().map(segment -> segment.activate(actor, approvalEvidence, at, oldId)).toList();
        for (SemanticFact candidate : activated) assertNoConflictingActiveFact(candidate, oldId, replacementIds);
        SemanticFact superseded = oldFact.supersede(at);
        // Validate every segment and conflict before mutating either the old fact or any replacement.
        facts.put(oldId, superseded);
        activated.forEach(segment -> facts.put(segment.id(), segment));
        return List.copyOf(activated);
    }

    @Override
    public synchronized SemanticFact reject(SemanticFactId id, long expectedVersion, String reviewer,
                                             dev.supplychain.agent.memory.common.EvidenceReference reviewEvidence,
                                             Instant at) {
        SemanticFact current = require(id);
        checkVersion(current, expectedVersion);
        SemanticFact rejected = current.reject(reviewer, reviewEvidence, at);
        facts.put(id, rejected);
        return rejected;
    }

    @Override
    public synchronized List<SemanticFact> findActive(dev.supplychain.agent.memory.semantic.SemanticFactQuery query) {
        return facts.values().stream()
                .filter(f -> f.knownAsActiveAt(query.knownAt()))
                .filter(f -> f.scopeId().equals(query.scopeId()) && f.subject().equals(query.subject()))
                .filter(f -> query.predicate() == null || f.predicate().equals(query.predicate()))
                .filter(f -> f.validAt(query.validAt()))
                .sorted(Comparator.comparing(SemanticFact::validFrom).reversed().thenComparing(f -> f.id().value()))
                .limit(query.limit()).toList();
    }

    @Override
    public synchronized int deleteExpiredSuperseded(Instant cutoff) {
        int before = facts.size();
        facts.entrySet().removeIf(entry -> entry.getValue().status() == FactStatus.SUPERSEDED
                && !entry.getValue().supersededAt().isAfter(cutoff));
        return before - facts.size();
    }

    private void assertNoConflictingActiveFact(SemanticFact candidate, SemanticFactId excludedId) {
        assertNoConflictingActiveFact(candidate, excludedId, java.util.Set.of());
    }

    private void assertNoConflictingActiveFact(SemanticFact candidate, SemanticFactId excludedId,
                                               java.util.Set<SemanticFactId> alsoExcluded) {
        for (SemanticFact existing : facts.values()) {
            if (existing.status() != FactStatus.ACTIVE || existing.id().equals(excludedId)
                    || alsoExcluded.contains(existing.id())) continue;
            if (existing.key().equals(candidate.key()) && existing.overlapsValidity(candidate)
                    && !existing.value().equals(candidate.value())) {
                throw new MemoryConflictException("Conflicting active semantic fact for " + candidate.key());
            }
        }
    }

    private SemanticFact require(SemanticFactId id) {
        SemanticFact fact = facts.get(id);
        if (fact == null) throw new MemoryNotFoundException("Semantic fact not found: " + id.value());
        return fact;
    }

    private static void checkVersion(SemanticFact fact, long expectedVersion) {
        if (fact.version() != expectedVersion) {
            throw new MemoryVersionConflictException("Fact version changed from " + expectedVersion + " to " + fact.version());
        }
    }
}
