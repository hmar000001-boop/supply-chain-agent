package dev.supplychain.agent.actions.external.grounding;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.simulator.SimulationResult;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import java.util.Objects;
import java.util.Set;

/** State-changing simulator-backed actions. Real ERP/carrier adapters are deliberately deferred. */
public final class SupplyChainGroundingActions {
    private SupplyChainGroundingActions() { }

    public static final class ReserveInventory implements ActionHandler<ReserveInventoryRequest, SimulationResult> {
        public static final ActionId ID = new ActionId("supply-chain.grounding.inventory.reserve");
        private static final ActionDescriptor D = SupplyChainGroundingActions.descriptor(ID, "Reserve inventory", "Create a validated inventory reservation in the modeled world", Capability.INVENTORY_RESERVE);
        private final SupplyChainSimulationWorld world;
        public ReserveInventory(SupplyChainSimulationWorld world) { this.world = Objects.requireNonNull(world); }
        public ActionDescriptor descriptor() { return D; }
        public Class<ReserveInventoryRequest> inputType() { return ReserveInventoryRequest.class; }
        public Class<SimulationResult> outputType() { return SimulationResult.class; }
        public Set<ResourceReference> resources(ReserveInventoryRequest i) { return Set.of(
                new ResourceReference("customer-order", i.orderId().toString()), new ResourceReference("inventory-lot", i.lotId().toString())); }
        public String requestFingerprint(ReserveInventoryRequest i) { return i.canonical(); }
        public PreconditionCheck precondition(ActionContext c, ReserveInventoryRequest i) {
            long current = world.currentSnapshot().version();
            return current == i.expectedWorldVersion() ? PreconditionCheck.pass()
                    : PreconditionCheck.reject("STALE_WORLD_VERSION", "Current world version is " + current + ", expected " + i.expectedWorldVersion());
        }
        public SimulationResult execute(ActionContext c, ReserveInventoryRequest i) {
            return world.apply(i.expectedWorldVersion(), new SimulationCommand.ReserveInventory(
                    c.operationId(), i.reservationId(), i.orderId(), i.orderLineId(), i.lotId(), i.fulfillmentQuantity(), c.now()));
        }
        public String resultReference(SimulationResult r) { return "supply-chain-snapshot:v" + r.newVersion(); }
    }

    public static final class ReleaseReservation implements ActionHandler<ReleaseReservationRequest, SimulationResult> {
        public static final ActionId ID = new ActionId("supply-chain.grounding.inventory.release");
        private static final ActionDescriptor D = SupplyChainGroundingActions.descriptor(ID, "Release inventory reservation", "Release an existing modeled inventory reservation", Capability.INVENTORY_RELEASE);
        private final SupplyChainSimulationWorld world;
        public ReleaseReservation(SupplyChainSimulationWorld world) { this.world = Objects.requireNonNull(world); }
        public ActionDescriptor descriptor() { return D; }
        public Class<ReleaseReservationRequest> inputType() { return ReleaseReservationRequest.class; }
        public Class<SimulationResult> outputType() { return SimulationResult.class; }
        public Set<ResourceReference> resources(ReleaseReservationRequest i) { return Set.of(new ResourceReference("reservation", i.reservationId().toString())); }
        public String requestFingerprint(ReleaseReservationRequest i) { return i.canonical(); }
        public PreconditionCheck precondition(ActionContext c, ReleaseReservationRequest i) {
            long current = world.currentSnapshot().version();
            return current == i.expectedWorldVersion() ? PreconditionCheck.pass()
                    : PreconditionCheck.reject("STALE_WORLD_VERSION", "Current world version is " + current + ", expected " + i.expectedWorldVersion());
        }
        public SimulationResult execute(ActionContext c, ReleaseReservationRequest i) {
            return world.apply(i.expectedWorldVersion(), new SimulationCommand.ReleaseReservation(c.operationId(), i.reservationId(), c.now()));
        }
        public String resultReference(SimulationResult r) { return "supply-chain-snapshot:v" + r.newVersion(); }
    }

