package dev.supplychain.agent.domain.orders;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CustomerOrder(
        CustomerOrderId id,
        String customerReference,
        OrderPriority priority,
        OrderStatus status,
        Instant dueAt,
        Map<OrderLineId, OrderLine> lines,
        long version) {
    public CustomerOrder {
        Objects.requireNonNull(id, "id");
        customerReference = text(customerReference, "customerReference");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(dueAt, "dueAt");
        lines = Map.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) throw new IllegalArgumentException("Customer order must contain at least one line");
        lines.forEach((lineId, line) -> {
            if (!lineId.equals(line.id())) throw new IllegalArgumentException("Order-line map key mismatch");
        });
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
    }
    public CustomerOrder withLine(OrderLine changedLine) {
        if (!lines.containsKey(changedLine.id())) throw new IllegalArgumentException("Unknown order line");
        Map<OrderLineId, OrderLine> changed = new LinkedHashMap<>(lines);
        changed.put(changedLine.id(), changedLine);
        return new CustomerOrder(id, customerReference, priority, status, dueAt, changed, version + 1);
    }
    public boolean acceptsReservations() {
        return status == OrderStatus.OPEN || status == OrderStatus.IN_PROGRESS;
    }
    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return result;
    }
}
