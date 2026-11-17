package dev.supplychain.agent.simulator;

import dev.supplychain.agent.domain.catalog.Product;
import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.catalog.ProductSubstitution;
import dev.supplychain.agent.domain.catalog.SubstitutionId;
import dev.supplychain.agent.domain.catalog.SubstitutionStatus;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.common.UnitOfMeasure;
import dev.supplychain.agent.domain.inventory.InventoryLocation;
import dev.supplychain.agent.domain.inventory.InventoryLocationId;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.logistics.Carrier;
import dev.supplychain.agent.domain.logistics.CarrierId;
import dev.supplychain.agent.domain.logistics.Route;
import dev.supplychain.agent.domain.logistics.RouteId;
import dev.supplychain.agent.domain.logistics.Shipment;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import dev.supplychain.agent.domain.logistics.ShipmentLine;
import dev.supplychain.agent.domain.logistics.ShipmentStatus;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLine;
import dev.supplychain.agent.domain.orders.OrderLineId;
import dev.supplychain.agent.domain.orders.OrderPriority;
import dev.supplychain.agent.domain.orders.OrderStatus;
import dev.supplychain.agent.domain.procurement.PurchaseOrder;
import dev.supplychain.agent.domain.procurement.PurchaseOrderId;
import dev.supplychain.agent.domain.procurement.PurchaseOrderLine;
import dev.supplychain.agent.domain.procurement.PurchaseOrderStatus;
import dev.supplychain.agent.domain.recovery.RecoveryAction;
import dev.supplychain.agent.domain.recovery.RecoveryCandidate;
import dev.supplychain.agent.domain.recovery.RecoveryCandidateId;
import dev.supplychain.agent.domain.recovery.RecoveryCandidateStatus;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.domain.supplier.Supplier;
import dev.supplychain.agent.domain.supplier.SupplierId;
import dev.supplychain.agent.domain.supplier.SupplierProductCapability;
import dev.supplychain.agent.domain.supplier.SupplierStatus;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ScenarioFixtures {
    static final Instant NOW = Instant.parse("2026-11-17T13:00:00Z");
    static final ProductId PRODUCT = new ProductId("SKU-100");
    static final ProductId SUBSTITUTE = new ProductId("SKU-100A");
    static final SupplierId SUPPLIER = new SupplierId("SUP-1");
    static final InventoryLocationId LOCATION = new InventoryLocationId("WH-MTL");
    static final InventoryLotId LOT = new InventoryLotId("LOT-100");
    static final InventoryLotId SUBSTITUTE_LOT = new InventoryLotId("LOT-100A");
    static final CustomerOrderId ORDER = new CustomerOrderId("SO-100");
    static final OrderLineId LINE = new OrderLineId("SO-100-1");
    static final PurchaseOrderId OPEN_PO = new PurchaseOrderId("PO-OPEN");
    static final CarrierId CARRIER = new CarrierId("CARRIER-1");
    static final RouteId ROUTE = new RouteId("ROUTE-MTL-TOR");
    static final ShipmentId SHIPMENT = new ShipmentId("SHIP-100");
    static final SubstitutionId SUBSTITUTION = new SubstitutionId("SUB-QUAL-1");
    static final RecoveryObjectiveId OBJECTIVE = new RecoveryObjectiveId("REC-100");
    static final RecoveryCandidateId CANDIDATE = new RecoveryCandidateId("CAND-1");

    private ScenarioFixtures() { }

    static Quantity q(String value) { return Quantity.of(value, UnitOfMeasure.EACH); }

    static SupplyChainSnapshot world() { return world(SubstitutionStatus.APPROVED); }

    static SupplyChainSnapshot world(SubstitutionStatus substitutionStatus) {
        Product product = new Product(PRODUCT, "SKU-100", "Precision valve", UnitOfMeasure.EACH, true);
        Product substitute = new Product(SUBSTITUTE, "SKU-100A", "Approved valve alternative", UnitOfMeasure.EACH, true);
        Supplier supplier = new Supplier(SUPPLIER, "Northstar Components", SupplierStatus.ACTIVE,
                Map.of(PRODUCT, new SupplierProductCapability(PRODUCT, q("100"), Duration.ofDays(2), true)), 0);
        InventoryLocation location = new InventoryLocation(LOCATION, "MTL-01", "Quebec", true);
        InventoryLot lot = new InventoryLot(LOT, PRODUCT, LOCATION, q("50"), LotQualityStatus.AVAILABLE,
                NOW.plus(Duration.ofDays(60)), null, null, 0);
        InventoryLot substituteLot = new InventoryLot(SUBSTITUTE_LOT, SUBSTITUTE, LOCATION, q("100"),
                LotQualityStatus.AVAILABLE, NOW.plus(Duration.ofDays(60)), null, null, 0);
        OrderLine orderLine = new OrderLine(LINE, PRODUCT, q("40"), q("0"));
        CustomerOrder order = new CustomerOrder(ORDER, "CUSTOMER-ALPHA", OrderPriority.HIGH,
                OrderStatus.OPEN, NOW.plus(Duration.ofDays(10)), Map.of(LINE, orderLine), 0);
        PurchaseOrder openPo = new PurchaseOrder(OPEN_PO, SUPPLIER, NOW.minus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(3)), PurchaseOrderStatus.CONFIRMED,
                List.of(new PurchaseOrderLine(PRODUCT, q("30"), q("0"), Money.of("12.50", "CAD"))), 0);
        Route route = new Route(ROUTE, "Montreal", "Toronto", Duration.ofDays(3), true);
        Carrier carrier = new Carrier(CARRIER, "Reliable Freight", Set.of(ROUTE), true);
        Shipment shipment = new Shipment(SHIPMENT, CARRIER, ROUTE,
                List.of(new ShipmentLine(ORDER, LINE, PRODUCT, null, q("5"), q("5"), NOW)),
                NOW.plus(Duration.ofDays(3)), null, ShipmentStatus.IN_TRANSIT, 0);
        ProductSubstitution substitution = new ProductSubstitution(SUBSTITUTION, PRODUCT, SUBSTITUTE,
                new BigDecimal("2"), substitutionStatus, NOW.minus(Duration.ofDays(5)), NOW.plus(Duration.ofDays(30)));
        RecoveryObjective objective = new RecoveryObjective(OBJECTIVE, "Restore order SO-100 fulfillment",
                Set.of(ORDER), order.dueAt(), Set.of(RecoveryConstraint.NO_UNQUALIFIED_SUBSTITUTION,
                RecoveryConstraint.NO_OVER_ALLOCATION), RecoveryObjectiveStatus.ACTIVE, 0);
        RecoveryCandidate candidate = new RecoveryCandidate(CANDIDATE, OBJECTIVE, "Reserve available stock",
                List.of(new RecoveryAction.ReserveInventory(ORDER, LINE, LOT, q("10"))),
                RecoveryCandidateStatus.PROPOSED, NOW, 0);

        return SupplyChainSnapshot.builder(0, NOW)
                .product(product).product(substitute)
                .substitution(substitution)
                .supplier(supplier)
                .location(location)
                .inventoryLot(lot).inventoryLot(substituteLot)
                .customerOrder(order)
                .purchaseOrder(openPo)
                .route(route).carrier(carrier).shipment(shipment)
                .recoveryObjective(objective).recoveryCandidate(candidate)
                .build();
    }
}
