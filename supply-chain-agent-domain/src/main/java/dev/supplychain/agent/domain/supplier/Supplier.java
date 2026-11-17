package dev.supplychain.agent.domain.supplier;

import dev.supplychain.agent.domain.catalog.ProductId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Supplier master data and product-specific qualifications/capacity. */
public record Supplier(
        SupplierId id,
        String name,
        SupplierStatus status,
        Map<ProductId, SupplierProductCapability> capabilities,
        long version) {
    public Supplier {
        Objects.requireNonNull(id, "id");
        name = requireText(name, "name");
        Objects.requireNonNull(status, "status");
        capabilities = Map.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        capabilities.forEach((productId, capability) -> {
            if (!productId.equals(capability.productId())) {
                throw new IllegalArgumentException("Supplier capability map key does not match capability product");
            }
        });
    }
    public Optional<SupplierProductCapability> capability(ProductId productId) {
        return Optional.ofNullable(capabilities.get(productId));
    }
    public Supplier withCapability(SupplierProductCapability capability) {
        Map<ProductId, SupplierProductCapability> changed = new LinkedHashMap<>(capabilities);
        changed.put(capability.productId(), capability);
        return new Supplier(id, name, status, changed, version + 1);
    }
    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return result;
    }
}
