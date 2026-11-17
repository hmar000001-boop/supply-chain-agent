package dev.supplychain.agent.domain.logistics;

import java.util.Objects;
import java.util.Set;

public record Carrier(CarrierId id, String name, Set<RouteId> supportedRoutes, boolean active) {
    public Carrier {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        name = name.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Carrier name cannot be blank");
        supportedRoutes = Set.copyOf(Objects.requireNonNull(supportedRoutes, "supportedRoutes"));
    }
}
