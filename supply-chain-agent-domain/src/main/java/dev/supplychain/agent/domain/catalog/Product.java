package dev.supplychain.agent.domain.catalog;

import dev.supplychain.agent.domain.common.UnitOfMeasure;
import java.util.Objects;

/** A purchasable/stockable product identity. SKU uniqueness is checked by the snapshot. */
public record Product(ProductId id, String sku, String name, UnitOfMeasure unit, boolean active) {
    public Product {
        Objects.requireNonNull(id, "id");
        sku = requireText(sku, "sku");
        name = requireText(name, "name");
        Objects.requireNonNull(unit, "unit");
    }
    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return result;
    }
}
