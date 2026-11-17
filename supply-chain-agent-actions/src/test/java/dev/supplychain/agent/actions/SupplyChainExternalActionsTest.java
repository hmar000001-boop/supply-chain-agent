package dev.supplychain.agent.actions;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.actions.external.grounding.*;
import dev.supplychain.agent.actions.external.perception.*;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.logistics.Carrier;
import dev.supplychain.agent.domain.logistics.CarrierId;
import dev.supplychain.agent.domain.logistics.Route;
import dev.supplychain.agent.domain.logistics.RouteId;
import dev.supplychain.agent.domain.logistics.Shipment;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import dev.supplychain.agent.domain.logistics.ShipmentLine;
import dev.supplychain.agent.domain.logistics.ShipmentStatus;
import dev.supplychain.agent.domain.procurement.PurchaseOrder;
import dev.supplychain.agent.domain.procurement.PurchaseOrderId;
import dev.supplychain.agent.domain.procurement.PurchaseOrderLine;
import dev.supplychain.agent.domain.procurement.PurchaseOrderStatus;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.supplier.Supplier;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyChainExternalActionsTest {
    private static final RouteId ROUTE_ORIGINAL = new RouteId("ROUTE-ACT-ORIGINAL");
    private static final RouteId ROUTE_ALTERNATIVE = new RouteId("ROUTE-ACT-ALTERNATIVE");
    private static final CarrierId CARRIER = new CarrierId("CARRIER-ACT-1");
    private static final ShipmentId SHIPMENT = new ShipmentId("SHIP-ACT-1");

    @Test
    void perceptionReturnsImmutableSnapshotObservationWithProvenance() {
        var snapshot = ActionTestFixtures.world();
        var port = new SnapshotSupplyChainPerceptionAdapter(() -> snapshot);
        var action = new SupplyChainPerceptionActions.ReadSupplier(port);
        var registry = new ActionRegistry().register(action);
        var dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var grant = CapabilityGrant.scoped(Capability.SUPPLIER_READ,
                Set.of(new ResourceReference("supplier", ActionTestFixtures.SUPPLIER.toString())));
        var context = context(Set.of(grant), null);
        var request = new ReadSupplierRequest(ActionTestFixtures.SUPPLIER);

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), observationSupplierType(), context, request);

        assertEquals(ActionStatus.SUCCEEDED, result.status());
        assertEquals(ActionTestFixtures.SUPPLIER, result.value().value().id());
        assertEquals(snapshot.version(), result.value().sourceVersion());
        assertEquals(ProvenanceSource.SIMULATOR, result.value().provenance().source());
        assertEquals("snapshot-v0:supplier:SUP-ACT-1", result.value().evidence().evidenceId());
    }

    @Test
    void perceptionScopeDenialOccursBeforeReading() {
        var snapshot = ActionTestFixtures.world();
        var port = new SnapshotSupplyChainPerceptionAdapter(() -> snapshot);
        var action = new SupplyChainPerceptionActions.ReadSupplier(port);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action), new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = context(Set.of(CapabilityGrant.scoped(Capability.SUPPLIER_READ,
                Set.of(new ResourceReference("supplier", "SUP-OTHER")))), null);

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), observationSupplierType(), context,
                new ReadSupplierRequest(ActionTestFixtures.SUPPLIER));

        assertEquals(ActionStatus.REJECTED, result.status());
        assertEquals(ActionFailureCode.AUTHORIZATION_DENIED, result.failure().code());
    }

    @Test
    void perceptionRejectsAReadTimeOlderThanCurrentSnapshot() {
        var snapshot = ActionTestFixtures.world();
        var port = new SnapshotSupplyChainPerceptionAdapter(() -> snapshot);
        var action = new SupplyChainPerceptionActions.ReadSupplier(port);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action), new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = ActionContext.create("exec-1", "corr-1",
                new ActionPrincipal("reader", Set.of(CapabilityGrant.all(Capability.SUPPLIER_READ))),
                ActionTestFixtures.NOW.minusSeconds(1), null);

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), observationSupplierType(), context,
                new ReadSupplierRequest(ActionTestFixtures.SUPPLIER));

        assertEquals(ActionStatus.FAILED, result.status());
        assertEquals(ActionFailureCode.INVALID_INPUT, result.failure().code());
    }

    @Test
    void reservationMutatesOnlyTheSimulationWorldAndDuplicateDoesNotApplyAgain() {
        var world = new InMemorySupplyChainSimulationWorld(ActionTestFixtures.world(), new SupplyChainSimulator());
        var action = new SupplyChainGroundingActions.ReserveInventory(world);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action), new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var grants = Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE, Set.of(
                new ResourceReference("customer-order", ActionTestFixtures.ORDER.toString()),
                new ResourceReference("inventory-lot", ActionTestFixtures.LOT.toString()))));
        var context = context(grants, "reserve-operation-1");
        var request = new ReserveInventoryRequest(0, new ReservationId("RES-ACT-1"), ActionTestFixtures.ORDER,
                ActionTestFixtures.LINE, ActionTestFixtures.LOT, ActionTestFixtures.q("10"));

        var first = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);
        var duplicate = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals(1, first.value().newVersion());
        assertEquals(Quantity.of("40", dev.supplychain.agent.domain.common.UnitOfMeasure.EACH),
                world.currentSnapshot().availableInventory(ActionTestFixtures.LOT, ActionTestFixtures.NOW.plusSeconds(1)));
        assertEquals(ActionStatus.ALREADY_COMPLETED, duplicate.status());
        assertEquals(1, world.currentSnapshot().version());
    }

    @Test
    void staleWorldVersionIsRejectedBeforeClaimAndSameKeyCanBeUsedAfterRefresh() {
        var world = new InMemorySupplyChainSimulationWorld(ActionTestFixtures.world(), new SupplyChainSimulator());
        // Advance the world directly to simulate a concurrent observation/update before this action arrives.
        world.apply(0, new dev.supplychain.agent.simulator.command.SimulationCommand.RecordDisruption(
                "SEED-DISRUPTION", new dev.supplychain.agent.domain.disruption.Disruption(
                new dev.supplychain.agent.domain.disruption.DisruptionId("DIS-SEED"),
                dev.supplychain.agent.domain.disruption.DisruptionType.SUPPLIER_OUTAGE,
                dev.supplychain.agent.domain.disruption.DisruptionSeverity.HIGH,
                dev.supplychain.agent.domain.disruption.DisruptionStatus.OPEN,
                "Test disruption", ActionTestFixtures.NOW, Set.of(ActionTestFixtures.SUPPLIER.toString())), ActionTestFixtures.NOW));
        var action = new SupplyChainGroundingActions.ReserveInventory(world);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action), new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = context(Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE, Set.of(
                new ResourceReference("customer-order", ActionTestFixtures.ORDER.toString()),
                new ResourceReference("inventory-lot", ActionTestFixtures.LOT.toString())))), "refreshed-key");
        var stale = new ReserveInventoryRequest(0, new ReservationId("RES-ACT-2"), ActionTestFixtures.ORDER,
                ActionTestFixtures.LINE, ActionTestFixtures.LOT, ActionTestFixtures.q("5"));

        var rejected = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, stale);
        var refreshed = new ReserveInventoryRequest(1, new ReservationId("RES-ACT-2"), ActionTestFixtures.ORDER,
                ActionTestFixtures.LINE, ActionTestFixtures.LOT, ActionTestFixtures.q("5"));
        var accepted = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, refreshed);

        assertEquals(ActionStatus.REJECTED, rejected.status());
        assertEquals(ActionFailureCode.PRECONDITION_FAILED, rejected.failure().code());
        assertEquals(ActionStatus.SUCCEEDED, accepted.status());
        assertEquals(2, world.currentSnapshot().version());
    }

    @Test
    void qualityDispositionIsReadOnlyAndCarriesLotAndSnapshotProvenance() {
        var snapshot = ActionTestFixtures.world();
        var port = new SnapshotSupplyChainPerceptionAdapter(() -> snapshot);
        var action = new SupplyChainPerceptionActions.ReadQualityDisposition(port);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = context(Set.of(CapabilityGrant.scoped(Capability.QUALITY_READ,
                Set.of(new ResourceReference("inventory-lot", ActionTestFixtures.LOT.toString())))), null);

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), qualityObservationType(), context,
                new ReadQualityStatusRequest(ActionTestFixtures.LOT));

        assertEquals(ActionStatus.SUCCEEDED, result.status());
        assertEquals(ActionTestFixtures.LOT, result.value().value().lotId());
        assertEquals(dev.supplychain.agent.domain.inventory.LotQualityStatus.AVAILABLE,
                result.value().value().status());
        assertNull(result.value().value().heldAt());
        assertNull(result.value().value().holdReason());
        assertEquals(0, result.value().value().lotVersion());
        assertEquals(0, result.value().sourceVersion());
        assertEquals(ProvenanceSource.SIMULATOR, result.value().provenance().source());
        assertEquals("snapshot-v0:inventory-lot:LOT-ACT-1", result.value().evidence().evidenceId());
    }

    @Test
    void qualityDispositionIncludesVerifiedSnapshotHoldMetadata() {
        var world = new InMemorySupplyChainSimulationWorld(ActionTestFixtures.world(), new SupplyChainSimulator());
        world.apply(0, new dev.supplychain.agent.simulator.command.SimulationCommand.ApplyQualityHold(
                "quality-observation-seed", ActionTestFixtures.LOT, "Inspection failure",
                ActionTestFixtures.NOW.plusSeconds(1)));
        var action = new SupplyChainPerceptionActions.ReadQualityDisposition(
                new SnapshotSupplyChainPerceptionAdapter(world::currentSnapshot));
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = ActionContext.create("quality-exec", "quality-corr",
                new ActionPrincipal("quality-reader", Set.of(CapabilityGrant.scoped(Capability.QUALITY_READ,
                        Set.of(new ResourceReference("inventory-lot", ActionTestFixtures.LOT.toString()))))),
                ActionTestFixtures.NOW.plusSeconds(2), null);

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), qualityObservationType(),
                context, new ReadQualityStatusRequest(ActionTestFixtures.LOT));

        assertEquals(ActionStatus.SUCCEEDED, result.status());
        assertEquals(dev.supplychain.agent.domain.inventory.LotQualityStatus.QUALITY_HOLD,
                result.value().value().status());
        assertEquals(ActionTestFixtures.NOW.plusSeconds(1), result.value().value().heldAt());
        assertEquals("Inspection failure", result.value().value().holdReason());
        assertEquals(1, result.value().value().lotVersion());
        assertEquals(1, result.value().sourceVersion());
    }

    @Test
    void releaseReservationUsesScopedCapabilityAndIsIdempotent() {
        var simulator = new SupplyChainSimulator();
        var seeded = simulator.simulate(ActionTestFixtures.world(),
                new dev.supplychain.agent.simulator.command.SimulationCommand.ReserveInventory(
                        "seed-reservation", new ReservationId("RES-RELEASE-1"), ActionTestFixtures.ORDER,
                        ActionTestFixtures.LINE, ActionTestFixtures.LOT, ActionTestFixtures.q("4"),
                        ActionTestFixtures.NOW));
        var world = new InMemorySupplyChainSimulationWorld(seeded.snapshot(), simulator);
        var action = new SupplyChainGroundingActions.ReleaseReservation(world);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = context(Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RELEASE,
                Set.of(new ResourceReference("reservation", "RES-RELEASE-1")))), "release-operation-1");
        var request = new ReleaseReservationRequest(1, new ReservationId("RES-RELEASE-1"));

        var first = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);
        var duplicate = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals(2, first.value().newVersion());
        assertEquals(dev.supplychain.agent.domain.inventory.ReservationStatus.RELEASED,
                first.value().snapshot().reservation(new ReservationId("RES-RELEASE-1")).status());
        assertEquals(ActionStatus.ALREADY_COMPLETED, duplicate.status());
        assertEquals(2, world.currentSnapshot().version());
    }

    @Test
    void purchaseOrderPlacementRequiresSupplierAndProductScopeAndIsIdempotent() {
        var world = new InMemorySupplyChainSimulationWorld(ActionTestFixtures.world(), new SupplyChainSimulator());
        var action = new SupplyChainGroundingActions.PlacePurchaseOrder(world);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var purchaseOrder = new PurchaseOrder(new PurchaseOrderId("PO-ACT-1"), ActionTestFixtures.SUPPLIER,
                ActionTestFixtures.NOW, ActionTestFixtures.NOW.plus(Duration.ofDays(4)),
                PurchaseOrderStatus.DRAFT,
                java.util.List.of(new PurchaseOrderLine(ActionTestFixtures.PRODUCT, ActionTestFixtures.q("10"),
                        ActionTestFixtures.q("0"), Money.of("12.50", "CAD"))), 0);
        var resources = Set.of(
                new ResourceReference("supplier", ActionTestFixtures.SUPPLIER.toString()),
                new ResourceReference("product", ActionTestFixtures.PRODUCT.toString()));
        var context = context(Set.of(CapabilityGrant.scoped(Capability.PURCHASE_ORDER_PLACE, resources)),
                "place-po-operation-1");
        var request = new PlacePurchaseOrderRequest(0, purchaseOrder);

        var first = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);
        var duplicate = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals(PurchaseOrderStatus.PLACED, first.value().snapshot().purchaseOrder(purchaseOrder.id()).status());
        assertEquals(1, first.value().newVersion());
        assertEquals(ActionStatus.ALREADY_COMPLETED, duplicate.status());
        assertEquals(1, world.currentSnapshot().version());
    }

    @Test
    void rerouteRequiresScopedAuthorityAndAppliesOnlyToSupportedPlannedShipment() {
        var initial = logisticsWorld();
        var world = new InMemorySupplyChainSimulationWorld(initial, new SupplyChainSimulator());
        var action = new SupplyChainGroundingActions.RerouteShipment(world);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var requestedResources = Set.of(
                new ResourceReference("shipment", SHIPMENT.toString()),
                new ResourceReference("route", ROUTE_ALTERNATIVE.toString()));
        var grants = Set.of(CapabilityGrant.scoped(Capability.SHIPMENT_REROUTE, requestedResources));
        var context = context(grants, "reroute-operation-1");
        var revisedArrival = ActionTestFixtures.NOW.plus(Duration.ofDays(5));
        var request = new RerouteShipmentRequest(0, SHIPMENT, ROUTE_ALTERNATIVE, revisedArrival,
                "Approved carrier capacity recovery");

        var first = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);
        var duplicate = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals(1, first.value().newVersion());
        assertEquals(ROUTE_ALTERNATIVE, first.value().snapshot().shipment(SHIPMENT).routeId());
        assertEquals(ShipmentStatus.PLANNED, first.value().snapshot().shipment(SHIPMENT).status());
        assertEquals(1, first.value().snapshot().shipment(SHIPMENT).version());
        assertTrue(first.value().effects().stream().anyMatch(e -> e.type()
                == dev.supplychain.agent.simulator.effect.SimulationEffectType.SHIPMENT_REROUTED));
        assertEquals(ActionStatus.ALREADY_COMPLETED, duplicate.status());
        assertEquals(1, world.currentSnapshot().version());
    }

    @Test
    void rerouteIsDeniedWhenEitherShipmentOrTargetRouteIsOutsideGrant() {
        var world = new InMemorySupplyChainSimulationWorld(logisticsWorld(), new SupplyChainSimulator());
        var action = new SupplyChainGroundingActions.RerouteShipment(world);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var grants = Set.of(CapabilityGrant.scoped(Capability.SHIPMENT_REROUTE,
                Set.of(new ResourceReference("shipment", SHIPMENT.toString()))));
        var context = context(grants, "reroute-denied");
        var request = new RerouteShipmentRequest(0, SHIPMENT, ROUTE_ALTERNATIVE,
                ActionTestFixtures.NOW.plus(Duration.ofDays(5)), "Route outside grant");

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);

        assertEquals(ActionStatus.REJECTED, result.status());
        assertEquals(ActionFailureCode.AUTHORIZATION_DENIED, result.failure().code());
        assertEquals(0, world.currentSnapshot().version());
    }

    private static SupplyChainSnapshot logisticsWorld() {
        var base = ActionTestFixtures.world();
        var originalRoute = new Route(ROUTE_ORIGINAL, "Montreal", "Toronto", Duration.ofDays(2), true);
        var alternativeRoute = new Route(ROUTE_ALTERNATIVE, "Montreal", "Ottawa", Duration.ofDays(3), true);
        var carrier = new Carrier(CARRIER, "Action Test Carrier", Set.of(ROUTE_ORIGINAL, ROUTE_ALTERNATIVE), true);
        var shipmentLine = new ShipmentLine(ActionTestFixtures.ORDER, ActionTestFixtures.LINE,
                ActionTestFixtures.PRODUCT, null, ActionTestFixtures.q("5"), ActionTestFixtures.q("5"),
                ActionTestFixtures.NOW);
        var shipment = new Shipment(SHIPMENT, CARRIER, ROUTE_ORIGINAL, Set.of(shipmentLine).stream().toList(),
                ActionTestFixtures.NOW.plus(Duration.ofDays(2)), null, ShipmentStatus.PLANNED, 0);
        return base.toBuilder().route(originalRoute).route(alternativeRoute).carrier(carrier).shipment(shipment).build();
    }

    @SuppressWarnings("unchecked")
    private static Class<Observation<dev.supplychain.agent.actions.external.perception.QualityDisposition>> qualityObservationType() {
        return (Class<Observation<dev.supplychain.agent.actions.external.perception.QualityDisposition>>) (Class<?>) Observation.class;
    }

    private static ActionContext context(Set<CapabilityGrant> grants, String idempotencyKey) {
        return ActionContext.create("exec-external", "corr-external", new ActionPrincipal("agent", grants),
                ActionTestFixtures.NOW.plusSeconds(1), idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private static Class<Observation<Supplier>> observationSupplierType() {
        return (Class<Observation<Supplier>>) (Class<?>) Observation.class;
    }
}
