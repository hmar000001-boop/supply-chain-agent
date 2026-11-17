package dev.supplychain.agent.actions;

import dev.supplychain.agent.domain.catalog.Product;
import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.common.UnitOfMeasure;
import dev.supplychain.agent.domain.inventory.InventoryLocation;
import dev.supplychain.agent.domain.inventory.InventoryLocationId;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLine;
import dev.supplychain.agent.domain.orders.OrderLineId;
import dev.supplychain.agent.domain.orders.OrderPriority;
import dev.supplychain.agent.domain.orders.OrderStatus;
import dev.supplychain.agent.domain.supplier.Supplier;
import dev.supplychain.agent.domain.supplier.SupplierId;
import dev.supplychain.agent.domain.supplier.SupplierProductCapability;
import dev.supplychain.agent.domain.supplier.SupplierStatus;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

final class ActionTestFixtures {
    static final Instant NOW = Instant.parse("2026-11-17T13:00:00Z");
    static final ProductId PRODUCT = new ProductId("SKU-ACT-1");
    static final SupplierId SUPPLIER = new SupplierId("SUP-ACT-1");
    static final InventoryLocationId LOCATION = new InventoryLocationId("WH-ACT-1");
    static final InventoryLotId LOT = new InventoryLotId("LOT-ACT-1");
    static final CustomerOrderId ORDER = new CustomerOrderId("SO-ACT-1");
    static final OrderLineId LINE = new OrderLineId("SO-ACT-1-1");

    private ActionTestFixtures() { }

    static Quantity q(String value) { return Quantity.of(value, UnitOfMeasure.EACH); }

    static SupplyChainSnapshot world() {
        Product product = new Product(PRODUCT, "SKU-ACT-1", "Action Test Product", UnitOfMeasure.EACH, true);
        Supplier supplier = new Supplier(SUPPLIER, "Action Test Supplier", SupplierStatus.ACTIVE,
                Map.of(PRODUCT, new SupplierProductCapability(PRODUCT, q("100"), Duration.ofDays(2), true)), 0);
        InventoryLocation location = new InventoryLocation(LOCATION, "ACT-01", "Quebec", true);
        InventoryLot lot = new InventoryLot(LOT, PRODUCT, LOCATION, q("50"), LotQualityStatus.AVAILABLE,
                NOW.plus(Duration.ofDays(30)), null, null, 0);
        OrderLine line = new OrderLine(LINE, PRODUCT, q("40"), q("0"));
        CustomerOrder order = new CustomerOrder(ORDER, "ACTION-TEST-CUSTOMER", OrderPriority.NORMAL,
                OrderStatus.OPEN, NOW.plus(Duration.ofDays(7)), Map.of(LINE, line), 0);
        return SupplyChainSnapshot.builder(0, NOW).product(product).supplier(supplier)
                .location(location).inventoryLot(lot).customerOrder(order).build();
    }
}
