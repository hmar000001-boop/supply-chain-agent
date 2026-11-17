package dev.supplychain.agent.memory.retrieval;

import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.procedural.ProcedureVersion;
import dev.supplychain.agent.memory.semantic.SemanticFact;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import java.util.List;
import java.util.Optional;

public record MemoryRetrievalResult(
        Optional<WorkingMemorySnapshot> workingMemory,
        List<Episode> verifiedEpisodes,
        List<SemanticFact> activeSemanticFacts,
        List<ProcedureVersion> activeProcedures) {
    public MemoryRetrievalResult {
        workingMemory = workingMemory == null ? Optional.empty() : workingMemory;
        verifiedEpisodes = List.copyOf(verifiedEpisodes);
        activeSemanticFacts = List.copyOf(activeSemanticFacts);
        activeProcedures = List.copyOf(activeProcedures);
    }
}
