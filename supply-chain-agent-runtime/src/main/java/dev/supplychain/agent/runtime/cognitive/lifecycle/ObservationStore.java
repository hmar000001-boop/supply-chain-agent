package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import java.util.Optional;

/** Store for an already reconciled observation so working-memory retries never repeat external reads/actions. */
public interface ObservationStore {
    void save(CognitiveCycleOperations.ExternalObservation observation);
    Optional<CognitiveCycleOperations.ExternalObservation> find(String observationId);
}
