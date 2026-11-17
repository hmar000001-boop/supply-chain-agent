package dev.supplychain.agent.memory.semantic;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Enforces evidence/provenance admission before semantic knowledge becomes active. */
public final class SemanticMemoryService {
    private final SemanticFactRepository repository;
    private final SemanticAdmissionPolicy admissionPolicy;

    public SemanticMemoryService(SemanticFactRepository repository, SemanticAdmissionPolicy admissionPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.admissionPolicy = Objects.requireNonNull(admissionPolicy, "admissionPolicy");
    }

    public SemanticFact propose(SemanticFact fact) { return repository.stage(fact); }

    public SemanticFact activateTrusted(SemanticFactId id, long expectedVersion, String actor, Instant at) {
        SemanticFact fact = load(id);
        requireVersion(fact, expectedVersion);
        if (!admissionPolicy.mayActivateWithoutHumanReview(fact.provenance())) {
            throw new SecurityException("provenance requires explicit human review before activation");
        }
        return repository.activate(id, expectedVersion, actor,
                new EvidenceReference("provenance", fact.provenance().sourceId()), at);
    }

    public SemanticFact approveAndActivate(SemanticFactId id, long expectedVersion, String reviewer,
                                           EvidenceReference reviewEvidence, Instant at) {
        Objects.requireNonNull(reviewEvidence, "reviewEvidence");
        SemanticFact fact = load(id);
        requireVersion(fact, expectedVersion);
        if (fact.status() != FactStatus.PROPOSED) throw new IllegalStateException("fact is not proposed");
        return repository.activate(id, expectedVersion, reviewer, reviewEvidence, at);
    }

    public SemanticFact replaceActive(SemanticFactId oldId, long expectedOldVersion,
                                      SemanticFactId newId, long expectedNewVersion,
                                      String reviewer, EvidenceReference reviewEvidence, Instant at) {
        Objects.requireNonNull(reviewEvidence, "reviewEvidence");
        SemanticFact oldFact = load(oldId);
        SemanticFact newFact = load(newId);
        requireVersion(oldFact, expectedOldVersion);
        requireVersion(newFact, expectedNewVersion);
        if (oldFact.status() != FactStatus.ACTIVE || newFact.status() != FactStatus.PROPOSED
                || !oldFact.key().equals(newFact.key())) {
            throw new IllegalStateException("replacement must pair an active fact with a proposed fact for the same key");
        }
        if (!oldFact.validFrom().equals(newFact.validFrom())
                || !Objects.equals(oldFact.validUntil(), newFact.validUntil())) {
            throw new IllegalArgumentException("replacement must preserve the old fact's validity interval; use a separate temporal amendment for partial intervals");
        }
        return repository.supersedeAndActivate(oldId, expectedOldVersion, newId,
                expectedNewVersion, reviewer, reviewEvidence, at);
    }

    /** Atomically closes one active timeline and activates contiguous replacement segments. */
    public List<SemanticFact> reviseTimeline(SemanticFactId oldId, long expectedOldVersion,
                                             List<SemanticFactRevision> replacements,
                                             String reviewer, EvidenceReference reviewEvidence, Instant at) {
        Objects.requireNonNull(reviewEvidence, "reviewEvidence");
        SemanticFact oldFact = load(oldId);
        requireVersion(oldFact, expectedOldVersion);
        if (oldFact.status() != FactStatus.ACTIVE) throw new IllegalStateException("only active facts can be revised");
        for (SemanticFactRevision revision : Objects.requireNonNull(replacements, "replacements")) {
            SemanticFact candidate = load(revision.factId());
            requireVersion(candidate, revision.expectedVersion());
            if (!oldFact.key().equals(candidate.key()) || candidate.status() != FactStatus.PROPOSED) {
                throw new IllegalStateException("all replacement segments must be proposed facts for the same key");
            }
        }
        return repository.supersedeAndActivateSegments(oldId, expectedOldVersion,
                replacements, reviewer, reviewEvidence, at);
    }

    public SemanticFact reject(SemanticFactId id, long expectedVersion, String reviewer,
                               EvidenceReference reviewEvidence, Instant at) {
        Objects.requireNonNull(reviewEvidence, "reviewEvidence");
        SemanticFact fact = load(id);
        requireVersion(fact, expectedVersion);
        if (fact.status() != FactStatus.PROPOSED) throw new IllegalStateException("fact is not proposed");
        return repository.reject(id, expectedVersion, reviewer, reviewEvidence, at);
    }

    public List<SemanticFact> retrieveCurrent(SemanticFactQuery query) {
        return repository.findActive(query);
    }

    public SemanticFact load(SemanticFactId id) {
        return repository.findById(id).orElseThrow(() -> new MemoryNotFoundException("Semantic fact not found: " + id.value()));
    }

    private static void requireVersion(SemanticFact fact, long expected) {
        if (fact.version() != expected) throw new dev.supplychain.agent.memory.common.MemoryVersionConflictException(
                "Fact version is " + fact.version() + ", expected " + expected);
    }
}
