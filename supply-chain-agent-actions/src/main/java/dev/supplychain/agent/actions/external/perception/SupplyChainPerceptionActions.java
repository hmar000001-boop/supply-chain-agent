package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.logistics.Shipment;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.supplier.Supplier;
import java.util.Objects;
import java.util.Set;

/** Typed read-only action handlers. They expose observations, never mutable domain aggregates. */
public final class SupplyChainPerceptionActions {
    private SupplyChainPerceptionActions() { }

    public static final class ReadSupplier implements ActionHandler<ReadSupplierRequest, Observation<Supplier>> {
        public static final ActionId ID = new ActionId("supply-chain.perception.supplier.read");
        private static final ActionDescriptor D = SupplyChainPerceptionActions.descriptor(ID, "Read supplier", "Read one supplier record with snapshot provenance", Capability.SUPPLIER_READ);
        private final SupplyChainPerceptionPort port;
        public ReadSupplier(SupplyChainPerceptionPort port) { this.port = Objects.requireNonNull(port); }
        public ActionDescriptor descriptor() { return D; }
        public Class<ReadSupplierRequest> inputType() { return ReadSupplierRequest.class; }
        @SuppressWarnings("unchecked") public Class<Observation<Supplier>> outputType() { return (Class<Observation<Supplier>>) (Class<?>) Observation.class; }
        public Set<ResourceReference> resources(ReadSupplierRequest i) { return Set.of(new ResourceReference("supplier", i.supplierId().toString())); }
        public Observation<Supplier> execute(ActionContext c, ReadSupplierRequest i) { return port.supplier(i.supplierId(), c.now()); }
    }
    public static final class ReadInventoryLot implements ActionHandler<ReadInventoryLotRequest, Observation<InventoryLot>> {
        public static final ActionId ID = new ActionId("supply-chain.perception.inventory-lot.read");
        private static final ActionDescriptor D = SupplyChainPerceptionActions.descriptor(ID, "Read inventory lot", "Read one inventory lot with snapshot provenance", Capability.INVENTORY_READ);
        private final SupplyChainPerceptionPort port;
        public ReadInventoryLot(SupplyChainPerceptionPort port) { this.port = Objects.requireNonNull(port); }
        public ActionDescriptor descriptor() { return D; }
        public Class<ReadInventoryLotRequest> inputType() { return ReadInventoryLotRequest.class; }
        @SuppressWarnings("unchecked") public Class<Observation<InventoryLot>> outputType() { return (Class<Observation<InventoryLot>>) (Class<?>) Observation.class; }
        public Set<ResourceReference> resources(ReadInventoryLotRequest i) { return Set.of(new ResourceReference("inventory-lot", i.inventoryLotId().toString())); }
        public Observation<InventoryLot> execute(ActionContext c, ReadInventoryLotRequest i) { return port.inventoryLot(i.inventoryLotId(), c.now()); }
    }
    public static final class ReadOrder implements ActionHandler<ReadCustomerOrderRequest, Observation<CustomerOrder>> {
        public static final ActionId ID = new ActionId("supply-chain.perception.customer-order.read");
        private static final ActionDescriptor D = SupplyChainPerceptionActions.descriptor(ID, "Read customer order", "Read one customer order with snapshot provenance", Capability.ORDER_READ);
        private final SupplyChainPerceptionPort port;
        public ReadOrder(SupplyChainPerceptionPort port) { this.port = Objects.requireNonNull(port); }
        public ActionDescriptor descriptor() { return D; }
        public Class<ReadCustomerOrderRequest> inputType() { return ReadCustomerOrderRequest.class; }
        @SuppressWarnings("unchecked") public Class<Observation<CustomerOrder>> outputType() { return (Class<Observation<CustomerOrder>>) (Class<?>) Observation.class; }
        public Set<ResourceReference> resources(ReadCustomerOrderRequest i) { return Set.of(new ResourceReference("customer-order", i.orderId().toString())); }
        public Observation<CustomerOrder> execute(ActionContext c, ReadCustomerOrderRequest i) { return port.customerOrder(i.orderId(), c.now()); }
    }
    public static final class ReadShipment implements ActionHandler<ReadShipmentRequest, Observation<Shipment>> {
        public static final ActionId ID = new ActionId("supply-chain.perception.shipment.read");
        private static final ActionDescriptor D = SupplyChainPerceptionActions.descriptor(ID, "Read shipment", "Read one shipment with snapshot provenance", Capability.SHIPMENT_READ);
        private final SupplyChainPerceptionPort port;
        public ReadShipment(SupplyChainPerceptionPort port) { this.port = Objects.requireNonNull(port); }
        public ActionDescriptor descriptor() { return D; }
        public Class<ReadShipmentRequest> inputType() { return ReadShipmentRequest.class; }
        @SuppressWarnings("unchecked") public Class<Observation<Shipment>> outputType() { return (Class<Observation<Shipment>>) (Class<?>) Observation.class; }
        public Set<ResourceReference> resources(ReadShipmentRequest i) { return Set.of(new ResourceReference("shipment", i.shipmentId().toString())); }
        public Observation<Shipment> execute(ActionContext c, ReadShipmentRequest i) { return port.shipment(i.shipmentId(), c.now()); }
    }
    public static final class ReadAvailability implements ActionHandler<ReadAvailableInventoryRequest, Observation<Quantity>> {
        public static final ActionId ID = new ActionId("supply-chain.perception.inventory-availability.read");
        private static final ActionDescriptor D = SupplyChainPerceptionActions.descriptor(ID, "Read available inventory", "Calculate available inventory at a specific observation time", Capability.INVENTORY_READ);
        private final SupplyChainPerceptionPort port;
        public ReadAvailability(SupplyChainPerceptionPort port) { this.port = Objects.requireNonNull(port); }
        public ActionDescriptor descriptor() { return D; }
        public Class<ReadAvailableInventoryRequest> inputType() { return ReadAvailableInventoryRequest.class; }
        @SuppressWarnings("unchecked") public Class<Observation<Quantity>> outputType() { return (Class<Observation<Quantity>>) (Class<?>) Observation.class; }
        public Set<ResourceReference> resources(ReadAvailableInventoryRequest i) { return Set.of(new ResourceReference("inventory-lot", i.inventoryLotId().toString())); }
        public Observation<Quantity> execute(ActionContext c, ReadAvailableInventoryRequest i) { return port.availableInventory(i.inventoryLotId(), c.now()); }
    }
    public static final class ReadQualityDisposition implements ActionHandler<ReadQualityStatusRequest, Observation<QualityDisposition>> {
        public static final ActionId ID = new ActionId("supply-chain.perception.quality-disposition.read");
        private static final ActionDescriptor D = SupplyChainPerceptionActions.descriptor(ID, "Read lot quality disposition",
                "Read the lot-level quality status and associated hold metadata from a versioned observation", Capability.QUALITY_READ);
        private final SupplyChainPerceptionPort port;
        public ReadQualityDisposition(SupplyChainPerceptionPort port) { this.port = Objects.requireNonNull(port); }
        public ActionDescriptor descriptor() { return D; }
        public Class<ReadQualityStatusRequest> inputType() { return ReadQualityStatusRequest.class; }
        @SuppressWarnings("unchecked") public Class<Observation<QualityDisposition>> outputType() {
            return (Class<Observation<QualityDisposition>>) (Class<?>) Observation.class;
        }
        public Set<ResourceReference> resources(ReadQualityStatusRequest input) {
            return Set.of(new ResourceReference("inventory-lot", input.inventoryLotId().toString()));
        }
        public Observation<QualityDisposition> execute(ActionContext context, ReadQualityStatusRequest input) {
            Observation<InventoryLot> source = port.inventoryLot(input.inventoryLotId(), context.now());
            InventoryLot lot = source.value();
            QualityDisposition value = new QualityDisposition(lot.id(), lot.qualityStatus(),
                    lot.qualityHeldAt(), lot.qualityHoldReason(), lot.version());
            return new Observation<>(value, source.entity(), source.evidence(), source.provenance(), source.sourceVersion());
        }
    }

    private static ActionDescriptor descriptor(ActionId id, String name, String description, Capability cap) {
        return new ActionDescriptor(id, name, description, ActionCategory.PERCEPTION, ActionEffect.READ_ONLY, cap, false);
    }
}