    public static final class PlacePurchaseOrder implements ActionHandler<PlacePurchaseOrderRequest, SimulationResult> {
        public static final ActionId ID = new ActionId("supply-chain.grounding.procurement.purchase-order.place");
        private static final ActionDescriptor D = SupplyChainGroundingActions.descriptor(ID, "Place purchase order", "Place a draft purchase order in the modeled world after capability authorization", Capability.PURCHASE_ORDER_PLACE);
        private final SupplyChainSimulationWorld world;
        public PlacePurchaseOrder(SupplyChainSimulationWorld world) { this.world = Objects.requireNonNull(world); }
        public ActionDescriptor descriptor() { return D; }
        public Class<PlacePurchaseOrderRequest> inputType() { return PlacePurchaseOrderRequest.class; }
        public Class<SimulationResult> outputType() { return SimulationResult.class; }
        public Set<ResourceReference> resources(PlacePurchaseOrderRequest i) {
            var resources = new java.util.LinkedHashSet<ResourceReference>();
            resources.add(new ResourceReference("supplier", i.purchaseOrder().supplierId().toString()));
            i.purchaseOrder().lines().forEach(line -> resources.add(new ResourceReference("product", line.productId().toString())));
            return Set.copyOf(resources);
        }
        public String requestFingerprint(PlacePurchaseOrderRequest i) { return i.canonical(); }
        public PreconditionCheck precondition(ActionContext c, PlacePurchaseOrderRequest i) {
            long current = world.currentSnapshot().version();
            return current == i.expectedWorldVersion() ? PreconditionCheck.pass()
                    : PreconditionCheck.reject("STALE_WORLD_VERSION", "Current world version is " + current + ", expected " + i.expectedWorldVersion());
        }
        public SimulationResult execute(ActionContext c, PlacePurchaseOrderRequest i) {
            return world.apply(i.expectedWorldVersion(), new SimulationCommand.PlacePurchaseOrder(c.operationId(), i.purchaseOrder(), c.now()));
        }
        public String resultReference(SimulationResult r) { return "supply-chain-snapshot:v" + r.newVersion(); }
    }

    public static final class RerouteShipment implements ActionHandler<RerouteShipmentRequest, SimulationResult> {
        public static final ActionId ID = new ActionId("supply-chain.grounding.shipment.reroute");
        private static final ActionDescriptor D = SupplyChainGroundingActions.descriptor(ID, "Reroute shipment",
                "Apply a pre-authorized shipment route change to the modeled world", Capability.SHIPMENT_REROUTE);
        private final SupplyChainSimulationWorld world;
        public RerouteShipment(SupplyChainSimulationWorld world) { this.world = Objects.requireNonNull(world); }
        public ActionDescriptor descriptor() { return D; }
        public Class<RerouteShipmentRequest> inputType() { return RerouteShipmentRequest.class; }
        public Class<SimulationResult> outputType() { return SimulationResult.class; }
        public Set<ResourceReference> resources(RerouteShipmentRequest input) {
            return Set.of(new ResourceReference("shipment", input.shipmentId().toString()),
                    new ResourceReference("route", input.newRouteId().toString()));
        }
        public String requestFingerprint(RerouteShipmentRequest input) { return input.canonical(); }
        public PreconditionCheck precondition(ActionContext context, RerouteShipmentRequest input) {
            long current = world.currentSnapshot().version();
            return current == input.expectedWorldVersion() ? PreconditionCheck.pass()
                    : PreconditionCheck.reject("STALE_WORLD_VERSION", "Current world version is " + current + ", expected " + input.expectedWorldVersion());
        }
        public SimulationResult execute(ActionContext context, RerouteShipmentRequest input) {
            return world.apply(input.expectedWorldVersion(), new SimulationCommand.RerouteShipment(context.operationId(),
                    input.shipmentId(), input.newRouteId(), input.revisedArrivalAt(), input.reason(), context.now()));
        }
        public String resultReference(SimulationResult result) { return "supply-chain-snapshot:v" + result.newVersion(); }
    }

    private static ActionDescriptor descriptor(ActionId id, String name, String description, Capability cap) {
        return new ActionDescriptor(id, name, description, ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE, cap, true);
    }
}
