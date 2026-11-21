package dev.supplychain.agent.mechanisms.b1;

import java.util.List;

/** B1 selection result including explicit evidence-diversity satisfaction for calibrated B6 governance. */
public record B1SelectionResult(List<B1MemoryCandidate> selectedCandidates,
                                List<B1CandidateScore> scores,
                                int usedContextUnits, int contextBudgetUnits,
                                int requiredDistinctMemoryKinds, int achievedDistinctMemoryKinds) {
    public B1SelectionResult {
        selectedCandidates = List.copyOf(selectedCandidates);
        scores = List.copyOf(scores);
        if (usedContextUnits < 0 || contextBudgetUnits < 1 || usedContextUnits > contextBudgetUnits) {
            throw new IllegalArgumentException("invalid context accounting");
        }
        if (requiredDistinctMemoryKinds < 1 || requiredDistinctMemoryKinds > 4
                || achievedDistinctMemoryKinds < 0 || achievedDistinctMemoryKinds > 4) {
            throw new IllegalArgumentException("invalid memory-diversity accounting");
        }
        long actualKinds = selectedCandidates.stream().map(B1MemoryCandidate::kind).distinct().count();
        if (actualKinds != achievedDistinctMemoryKinds) {
            throw new IllegalArgumentException("reported B1 memory diversity differs from selected candidates");
        }
    }

    /** Source-compatible constructor for legacy direct test fixtures. */
    public B1SelectionResult(List<B1MemoryCandidate> selectedCandidates, List<B1CandidateScore> scores,
                             int usedContextUnits, int contextBudgetUnits) {
        this(selectedCandidates, scores, usedContextUnits, contextBudgetUnits,
                1, (int) selectedCandidates.stream().map(B1MemoryCandidate::kind).distinct().count());
    }

    /**
     * A requirement of one is the neutral/unconstrained default. It does not require that
     * retrieval invent at least one memory candidate when the scoped long-term stores are empty.
     * Only calibrated diversity requirements above one create a fail-closed floor.
     */
    public boolean diversitySatisfied() {
        return requiredDistinctMemoryKinds <= 1 || achievedDistinctMemoryKinds >= requiredDistinctMemoryKinds;
    }
}
