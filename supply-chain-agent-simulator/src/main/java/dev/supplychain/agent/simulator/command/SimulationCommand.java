package dev.supplychain.agent.simulator.command;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.disruption.Disruption;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLineId;
import dev.supplychain.agent.domain.procurement.PurchaseOrder;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.supplier.SupplierId;
import java.time.Instant;
import java.util.Objects;

/** Deterministic, side-effect-free commands applied to a supply-chain simulation snapshot. */
public sealed interface SimulationCommand permits
        SimulationCommand.ReduceSupplierCapacity,
        SimulationCommand.PlacePurchaseOrder,
        SimulationCommand.ApplyQualityHold,
        SimulationCommand.DelayShipment,
        SimulationCommand.ReserveInventory,
        SimulationCommand.ReleaseReservation,
        SimulationCommand.ChangeOrderDemand,
        SimulationCommand.RecordDisruption {

    String commandId();
    Instant occurredAt();

    record ReduceSupplierCapacity(String commandId, SupplierId supplierId, ProductId productId,
                                  Quantity newCapacity, Instant occurredAt) implements SimulationCommand {
        public ReduceSupplierCapacity {
            requireId(commandId); Objects.requireNonNull(supplierId); Objects.requireNonNull(productId);
            Objects.requireNonNull(newCapacity); Objects.requireNonNull(occurredAt);
        }
    }

    record PlacePurchaseOrder(String commandId, PurchaseOrder purchaseOrder,
                              Instant occurredAt) implements SimulationCommand {
        public PlacePurchaseOrder {
            requireId(commandId); Objects.requireNonNull(purchaseOrder); Objects.requireNonNull(occurredAt);
        }
    }

    record ApplyQualityHold(String commandId, InventoryLotId lotId, String reason,
                            Instant occurredAt) implements SimulationCommand {
        public ApplyQualityHold {
            requireId(commandId); Objects.requireNonNull(lotId); Objects.requireNonNull(occurredAt);
            reason = Objects.requireNonNull(reason, "reason").trim();
            if (reason.isEmpty()) throw new IllegalArgumentException("Quality hold reason cannot be blank");
        }
    }

    record DelayShipment(String commandId, ShipmentId shipmentId, Instant revisedArrivalAt,
                         String reason, Instant occurredAt) implements SimulationCommand {
        public DelayShipment {
            requireId(commandId); Objects.requireNonNull(shipmentId); Objects.requireNonNull(revisedArrivalAt);
            Objects.requireNonNull(occurredAt);
            reason = Objects.requireNonNull(reason, "reason").trim();
            if (reason.isEmpty()) throw new IllegalArgumentException("Delay reason cannot be blank");
        }
    }

    record ReserveInventory(String commandId, ReservationId reservationId,
                            CustomerOrderId orderId, OrderLineId orderLineId,
                            InventoryLotId lotId, Quantity fulfillmentQuantity,
                            Instant occurredAt) implements SimulationCommand {
        public ReserveInventory {
            requireId(commandId); Objects.requireNonNull(reservationId); Objects.requireNonNull(orderId);
            Objects.requireNonNull(orderLineId); Objects.requireNonNull(lotId);
            Objects.requireNonNull(fulfillmentQuantity); Objects.requireNonNull(occurredAt);
            if (!fulfillmentQuantity.isPositive()) throw new IllegalArgumentException("fulfillmentQuantity must be positive");
        }
    }

    record ReleaseReservation(String commandId, ReservationId reservationId,
                              Instant occurredAt) implements SimulationCommand {
        public ReleaseReservation {
            requireId(commandId); Objects.requireNonNull(reservationId); Objects.requireNonNull(occurredAt);
        }
    }

    record ChangeOrderDemand(String commandId, CustomerOrderId orderId, OrderLineId orderLineId,
                              Quantity newRequestedQuantity, Instant occurredAt) implements SimulationCommand {
        public ChangeOrderDemand {
            requireId(commandId); Objects.requireNonNull(orderId); Objects.requireNonNull(orderLineId);
            Objects.requireNonNull(newRequestedQuantity); Objects.requireNonNull(occurredAt);
            if (!newRequestedQuantity.isPositive()) throw new IllegalArgumentException("newRequestedQuantity must be positive");
        }
    }

    record RecordDisruption(String commandId, Disruption disruption,
                            Instant occurredAt) implements SimulationCommand {
        public RecordDisruption {
            requireId(commandId); Objects.requireNonNull(disruption); Objects.requireNonNull(occurredAt);
        }
    }

    private static void requireId(String value) {
        Objects.requireNonNull(value, "commandId");
        if (value.isBlank()) throw new IllegalArgumentException("commandId cannot be blank");
    }
}
