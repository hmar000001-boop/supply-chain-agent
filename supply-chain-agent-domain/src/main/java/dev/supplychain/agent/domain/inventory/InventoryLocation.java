package dev.supplychain.agent.domain.inventory;

import java.util.Objects;

public record InventoryLocation(InventoryLocationId id, String code, String region, boolean active) {
    public InventoryLocation {
        Objects.requireNonNull(id, "id");
        code = text(code, "code");
        region = text(region, "region");
    }
    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return result;
    }
}
