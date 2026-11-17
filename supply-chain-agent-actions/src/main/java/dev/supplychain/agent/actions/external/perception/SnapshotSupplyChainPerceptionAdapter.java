package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.common.DomainViolation;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.logistics.Shipment;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.supplier.Supplier;
import dev.supplychain.agent.domain.supplier.SupplierId;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import java.time.Instant;
import java.util.Objects;

/** Perception adapter over M1's immutable simulator snapshot; replaceable by real read-only system adapters. */
public final class SnapshotSupplyChainPerceptionAdapter implements SupplyChainPerceptionPort {
    private final SupplyChainSnapshotProvider snapshots;
    public SnapshotSupplyChainPerceptionAdapter(SupplyChainSnapshotProvider snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }
    @Override public Observation<Supplier> supplier(SupplierId id, Instant readAt) {
        var snapshot = snapshotAt(readAt);
        return observation(snapshot, "supplier", id.toString(), snapshot.suppliers().get(id), readAt);
    }
    @Override public Observation<InventoryLot> inventoryLot(InventoryLotId id, Instant readAt) {
        var snapshot = snapshotAt(readAt);
        return observation(snapshot, "inventory-lot", id.toString(), snapshot.inventoryLots().get(id), readAt);
    }
    @Override public Observation<CustomerOrder> customerOrder(CustomerOrderId id, Instant readAt) {
        var snapshot = snapshotAt(readAt);
        return observation(snapshot, "customer-order", id.toString(), snapshot.customerOrders().get(id), readAt);
    }
    @Override public Observation<Shipment> shipment(ShipmentId id, Instant readAt) {
        var snapshot = snapshotAt(readAt);
        return observation(snapshot, "shipment", id.toString(), snapshot.shipments().get(id), readAt);
    }
    @Override public Observation<Quantity> availableInventory(InventoryLotId id, Instant readAt) {
        var snapshot = snapshotAt(readAt);
        var lot = snapshot.inventoryLots().get(id);
        if (lot == null) throw notFound("inventory lot", id.toString());
        Quantity available = snapshot.availableInventory(id, readAt);
        return observation(snapshot, "inventory-availability", id.toString(), available, readAt);
    }
    private SupplyChainSnapshot snapshotAt(Instant at) {
        Objects.requireNonNull(at, "readAt");
        SupplyChainSnapshot snapshot = Objects.requireNonNull(snapshots.currentSnapshot(), "current snapshot");
        if (at.isBefore(snapshot.capturedAt())) throw new IllegalArgumentException("readAt precedes the current snapshot capture time");
        return snapshot;
    }
    private static <T> Observation<T> observation(SupplyChainSnapshot snapshot, String type, String id, T value, Instant readAt) {
        if (value == null) throw notFound(type, id);
        String sourceId = "snapshot-v" + snapshot.version() + ":" + type + ":" + id;
        var provenance = new Provenance(ProvenanceSource.SIMULATOR, sourceId, snapshot.capturedAt(), readAt,
                Long.toString(snapshot.version()));
        return new Observation<>(value, new EntityReference(type, id),
                new EvidenceReference("supply-chain-snapshot", sourceId), provenance, snapshot.version());
    }
    private static DomainViolation notFound(String type, String id) {
        return new DomainViolation(DomainViolation.Code.NOT_FOUND, "No " + type + " exists with id " + id);
    }
}
