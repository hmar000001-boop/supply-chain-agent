package dev.supplychain.agent.mechanisms.b1;

import java.util.List;

public record B1SelectionResult(List<B1MemoryCandidate> selectedCandidates,
                                List<B1CandidateScore> scores,
                                int usedContextUnits, int contextBudgetUnits) {
    public B1SelectionResult {
        selectedCandidates = List.copyOf(selectedCandidates);
        scores = List.copyOf(scores);
        if (usedContextUnits < 0 || contextBudgetUnits < 1 || usedContextUnits > contextBudgetUnits) {
            throw new IllegalArgumentException("invalid context accounting");
        }
    }
}
