package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.supplier.SupplierId;
import java.util.Objects;

public record ReadSupplierRequest(SupplierId supplierId) {
    public ReadSupplierRequest { supplierId = Objects.requireNonNull(supplierId, "supplierId"); }
}
