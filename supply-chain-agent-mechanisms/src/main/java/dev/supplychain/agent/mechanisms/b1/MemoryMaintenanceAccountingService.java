package dev.supplychain.agent.mechanisms.b1;

import java.time.Instant;
import java.util.Objects;

public final class MemoryMaintenanceAccountingService {
    private final MemoryMaintenanceLedger ledger;
    public MemoryMaintenanceAccountingService(MemoryMaintenanceLedger ledger) { this.ledger = Objects.requireNonNull(ledger, "ledger"); }
    public boolean record(MemoryMaintenanceEvent event) { return ledger.appendIfAbsent(event); }
    public double actualCost(String candidateId, Instant from, Instant to) {
        return ledger.findForCandidate(candidateId, from, to).stream().mapToDouble(MemoryMaintenanceEvent::costUtilityPoints).sum();
    }
}
