package dev.supplychain.agent.mechanisms.b1;

import java.time.Instant;
import java.util.List;

public interface MemoryMaintenanceLedger {
    /** @return false only when the same event ID and identical event were already recorded. */
    boolean appendIfAbsent(MemoryMaintenanceEvent event);
    List<MemoryMaintenanceEvent> findForCandidate(String candidateId, Instant fromInclusive, Instant toExclusive);
}
