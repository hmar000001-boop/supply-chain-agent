package dev.supplychain.agent.domain.recovery;

import dev.supplychain.agent.domain.orders.CustomerOrderId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record RecoveryObjective(
        RecoveryObjectiveId id,
        String description,
        Set<CustomerOrderId> affectedOrders,
        Instant targetFulfillmentAt,
        Set<RecoveryConstraint> hardConstraints,
        RecoveryObjectiveStatus status,
        long version) {
    public RecoveryObjective {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        description = description.trim();
        if (description.isEmpty()) throw new IllegalArgumentException("description cannot be blank");
        affectedOrders = Set.copyOf(Objects.requireNonNull(affectedOrders, "affectedOrders"));
        if (affectedOrders.isEmpty()) throw new IllegalArgumentException("Recovery objective must affect at least one order");
        Objects.requireNonNull(targetFulfillmentAt, "targetFulfillmentAt");
        hardConstraints = Set.copyOf(Objects.requireNonNull(hardConstraints, "hardConstraints"));
        Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
    }
}
