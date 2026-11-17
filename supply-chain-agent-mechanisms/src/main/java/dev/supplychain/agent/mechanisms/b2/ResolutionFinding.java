package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.util.List;
import java.util.Objects;

/** A finding, not an executable command or direct parent-state mutation. */
public record ResolutionFinding(String statement, List<EvidenceReference> evidence) {
    public ResolutionFinding {
        statement=Objects.requireNonNull(statement,"statement").trim();
        if(statement.isEmpty()) throw new IllegalArgumentException("statement cannot be blank");
        evidence=List.copyOf(Objects.requireNonNull(evidence,"evidence"));
        if(evidence.isEmpty()) throw new IllegalArgumentException("each finding requires at least one evidence reference");
    }
}
