package dev.supplychain.agent.simulator;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.DomainViolation;
import dev.supplychain.agent.domain.disruption.Disruption;
import dev.supplychain.agent.domain.disruption.DisruptionId;
import dev.supplychain.agent.domain.disruption.DisruptionSeverity;
import dev.supplychain.agent.domain.disruption.DisruptionStatus;
import dev.supplychain.agent.domain.disruption.DisruptionType;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.procurement.PurchaseOrder;
import dev.supplychain.agent.domain.procurement.PurchaseOrderId;
import dev.supplychain.agent.domain.procurement.PurchaseOrderLine;
import dev.supplychain.agent.domain.procurement.PurchaseOrderStatus;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.orders.OrderStatus;
import dev.supplychain.agent.domain.supplier.SupplierProductCapability;
import dev.supplychain.agent.domain.supplier.SupplierStatus;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import dev.supplychain.agent.simulator.effect.SimulationEffectType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyChainSimulatorTest {
    private final SupplyChainSimulator simulator = new SupplyChainSimulator();

    @Test
    void supplierCapacityReductionFlagsExistingOpenCommitmentsAtRisk() {
        var result = simulator.simulate(ScenarioFixtures.world(),
                new SimulationCommand.ReduceSupplierCapacity("CMD-1", ScenarioFixtures.SUPPLIER,
                        ScenarioFixtures.PRODUCT, ScenarioFixtures.q("20"), ScenarioFixtures.NOW.plusSeconds(60)));

        assertEquals(1, result.newVersion());
        assertEquals(ScenarioFixtures.q("20"), result.snapshot().supplier(ScenarioFixtures.SUPPLIER)
                .capability(ScenarioFixtures.PRODUCT).orElseThrow().capacity());
        assertEquals(1, result.snapshot().supplier(ScenarioFixtures.SUPPLIER).version());
        assertTrue(result.effects().stream().anyMatch(e -> e.type() == SimulationEffectType.OPEN_PURCHASE_ORDERS_AT_RISK));
        assertEquals(0, ScenarioFixtures.world().version(), "The original snapshot must remain immutable");
    }

    @Test
    void capacityCommandRejectsAnIncrease() {
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(ScenarioFixtures.world(),
                new SimulationCommand.ReduceSupplierCapacity("CMD-2", ScenarioFixtures.SUPPLIER,
                        ScenarioFixtures.PRODUCT, ScenarioFixtures.q("110"), ScenarioFixtures.NOW)));
        assertEquals(DomainViolation.Code.INVALID_TRANSITION, exception.code());
    }

    @Test
    void purchaseOrderPlacementChecksQualificationLeadTimeAndCapacity() {
        var draft = new PurchaseOrder(new PurchaseOrderId("PO-NEW"), ScenarioFixtures.SUPPLIER,
                ScenarioFixtures.NOW, ScenarioFixtures.NOW.plus(Duration.ofDays(2)), PurchaseOrderStatus.DRAFT,
                List.of(new PurchaseOrderLine(ScenarioFixtures.PRODUCT, ScenarioFixtures.q("60"), ScenarioFixtures.q("0"), Money.of("12.50", "CAD"))), 0);
        var result = simulator.simulate(ScenarioFixtures.world(),
                new SimulationCommand.PlacePurchaseOrder("CMD-3", draft, ScenarioFixtures.NOW));
        assertEquals(PurchaseOrderStatus.PLACED, result.snapshot().purchaseOrder(draft.id()).status());
        assertEquals(SimulationEffectType.PURCHASE_ORDER_PLACED, result.effects().getFirst().type());
    }

    @Test
    void purchaseOrderPlacementRejectsCapacityExceeded() {
        var draft = new PurchaseOrder(new PurchaseOrderId("PO-TOO-LARGE"), ScenarioFixtures.SUPPLIER,
                ScenarioFixtures.NOW, ScenarioFixtures.NOW.plus(Duration.ofDays(2)), PurchaseOrderStatus.DRAFT,
                List.of(new PurchaseOrderLine(ScenarioFixtures.PRODUCT, ScenarioFixtures.q("80"), ScenarioFixtures.q("0"), Money.of("12.50", "CAD"))), 0);
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(ScenarioFixtures.world(),
                new SimulationCommand.PlacePurchaseOrder("CMD-4", draft, ScenarioFixtures.NOW)));
        assertEquals(DomainViolation.Code.CAPACITY_EXCEEDED, exception.code());
    }

    @Test
    void exactProductReservationReducesAvailableInventoryAndIsImmutable() {
        var before = ScenarioFixtures.world();
        var result = simulator.simulate(before, new SimulationCommand.ReserveInventory("CMD-5",
                new ReservationId("RES-1"), ScenarioFixtures.ORDER, ScenarioFixtures.LINE,
                ScenarioFixtures.LOT, ScenarioFixtures.q("10"), ScenarioFixtures.NOW.plusSeconds(30)));
        assertEquals(ScenarioFixtures.q("40"), result.snapshot().availableInventory(ScenarioFixtures.LOT, ScenarioFixtures.NOW.plusSeconds(30)));
        assertEquals(ScenarioFixtures.q("50"), before.availableInventory(ScenarioFixtures.LOT, ScenarioFixtures.NOW.plusSeconds(30)));
    }

    @Test
    void approvedSubstitutionUsesItsDeclaredPhysicalQuantityRatio() {
        var result = simulator.simulate(ScenarioFixtures.world(), new SimulationCommand.ReserveInventory("CMD-6",
                new ReservationId("RES-SUB"), ScenarioFixtures.ORDER, ScenarioFixtures.LINE,
                ScenarioFixtures.SUBSTITUTE_LOT, ScenarioFixtures.q("3"), ScenarioFixtures.NOW.plusSeconds(30)));
        var reservation = result.snapshot().reservation(new ReservationId("RES-SUB"));
        assertEquals(ScenarioFixtures.q("6"), reservation.reservedQuantity());
        assertEquals(ScenarioFixtures.q("3"), reservation.fulfillmentQuantity());
        assertEquals(ScenarioFixtures.SUBSTITUTION, reservation.substitutionId());
    }

    @Test
    void expiredInventoryLotCannotBeReserved() {
        var initial = ScenarioFixtures.world();
        var lot = initial.inventoryLot(ScenarioFixtures.LOT);
        var expired = new dev.supplychain.agent.domain.inventory.InventoryLot(lot.id(), lot.productId(),
                lot.locationId(), lot.quantityOnHand(), lot.qualityStatus(), ScenarioFixtures.NOW.minusSeconds(1),
                null, null, lot.version() + 1);
        var expiredWorld = initial.toBuilder().replaceInventoryLot(expired).build();
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(expiredWorld,
                new SimulationCommand.ReserveInventory("CMD-EXPIRED", new ReservationId("RES-EXPIRED"),
                        ScenarioFixtures.ORDER, ScenarioFixtures.LINE, ScenarioFixtures.LOT,
                        ScenarioFixtures.q("1"), ScenarioFixtures.NOW)));
        assertEquals(DomainViolation.Code.INVALID_STATE, exception.code());
    }

    @Test
    void pendingSubstitutionCannotBeUsedForReservation() {
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(
                ScenarioFixtures.world(dev.supplychain.agent.domain.catalog.SubstitutionStatus.PENDING),
                new SimulationCommand.ReserveInventory("CMD-7", new ReservationId("RES-NO"),
                        ScenarioFixtures.ORDER, ScenarioFixtures.LINE, ScenarioFixtures.SUBSTITUTE_LOT,
                        ScenarioFixtures.q("1"), ScenarioFixtures.NOW)));
        assertEquals(DomainViolation.Code.UNQUALIFIED_SUBSTITUTE, exception.code());
    }

    @Test
    void inFlightShipmentQuantityConsumesOrderDemandBeforeNewReservation() {
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(ScenarioFixtures.world(),
                new SimulationCommand.ReserveInventory("CMD-SHIP-COMMIT", new ReservationId("RES-SHIP-COMMIT"),
                        ScenarioFixtures.ORDER, ScenarioFixtures.LINE, ScenarioFixtures.LOT,
                        ScenarioFixtures.q("36"), ScenarioFixtures.NOW)));
        assertEquals(DomainViolation.Code.ORDER_QUANTITY_EXCEEDED, exception.code());
        assertEquals(ScenarioFixtures.q("5"), ScenarioFixtures.world()
                .committedShipmentFulfillmentQuantity(ScenarioFixtures.ORDER, ScenarioFixtures.LINE));
    }

    @Test
    void reservationsCannotExceedRemainingOrderDemand() {
        var first = simulator.simulate(ScenarioFixtures.world(), new SimulationCommand.ReserveInventory("CMD-8A",
                new ReservationId("RES-A"), ScenarioFixtures.ORDER, ScenarioFixtures.LINE,
                ScenarioFixtures.LOT, ScenarioFixtures.q("25"), ScenarioFixtures.NOW));
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(first.snapshot(),
                new SimulationCommand.ReserveInventory("CMD-8B", new ReservationId("RES-B"),
                        ScenarioFixtures.ORDER, ScenarioFixtures.LINE, ScenarioFixtures.LOT,
                        ScenarioFixtures.q("20"), ScenarioFixtures.NOW.plusSeconds(1))));
        assertEquals(DomainViolation.Code.ORDER_QUANTITY_EXCEEDED, exception.code());
    }

    @Test
    void qualityHoldBlocksNewReservationsAndFlagsExistingReservations() {
        var reserved = simulator.simulate(ScenarioFixtures.world(), new SimulationCommand.ReserveInventory("CMD-9A",
                new ReservationId("RES-Q"), ScenarioFixtures.ORDER, ScenarioFixtures.LINE,
                ScenarioFixtures.LOT, ScenarioFixtures.q("5"), ScenarioFixtures.NOW));
        var held = simulator.simulate(reserved.snapshot(), new SimulationCommand.ApplyQualityHold("CMD-9B",
                ScenarioFixtures.LOT, "Inspection failure", ScenarioFixtures.NOW.plusSeconds(1)));
        assertEquals(LotQualityStatus.QUALITY_HOLD, held.snapshot().inventoryLot(ScenarioFixtures.LOT).qualityStatus());
        assertTrue(held.snapshot().availableInventory(ScenarioFixtures.LOT, ScenarioFixtures.NOW.plusSeconds(2)).isZero());
        assertTrue(held.effects().stream().anyMatch(e -> e.type() == SimulationEffectType.ACTIVE_RESERVATIONS_AT_RISK));
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(held.snapshot(),
                new SimulationCommand.ReserveInventory("CMD-9C", new ReservationId("RES-BLOCKED"),
                        ScenarioFixtures.ORDER, ScenarioFixtures.LINE, ScenarioFixtures.LOT,
                        ScenarioFixtures.q("1"), ScenarioFixtures.NOW.plusSeconds(2))));
        assertEquals(DomainViolation.Code.INVALID_STATE, exception.code());
    }

    @Test
    void shipmentDelayUpdatesEtaAndIdentifiesAffectedOrders() {
        var revisedEta = ScenarioFixtures.NOW.plus(Duration.ofDays(5));
        var result = simulator.simulate(ScenarioFixtures.world(), new SimulationCommand.DelayShipment("CMD-10",
                ScenarioFixtures.SHIPMENT, revisedEta, "Weather disruption", ScenarioFixtures.NOW.plusSeconds(1)));
        var shipment = result.snapshot().shipment(ScenarioFixtures.SHIPMENT);
        assertEquals(revisedEta, shipment.expectedArrivalAt());
        assertEquals(dev.supplychain.agent.domain.logistics.ShipmentStatus.DELAYED, shipment.status());
        assertTrue(result.effects().getFirst().affectedEntityReferences().contains(ScenarioFixtures.ORDER.toString()));
    }

    @Test
    void plannedShipmentCanBeReroutedToActiveCarrierSupportedRoute() {
        var initial = ScenarioFixtures.world();
        var inTransit = initial.shipment(ScenarioFixtures.SHIPMENT);
        var planned = new dev.supplychain.agent.domain.logistics.Shipment(inTransit.id(), inTransit.carrierId(),
                inTransit.routeId(), inTransit.lines(), inTransit.expectedArrivalAt(), null,
                dev.supplychain.agent.domain.logistics.ShipmentStatus.PLANNED, inTransit.version());
        var plannedWorld = initial.toBuilder().replaceShipment(planned).build();
        var revisedEta = ScenarioFixtures.NOW.plus(Duration.ofDays(5));

        var result = simulator.simulate(plannedWorld, new SimulationCommand.RerouteShipment("CMD-REROUTE-1",
                ScenarioFixtures.SHIPMENT, ScenarioFixtures.ALTERNATIVE_ROUTE, revisedEta,
                "Approved alternate route", ScenarioFixtures.NOW.plusSeconds(1)));

        var changed = result.snapshot().shipment(ScenarioFixtures.SHIPMENT);
        assertEquals(ScenarioFixtures.ALTERNATIVE_ROUTE, changed.routeId());
        assertEquals(revisedEta, changed.expectedArrivalAt());
        assertEquals(dev.supplychain.agent.domain.logistics.ShipmentStatus.PLANNED, changed.status());
        assertEquals(1, changed.version());
        assertTrue(result.effects().stream().anyMatch(effect -> effect.type() == SimulationEffectType.SHIPMENT_REROUTED));
        assertTrue(result.effects().getFirst().affectedEntityReferences().contains(ScenarioFixtures.ORDER.toString()));
    }

    @Test
    void shipmentInTransitCannotBeRerouted() {
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(ScenarioFixtures.world(),
                new SimulationCommand.RerouteShipment("CMD-REROUTE-2", ScenarioFixtures.SHIPMENT,
                        ScenarioFixtures.ALTERNATIVE_ROUTE, ScenarioFixtures.NOW.plus(Duration.ofDays(5)),
                        "Too late to reroute", ScenarioFixtures.NOW.plusSeconds(1))));
        assertEquals(DomainViolation.Code.INVALID_STATE, exception.code());
    }

    @Test
    void rerouteRejectsArrivalEarlierThanTargetRouteTransitTime() {
        var initial = ScenarioFixtures.world();
        var inTransit = initial.shipment(ScenarioFixtures.SHIPMENT);
        var planned = new dev.supplychain.agent.domain.logistics.Shipment(inTransit.id(), inTransit.carrierId(),
                inTransit.routeId(), inTransit.lines(), inTransit.expectedArrivalAt(), null,
                dev.supplychain.agent.domain.logistics.ShipmentStatus.PLANNED, inTransit.version());
        var plannedWorld = initial.toBuilder().replaceShipment(planned).build();
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(plannedWorld,
                new SimulationCommand.RerouteShipment("CMD-REROUTE-3", ScenarioFixtures.SHIPMENT,
                        ScenarioFixtures.ALTERNATIVE_ROUTE, ScenarioFixtures.NOW.plusSeconds(2),
                        "Impossible ETA", ScenarioFixtures.NOW.plusSeconds(1))));
        assertEquals(DomainViolation.Code.INVALID_DATE, exception.code());
    }

    @Test
    void releasingReservationRestoresAvailability() {
        var reserved = simulator.simulate(ScenarioFixtures.world(), new SimulationCommand.ReserveInventory("CMD-11A",
                new ReservationId("RES-REL"), ScenarioFixtures.ORDER, ScenarioFixtures.LINE,
                ScenarioFixtures.LOT, ScenarioFixtures.q("12"), ScenarioFixtures.NOW));
        var released = simulator.simulate(reserved.snapshot(), new SimulationCommand.ReleaseReservation("CMD-11B",
                new ReservationId("RES-REL"), ScenarioFixtures.NOW.plusSeconds(1)));
        assertEquals(ScenarioFixtures.q("50"), released.snapshot().availableInventory(ScenarioFixtures.LOT, ScenarioFixtures.NOW.plusSeconds(2)));
        assertEquals(dev.supplychain.agent.domain.inventory.ReservationStatus.RELEASED,
                released.snapshot().reservation(new ReservationId("RES-REL")).status());
    }

    @Test
    void demandCannotBeReducedBelowFulfilledPlusReservedQuantity() {
        var reserved = simulator.simulate(ScenarioFixtures.world(), new SimulationCommand.ReserveInventory("CMD-12A",
                new ReservationId("RES-DEM"), ScenarioFixtures.ORDER, ScenarioFixtures.LINE,
                ScenarioFixtures.LOT, ScenarioFixtures.q("10"), ScenarioFixtures.NOW));
        var exception = assertThrows(DomainViolation.class, () -> simulator.simulate(reserved.snapshot(),
                new SimulationCommand.ChangeOrderDemand("CMD-12B", ScenarioFixtures.ORDER, ScenarioFixtures.LINE,
                        ScenarioFixtures.q("5"), ScenarioFixtures.NOW.plusSeconds(1))));
        assertEquals(DomainViolation.Code.ORDER_QUANTITY_EXCEEDED, exception.code());
    }

    @Test
    void demandCanIncreaseAndPreservesOrderIdentity() {
        var changed = simulator.simulate(ScenarioFixtures.world(), new SimulationCommand.ChangeOrderDemand("CMD-13",
                ScenarioFixtures.ORDER, ScenarioFixtures.LINE, ScenarioFixtures.q("50"), ScenarioFixtures.NOW));
        assertEquals(ScenarioFixtures.q("50"), changed.snapshot().customerOrder(ScenarioFixtures.ORDER)
                .lines().get(ScenarioFixtures.LINE).requestedQuantity());
        assertEquals(1, changed.snapshot().customerOrder(ScenarioFixtures.ORDER).version());
    }

    @Test
    void disruptionObservationIsRecordedWithoutExternalSideEffects() {
        var disruption = new Disruption(new DisruptionId("DIS-1"), DisruptionType.SUPPLIER_OUTAGE,
                DisruptionSeverity.CRITICAL, DisruptionStatus.OPEN, "Supplier site stopped production",
                ScenarioFixtures.NOW, Set.of(ScenarioFixtures.SUPPLIER.toString()));
        var result = simulator.simulate(ScenarioFixtures.world(),
                new SimulationCommand.RecordDisruption("CMD-14", disruption, ScenarioFixtures.NOW));
        assertEquals(disruption, result.snapshot().disruptions().get(disruption.id()));
        assertEquals(SimulationEffectType.DISRUPTION_RECORDED, result.effects().getFirst().type());
    }

    @Test
    void simulationIsDeterministicForSameSnapshotAndCommand() {
        var initial = ScenarioFixtures.world();
        var command = new SimulationCommand.ReserveInventory("CMD-15", new ReservationId("RES-D"),
                ScenarioFixtures.ORDER, ScenarioFixtures.LINE, ScenarioFixtures.LOT,
                ScenarioFixtures.q("7"), ScenarioFixtures.NOW);
        var first = simulator.simulate(initial, command);
        var second = simulator.simulate(initial, command);
        assertEquals(first.snapshot().version(), second.snapshot().version());
        assertEquals(first.snapshot().reservations(), second.snapshot().reservations());
        assertEquals(first.effects(), second.effects());
    }
}
