package dev.supplychain.agent.mechanisms.b1;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference adapter; M7 supplies durable transactional persistence. */
public final class InMemoryMemoryMaintenanceLedger implements MemoryMaintenanceLedger {
    private final ConcurrentHashMap<String, MemoryMaintenanceEvent> events = new ConcurrentHashMap<>();
    @Override public boolean appendIfAbsent(MemoryMaintenanceEvent event) {
        Objects.requireNonNull(event, "event");
        var existing = events.putIfAbsent(event.eventId(), event);
        if (existing == null) return true;
        if (!existing.equals(event)) throw new IllegalStateException("maintenance event ID reused with different content");
        return false;
    }
    @Override public List<MemoryMaintenanceEvent> findForCandidate(String candidateId, Instant from, Instant to) {
        if (candidateId == null || candidateId.isBlank()) throw new IllegalArgumentException("candidateId cannot be blank");
        Objects.requireNonNull(from, "from"); Objects.requireNonNull(to, "to");
        if (!to.isAfter(from)) throw new IllegalArgumentException("to must be after from");
        return events.values().stream().filter(e -> e.candidateId().equals(candidateId)
                && !e.occurredAt().isBefore(from) && e.occurredAt().isBefore(to))
                .sorted(Comparator.comparing(MemoryMaintenanceEvent::occurredAt).thenComparing(MemoryMaintenanceEvent::eventId)).toList();
    }
}
