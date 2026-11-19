package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;

/** Trusted host adapter that supplies provenance unavailable in the minimal cognitive observation value. */
@FunctionalInterface
public interface ObservationProvenanceResolver {
    ObservationProvenance resolve(CognitiveCycleOperations.ExternalObservation observation);

    record ObservationProvenance(String executionId, String principalId, String sourceReference,
                                 java.time.Instant observedAt) {
        public ObservationProvenance {
            executionId = required(executionId, "executionId");
            principalId = required(principalId, "principalId");
            sourceReference = required(sourceReference, "sourceReference");
            java.util.Objects.requireNonNull(observedAt, "observedAt");
        }
        private static String required(String value, String name) {
            String result = java.util.Objects.requireNonNull(value, name).trim();
            if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
            return result;
        }
    }
}
