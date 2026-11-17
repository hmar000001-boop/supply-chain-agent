package dev.supplychain.agent.domain.world;

import dev.supplychain.agent.domain.catalog.Product;
import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.catalog.ProductSubstitution;
import dev.supplychain.agent.domain.catalog.SubstitutionId;
import dev.supplychain.agent.domain.catalog.SubstitutionStatus;
import dev.supplychain.agent.domain.common.DomainViolation;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.common.UnitOfMeasure;
import dev.supplychain.agent.domain.inventory.InventoryLocation;
import dev.supplychain.agent.domain.inventory.InventoryLocationId;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.InventoryReservation;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.inventory.ReservationStatus;
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
import java.time.Instant;
import java.time.Duration;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyChainSnapshotInvariantTest {
    private static final Instant NOW = Instant.parse("2026-11-17T13:00:00Z");
    private static final ProductId PRODUCT = new ProductId("P-1");
    private static final InventoryLocationId LOCATION = new InventoryLocationId("WH-1");
    private static final InventoryLotId LOT = new InventoryLotId("LOT-1");
    private static final CustomerOrderId ORDER = new CustomerOrderId("SO-1");
    private static final OrderLineId LINE = new OrderLineId("SO-1-1");

    @Test
    void snapshotRejectsReservationsThatOverAllocatePhysicalStock() {
        var product = new Product(PRODUCT, "P-1", "Part", UnitOfMeasure.EACH, true);
        var location = new InventoryLocation(LOCATION, "WH-1", "Quebec", true);
        var lot = new InventoryLot(LOT, PRODUCT, LOCATION, Quantity.of("10", UnitOfMeasure.EACH),
                LotQualityStatus.AVAILABLE, null, null, null, 0);
        var line = new OrderLine(LINE, PRODUCT, Quantity.of("20", UnitOfMeasure.EACH),
                Quantity.zero(UnitOfMeasure.EACH));
        var order = new CustomerOrder(ORDER, "C-1", OrderPriority.NORMAL, OrderStatus.OPEN,
                NOW.plusSeconds(3600), Map.of(LINE, line), 0);
        var reservation = new InventoryReservation(new ReservationId("RES-1"), ORDER, LINE, LOT, PRODUCT,
                null, Quantity.of("11", UnitOfMeasure.EACH), Quantity.of("11", UnitOfMeasure.EACH),
                ReservationStatus.ACTIVE, NOW);

        assertThrows(DomainViolation.class, () -> SupplyChainSnapshot.builder(0, NOW)
                .product(product).location(location).inventoryLot(lot).customerOrder(order)
                .reservation(reservation).build());
    }

    @Test
    void shipmentCanUseApprovedSubstituteWhileCountingRequiredProductQuantity() {
        var substituteId = new ProductId("P-SUB");
        var substitutionId = new SubstitutionId("SUB-SHIP");
        var product = new Product(PRODUCT, "P-1", "Part", UnitOfMeasure.EACH, true);
        var substitute = new Product(substituteId, "P-SUB", "Approved replacement", UnitOfMeasure.EACH, true);
        var location = new InventoryLocation(LOCATION, "WH-1", "Quebec", true);
        var lot = new InventoryLot(LOT, substituteId, LOCATION, Quantity.of("100", UnitOfMeasure.EACH),
                LotQualityStatus.AVAILABLE, null, null, null, 0);
        var line = new OrderLine(LINE, PRODUCT, Quantity.of("20", UnitOfMeasure.EACH),
                Quantity.zero(UnitOfMeasure.EACH));
        var order = new CustomerOrder(ORDER, "C-1", OrderPriority.NORMAL, OrderStatus.OPEN,
                NOW.plusSeconds(3600), Map.of(LINE, line), 0);
        var routeId = new RouteId("R-SUB");
        var carrierId = new CarrierId("C-SUB");
        var route = new Route(routeId, "A", "B", Duration.ofDays(1), true);
        var carrier = new Carrier(carrierId, "Carrier", Set.of(routeId), true);
        var rule = new ProductSubstitution(substitutionId, PRODUCT, substituteId,
                new java.math.BigDecimal("2"), SubstitutionStatus.APPROVED,
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        var shipment = new Shipment(new ShipmentId("SH-SUB"), carrierId, routeId,
                java.util.List.of(new ShipmentLine(ORDER, LINE, substituteId, substitutionId,
                        Quantity.of("10", UnitOfMeasure.EACH), Quantity.of("5", UnitOfMeasure.EACH), NOW)),
                NOW.plusSeconds(7200), null, ShipmentStatus.IN_TRANSIT, 0);
        var snapshot = SupplyChainSnapshot.builder(0, NOW)
                .product(product).product(substitute).substitution(rule).location(location).inventoryLot(lot)
                .customerOrder(order).route(route).carrier(carrier).shipment(shipment).build();

        assertEquals(Quantity.of("5", UnitOfMeasure.EACH),
                snapshot.committedShipmentFulfillmentQuantity(ORDER, LINE));
    }

    @Test
    void snapshotCountsInFlightShipmentQuantityAgainstOrderDemand() {
        var product = new Product(PRODUCT, "P-1", "Part", UnitOfMeasure.EACH, true);
        var location = new InventoryLocation(LOCATION, "WH-1", "Quebec", true);
        var lot = new InventoryLot(LOT, PRODUCT, LOCATION, Quantity.of("100", UnitOfMeasure.EACH),
                LotQualityStatus.AVAILABLE, null, null, null, 0);
        var line = new OrderLine(LINE, PRODUCT, Quantity.of("20", UnitOfMeasure.EACH),
                Quantity.zero(UnitOfMeasure.EACH));
        var order = new CustomerOrder(ORDER, "C-1", OrderPriority.NORMAL, OrderStatus.OPEN,
                NOW.plusSeconds(3600), Map.of(LINE, line), 0);
        var routeId = new RouteId("R-1");
        var carrierId = new CarrierId("C-1");
        var route = new Route(routeId, "A", "B", Duration.ofDays(1), true);
        var carrier = new Carrier(carrierId, "Carrier", Set.of(routeId), true);
        var shipment = new Shipment(new ShipmentId("SH-1"), carrierId, routeId,
                java.util.List.of(new ShipmentLine(ORDER, LINE, PRODUCT, null, Quantity.of("21", UnitOfMeasure.EACH),
                        Quantity.of("21", UnitOfMeasure.EACH), NOW)),
                NOW.plusSeconds(7200), null, ShipmentStatus.IN_TRANSIT, 0);

        assertThrows(DomainViolation.class, () -> SupplyChainSnapshot.builder(0, NOW)
                .product(product).location(location).inventoryLot(lot).customerOrder(order)
                .route(route).carrier(carrier).shipment(shipment).build());
    }

    @Test
    void deliveredShipmentQuantityMustBeReflectedInFulfilledQuantity() {
        var product = new Product(PRODUCT, "P-1", "Part", UnitOfMeasure.EACH, true);
        var location = new InventoryLocation(LOCATION, "WH-1", "Quebec", true);
        var lot = new InventoryLot(LOT, PRODUCT, LOCATION, Quantity.of("100", UnitOfMeasure.EACH),
                LotQualityStatus.AVAILABLE, null, null, null, 0);
        var line = new OrderLine(LINE, PRODUCT, Quantity.of("20", UnitOfMeasure.EACH),
                Quantity.of("5", UnitOfMeasure.EACH));
        var order = new CustomerOrder(ORDER, "C-1", OrderPriority.NORMAL, OrderStatus.IN_PROGRESS,
                NOW.plusSeconds(3600), Map.of(LINE, line), 0);
        var routeId = new RouteId("R-DELIVERED");
        var carrierId = new CarrierId("C-DELIVERED");
        var route = new Route(routeId, "A", "B", Duration.ofDays(1), true);
        var carrier = new Carrier(carrierId, "Carrier", Set.of(routeId), true);
        var shipment = new Shipment(new ShipmentId("SH-DELIVERED"), carrierId, routeId,
                java.util.List.of(new ShipmentLine(ORDER, LINE, PRODUCT, null, Quantity.of("6", UnitOfMeasure.EACH),
                        Quantity.of("6", UnitOfMeasure.EACH), NOW)),
                NOW.plusSeconds(7200), NOW, ShipmentStatus.DELIVERED, 0);

        assertThrows(DomainViolation.class, () -> SupplyChainSnapshot.builder(0, NOW)
                .product(product).location(location).inventoryLot(lot).customerOrder(order)
                .route(route).carrier(carrier).shipment(shipment).build());
    }

    @Test
    void snapshotRejectsOverlappingApprovedSubstitutionRulesForSamePair() {
        var required = new Product(PRODUCT, "P-1", "Part", UnitOfMeasure.EACH, true);
        var substitute = new Product(new ProductId("P-2"), "P-2", "Substitute", UnitOfMeasure.EACH, true);
        var first = new ProductSubstitution(new SubstitutionId("SUB-1"), PRODUCT, substitute.id(),
                new java.math.BigDecimal("1"), SubstitutionStatus.APPROVED,
                NOW, NOW.plusSeconds(3600));
        var second = new ProductSubstitution(new SubstitutionId("SUB-2"), PRODUCT, substitute.id(),
                new java.math.BigDecimal("2"), SubstitutionStatus.APPROVED,
                NOW.plusSeconds(1800), NOW.plusSeconds(7200));
        assertThrows(DomainViolation.class, () -> SupplyChainSnapshot.builder(0, NOW)
                .product(required).product(substitute).substitution(first).substitution(second).build());
    }

    @Test
    void snapshotRejectsDuplicateSkuEvenWhenProductIdsDiffer() {
        var first = new Product(new ProductId("P-1"), "same-sku", "First", UnitOfMeasure.EACH, true);
        var second = new Product(new ProductId("P-2"), "SAME-SKU", "Second", UnitOfMeasure.EACH, true);
        assertThrows(DomainViolation.class,
                () -> SupplyChainSnapshot.builder(0, NOW).product(first).product(second).build());
    }
}
