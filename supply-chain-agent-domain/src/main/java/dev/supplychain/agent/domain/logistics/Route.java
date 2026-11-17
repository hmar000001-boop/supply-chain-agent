package dev.supplychain.agent.domain.logistics;

import java.time.Duration;
import java.util.Objects;

public record Route(RouteId id, String origin, String destination, Duration transitTime, boolean active) {
    public Route {
        Objects.requireNonNull(id, "id");
        origin = text(origin, "origin");
        destination = text(destination, "destination");
        Objects.requireNonNull(transitTime, "transitTime");
        if (transitTime.isNegative()) throw new IllegalArgumentException("transitTime cannot be negative");
    }
    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return result;
    }
}
