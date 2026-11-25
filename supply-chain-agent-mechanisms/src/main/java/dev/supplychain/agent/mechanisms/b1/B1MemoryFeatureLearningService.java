package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.mechanisms.b3.VerifiedDownstreamOutcomeEvidence;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Durable B1 access-history and cue-association learning. Only outcome capabilities minted after
 * B3 receipt and verifier revalidation can update the persisted outcome statistics. The normalized
 * outcome statistic is not substituted for the task-specific utility supplied by the candidate adapter.
 */
public final class B1MemoryFeatureLearningService {
    private static final int MAX_CAS_ATTEMPTS = 8;
    private final B1MemoryFeatureRepository repository;

    public B1MemoryFeatureLearningService(B1MemoryFeatureRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Applies persisted access/verified-use history and cue-association strengths before scoring.
     * The candidate adapter's task-specific downstream utility is preserved: the persisted outcome
     * statistic is normalized to [0,1] and is not calibrated to the provider's utility scale.
     */
    public List<B1MemoryCandidate> enrich(List<B1MemoryCandidate> candidates, Instant at) {
        Objects.requireNonNull(at, "at");
        return Objects.requireNonNull(candidates, "candidates").stream().map(candidate -> {
            var state = repository.find(reference(candidate)).orElse(null);
            if (state == null || state.revision() == 0) return candidate;
            var history = new ArrayList<>(candidate.accessHistory());
            history.addAll(state.accessHistory());
            // Verified downstream use is an accessibility signal distinct from retrieval selection.
            history.addAll(state.verifiedUseHistory());
            history = new ArrayList<>(history.stream().distinct().sorted().toList());
            if (history.size() > 64) history = new ArrayList<>(history.subList(history.size() - 64, history.size()));
            var cues = new TreeMap<>(candidate.cueAssociationStrengths());
            state.cueAssociationStrengths().forEach(cues::put);
            return new B1MemoryCandidate(candidate.candidateId(), candidate.scopeId(), candidate.kind(),
                    candidate.memoryReference(), history, cues, candidate.expectedDownstreamUtility(),
                    candidate.contextUnits(), candidate.measuredInfrastructureLatency(),
                    candidate.maintenanceProfile());
        }).toList();
    }

    /** Idempotent on execution/evidence-set/candidate so checkpoint retries do not inflate frequency. */
    public void recordSelected(List<B1MemoryCandidate> selected, Set<String> queryCues,
            String executionId, String evidenceSetId, Instant at) {
        String eventBase = required(executionId, "executionId") + "|" + required(evidenceSetId, "evidenceSetId");
        for (B1MemoryCandidate candidate : Objects.requireNonNull(selected, "selected")) {
            Objects.requireNonNull(candidate, "selected candidate");
            update(reference(candidate), current -> current.recordAccess(
                    eventBase + "|" + candidate.candidateId(), queryCues, at));
        }
    }

    /** A verified B3 outcome updates only the exact memory revisions explicitly attributed to it. */
    public void applyVerifiedOutcome(VerifiedDownstreamOutcomeEvidence verified) {
        Objects.requireNonNull(verified, "verified");
        var outcome = verified.evidence();
        for (var attribution : outcome.memoryAttributions()) {
            if (!attribution.reference().scopeId().equals(outcome.scopeId()))
                throw new SecurityException("B1 attributed memory does not belong to the verified B3 outcome scope");
            update(attribution.reference(), current -> current.applyVerifiedOutcome(
                    outcome.idempotencyKey(), outcome.signal(), attribution.queryCues(), outcome.verifiedAt()));
        }
    }

    public java.util.Optional<B1MemoryFeatureState> find(B1MemoryCandidateReference reference) {
        return repository.find(Objects.requireNonNull(reference, "reference"));
    }

    private void update(B1MemoryCandidateReference reference,
            java.util.function.UnaryOperator<B1MemoryFeatureState> operation) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            var existing = repository.find(reference);
            var current = existing.orElseGet(() -> B1MemoryFeatureState.empty(reference, Instant.EPOCH));
            var next = operation.apply(current);
            if (next == current) return;
            if (existing.isEmpty()) {
                try { repository.saveNew(next); return; }
                catch (IllegalStateException concurrentInsert) { /* retry against authoritative row */ }
            } else if (repository.compareAndSet(reference, current.revision(), next)) return;
        }
        throw new IllegalStateException("B1 feature state changed concurrently after bounded CAS retries");
    }

    /** Creates the exact attribution binding used by B3 receipt evidence for a mapped workspace item. */
    public B1MemoryAttribution attribute(B1MemoryCandidate candidate, Set<String> queryCues,
            String sourceCandidateFingerprint) {
        Objects.requireNonNull(candidate, "candidate");
        return new B1MemoryAttribution(reference(candidate), queryCues, sourceCandidateFingerprint);
    }

    private static B1MemoryCandidateReference reference(B1MemoryCandidate candidate) {
        String ref = candidate.memoryReference();
        int colon = ref.indexOf(':');
        int at = ref.lastIndexOf('@');
        if (colon < 1 || at <= colon + 1) throw new IllegalArgumentException("B1 memory reference must be kind:itemId@version");
        return new B1MemoryCandidateReference(candidate.kind(), candidate.scopeId(), ref.substring(colon + 1, at),
                Long.parseLong(ref.substring(at + 1)));
    }

    private static String required(String value,String name){String s=Objects.requireNonNull(value,name).trim();if(s.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return s;}
}
