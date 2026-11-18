package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.mechanisms.b2.ChildResolutionResult;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;

import java.util.Optional;

/** M6 child-result boundary; M7 supplies durable persistence across JVM restarts. */
public interface ImpasseChildArtifactStore {
    void save(String caseId, ChildResolutionResult result, DelegationReturn intentionReturn);
    Optional<ImpasseChildArtifact> find(String caseId);

    record ImpasseChildArtifact(ChildResolutionResult result, DelegationReturn intentionReturn) {
        public ImpasseChildArtifact {
            java.util.Objects.requireNonNull(result, "result");
            java.util.Objects.requireNonNull(intentionReturn, "intentionReturn");
        }
    }
}
