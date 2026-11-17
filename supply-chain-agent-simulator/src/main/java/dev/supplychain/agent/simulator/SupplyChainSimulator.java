package dev.supplychain.agent.simulator;

import dev.supplychain.agent.domain.catalog.ProductSubstitution;
import dev.supplychain.agent.domain.catalog.SubstitutionId;
import dev.supplychain.agent.domain.common.DomainViolation;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.disruption.Disruption;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryReservation;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.inventory.ReservationStatus;
import dev.supplychain.agent.domain.logistics.Shipment;
import dev.supplychain.agent.domain.logistics.Route;
import dev.supplychain.agent.domain.logistics.Carrier;
import dev.supplychain.agent.domain.logistics.ShipmentStatus;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.OrderStatus;
import dev.supplychain.agent.domain.procurement.PurchaseOrder;
import dev.supplychain.agent.domain.procurement.PurchaseOrderStatus;
import dev.supplychain.agent.domain.supplier.Supplier;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import dev.supplychain.agent.simulator.effect.SimulationEffect;
import dev.supplychain.agent.simulator.effect.SimulationEffectType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pure deterministic supply-chain world transition engine. It validates business feasibility
 * and returns a new immutable snapshot; it performs no database, network or operational action.
 */
public final class SupplyChainSimulator {

    public SimulationResult simulate(SupplyChainSnapshot current, SimulationCommand command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        List<SimulationEffect> effects = new ArrayList<>();
        SupplyChainSnapshot next;

        if (command instanceof SimulationCommand.ReduceSupplierCapacity c) {
            next = reduceSupplierCapacity(current, c, effects);
        } else if (command instanceof SimulationCommand.PlacePurchaseOrder c) {
            next = placePurchaseOrder(current, c, effects);
        } else if (command instanceof SimulationCommand.ApplyQualityHold c) {
            next = applyQualityHold(current, c, effects);
        } else if (command instanceof SimulationCommand.DelayShipment c) {
            next = delayShipment(current, c, effects);
        } else if (command instanceof SimulationCommand.RerouteShipment c) {
            next = rerouteShipment(current, c, effects);
        } else if (command instanceof SimulationCommand.ReserveInventory c) {
            next = reserveInventory(current, c, effects);
        } else if (command instanceof SimulationCommand.ReleaseReservation c) {
            next = releaseReservation(current, c, effects);
        } else if (command instanceof SimulationCommand.ChangeOrderDemand c) {
            next = changeOrderDemand(current, c, effects);
        } else if (command instanceof SimulationCommand.RecordDisruption c) {
            next = recordDisruption(current, c, effects);
        } else {
            throw new IllegalArgumentException("Unsupported simulation command: " + command.getClass().getName());
        }

        return new SimulationResult(command.commandId(), current.version(), next.version(), next, effects);
    }

    private SupplyChainSnapshot reduceSupplierCapacity(SupplyChainSnapshot current,
            SimulationCommand.ReduceSupplierCapacity command, List<SimulationEffect> effects) {
        Supplier supplier = current.supplier(command.supplierId());
        var capability = supplier.capability(command.productId()).orElseThrow(() ->
                new DomainViolation(DomainViolation.Code.UNQUALIFIED_SUPPLIER,
                        "Supplier " + supplier.id() + " is not qualified for product " + command.productId()));
        if (capability.capacity().unit() != command.newCapacity().unit()) {
            throw new DomainViolation(DomainViolation.Code.INVALID_QUANTITY, "Supplier capacity unit mismatch");
        }
        if (command.newCapacity().compareTo(capability.capacity()) >= 0) {
            throw new DomainViolation(DomainViolation.Code.INVALID_TRANSITION,
                    "ReduceSupplierCapacity requires a strictly lower capacity");
        }
        Supplier changedSupplier = supplier.withCapability(capability.withCapacity(command.newCapacity()));
        Quantity committed = current.openSupplierCommitment(command.supplierId(), command.productId());
        effects.add(effect(SimulationEffectType.SUPPLIER_CAPACITY_REDUCED,
                "Supplier product capacity reduced from " + capability.capacity() + " to " + command.newCapacity(),
                Set.of(supplier.id().toString(), command.productId().toString()),
                Map.of("previousCapacity", capability.capacity().toString(), "newCapacity", command.newCapacity().toString())));
        if (committed.compareTo(command.newCapacity()) > 0) {
            Set<String> refs = new LinkedHashSet<>();
            refs.add(supplier.id().toString()); refs.add(command.productId().toString());
            current.purchaseOrders().values().stream()
                    .filter(order -> order.supplierId().equals(supplier.id()) && order.status().consumesSupplierCapacity())
                    .filter(order -> order.lines().stream().anyMatch(line -> line.productId().equals(command.productId()) && line.openQuantity().isPositive()))
                    .forEach(order -> refs.add(order.id().toString()));
            effects.add(effect(SimulationEffectType.OPEN_PURCHASE_ORDERS_AT_RISK,
                    "Open supplier commitments exceed the reduced planning-horizon capacity",
                    refs, Map.of("openCommitment", committed.toString(), "newCapacity", command.newCapacity().toString())));
        }
        return advance(current, command.occurredAt(), builder -> builder.replaceSupplier(changedSupplier));
    }

    private SupplyChainSnapshot placePurchaseOrder(SupplyChainSnapshot current,
            SimulationCommand.PlacePurchaseOrder command, List<SimulationEffect> effects) {
        PurchaseOrder draft = command.purchaseOrder();
        if (draft.status() != PurchaseOrderStatus.DRAFT) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE, "Only a DRAFT purchase order can be placed");
        }
        if (current.purchaseOrders().containsKey(draft.id())) {
            throw new DomainViolation(DomainViolation.Code.DUPLICATE_ID, "Purchase order already exists: " + draft.id());
        }
        Supplier supplier = current.supplier(draft.supplierId());
        if (supplier.status() != dev.supplychain.agent.domain.supplier.SupplierStatus.ACTIVE) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE, "Supplier is not active: " + supplier.id());
        }
        Map<dev.supplychain.agent.domain.catalog.ProductId, Quantity> requestedByProduct = new java.util.LinkedHashMap<>();
        draft.lines().forEach(line -> requestedByProduct.merge(line.productId(), line.openQuantity(), Quantity::plus));
        for (var requested : requestedByProduct.entrySet()) {
            var capability = supplier.capability(requested.getKey()).orElseThrow(() ->
                    new DomainViolation(DomainViolation.Code.UNQUALIFIED_SUPPLIER,
                            "Supplier is not qualified for product " + requested.getKey()));
            if (!capability.qualityQualified()) {
                throw new DomainViolation(DomainViolation.Code.UNQUALIFIED_SUPPLIER,
                        "Supplier lacks required quality qualification for " + requested.getKey());
            }
            if (requested.getValue().unit() != capability.capacity().unit()) {
                throw new DomainViolation(DomainViolation.Code.INVALID_QUANTITY, "Purchase-order quantity unit mismatch");
            }
            Instant earliestDelivery = draft.createdAt().plus(capability.leadTime());
            if (draft.expectedDeliveryAt().isBefore(earliestDelivery)) {
                throw new DomainViolation(DomainViolation.Code.INVALID_DATE,
                        "Expected delivery is earlier than supplier lead time for " + requested.getKey());
            }
            Quantity committed = current.openSupplierCommitment(supplier.id(), requested.getKey());
            Quantity total = committed.plus(requested.getValue());
            if (total.compareTo(capability.capacity()) > 0) {
                throw new DomainViolation(DomainViolation.Code.CAPACITY_EXCEEDED,
                        "Purchase order would exceed supplier capacity for " + requested.getKey()
                                + ": existing=" + committed + ", requested=" + requested.getValue()
                                + ", capacity=" + capability.capacity());
            }
        }
        PurchaseOrder placed = draft.withStatus(PurchaseOrderStatus.PLACED);
        effects.add(effect(SimulationEffectType.PURCHASE_ORDER_PLACED,
                "Purchase order placed in the simulation model; no external supplier communication occurred",
                Set.of(placed.id().toString(), supplier.id().toString()), Map.of("status", placed.status().name())));
        return advance(current, command.occurredAt(), builder -> builder.purchaseOrder(placed));
    }

    private SupplyChainSnapshot applyQualityHold(SupplyChainSnapshot current,
            SimulationCommand.ApplyQualityHold command, List<SimulationEffect> effects) {
        InventoryLot lot = current.inventoryLot(command.lotId());
        if (lot.qualityStatus() == LotQualityStatus.QUALITY_HOLD) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE, "Inventory lot is already on quality hold");
        }
        InventoryLot held = lot.applyQualityHold(command.reason(), command.occurredAt());
        effects.add(effect(SimulationEffectType.QUALITY_HOLD_APPLIED,
                "Inventory lot is blocked from new reservations pending quality disposition",
                Set.of(lot.id().toString()), Map.of("reason", command.reason())));
        Set<String> atRisk = new LinkedHashSet<>();
        atRisk.add(lot.id().toString());
        current.reservations().values().stream()
                .filter(reservation -> reservation.lotId().equals(lot.id()))
                .filter(reservation -> reservation.status() == ReservationStatus.ACTIVE)
                .forEach(reservation -> atRisk.add(reservation.id().toString()));
        if (atRisk.size() > 1) {
            effects.add(effect(SimulationEffectType.ACTIVE_RESERVATIONS_AT_RISK,
                    "Existing reservations reference a lot newly placed on quality hold", atRisk, Map.of()));
        }
        return advance(current, command.occurredAt(), builder -> builder.replaceInventoryLot(held));
    }

    private SupplyChainSnapshot delayShipment(SupplyChainSnapshot current,
            SimulationCommand.DelayShipment command, List<SimulationEffect> effects) {
        Shipment shipment = current.shipment(command.shipmentId());
        if (shipment.status() == ShipmentStatus.DELIVERED || shipment.status() == ShipmentStatus.CANCELLED) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE,
                    "Delivered or cancelled shipments cannot be delayed: " + shipment.id());
        }
        if (!command.revisedArrivalAt().isAfter(shipment.expectedArrivalAt())) {
            throw new DomainViolation(DomainViolation.Code.INVALID_DATE,
                    "Revised arrival must be later than the current expected arrival");
        }
        Shipment changed = shipment.delayedUntil(command.revisedArrivalAt());
        Set<String> affected = new LinkedHashSet<>();
        affected.add(shipment.id().toString());
        shipment.affectedOrders().forEach(orderId -> affected.add(orderId.toString()));
        effects.add(effect(SimulationEffectType.SHIPMENT_DELAYED,
                "Shipment expected arrival moved from " + shipment.expectedArrivalAt() + " to " + command.revisedArrivalAt()
                        + "; affected orders require reassessment",
                affected, Map.of("reason", command.reason())));
        return advance(current, command.occurredAt(), builder -> builder.replaceShipment(changed));
    }

    private SupplyChainSnapshot rerouteShipment(SupplyChainSnapshot current,
            SimulationCommand.RerouteShipment command, List<SimulationEffect> effects) {
        Shipment shipment = current.shipment(command.shipmentId());
        if (shipment.status() != ShipmentStatus.PLANNED && shipment.status() != ShipmentStatus.DELAYED) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE,
                    "Only planned or delayed shipments can be rerouted: " + shipment.id());
        }
        if (shipment.routeId().equals(command.newRouteId())) {
            throw new DomainViolation(DomainViolation.Code.INVALID_TRANSITION,
                    "A shipment cannot be rerouted to its current route");
        }
        Route route = current.routes().get(command.newRouteId());
        if (route == null) throw new DomainViolation(DomainViolation.Code.NOT_FOUND,
                "Route not found: " + command.newRouteId());
        if (!route.active()) throw new DomainViolation(DomainViolation.Code.INVALID_STATE,
                "Target route is inactive: " + route.id());
        Carrier carrier = current.carriers().get(shipment.carrierId());
        if (carrier == null) throw new DomainViolation(DomainViolation.Code.NOT_FOUND,
                "Carrier not found: " + shipment.carrierId());
        if (!carrier.active() || !carrier.supportedRoutes().contains(route.id())) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE,
                    "Carrier is not active and qualified for target route " + route.id());
        }
        Instant earliestArrival = command.occurredAt().plus(route.transitTime());
        if (command.revisedArrivalAt().isBefore(earliestArrival)) {
            throw new DomainViolation(DomainViolation.Code.INVALID_DATE,
                    "Revised arrival is earlier than the target route's modeled transit time");
        }
        Shipment rerouted = new Shipment(shipment.id(), shipment.carrierId(), route.id(), shipment.lines(),
                command.revisedArrivalAt(), null, ShipmentStatus.PLANNED, shipment.version() + 1);
        Set<String> affected = new LinkedHashSet<>();
        affected.add(shipment.id().toString());
        shipment.affectedOrders().forEach(orderId -> affected.add(orderId.toString()));
        effects.add(effect(SimulationEffectType.SHIPMENT_REROUTED,
                "Shipment route changed in the simulation model from " + shipment.routeId() + " to " + route.id()
                        + "; affected orders require reassessment",
                affected, Map.of("previousRoute", shipment.routeId().toString(), "newRoute", route.id().toString(),
                        "reason", command.reason(), "revisedArrivalAt", command.revisedArrivalAt().toString())));
        return advance(current, command.occurredAt(), builder -> builder.replaceShipment(rerouted));
    }

    private SupplyChainSnapshot reserveInventory(SupplyChainSnapshot current,
            SimulationCommand.ReserveInventory command, List<SimulationEffect> effects) {
        if (current.reservations().containsKey(command.reservationId())) {
            throw new DomainViolation(DomainViolation.Code.DUPLICATE_ID, "Reservation already exists: " + command.reservationId());
        }
        CustomerOrder order = current.customerOrder(command.orderId());
        if (!order.acceptsReservations()) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE, "Order does not accept new reservations: " + order.id());
        }
        var line = order.lines().get(command.orderLineId());
        if (line == null) throw new DomainViolation(DomainViolation.Code.NOT_FOUND, "Order line not found: " + command.orderLineId());
        if (command.fulfillmentQuantity().unit() != line.requestedQuantity().unit()) {
            throw new DomainViolation(DomainViolation.Code.INVALID_QUANTITY, "Requested fulfillment unit mismatch");
        }
        Quantity alreadyReservedForLine = current.reservedFulfillmentQuantity(order.id(), line.id());
        Quantity alreadyInFlightForLine = current.committedShipmentFulfillmentQuantity(order.id(), line.id());
        Quantity outstanding = line.requestedQuantity()
                .minus(line.fulfilledQuantity())
                .minus(alreadyReservedForLine)
                .minus(alreadyInFlightForLine);
        if (command.fulfillmentQuantity().compareTo(outstanding) > 0) {
            throw new DomainViolation(DomainViolation.Code.ORDER_QUANTITY_EXCEEDED,
                    "Reservation would over-allocate order line " + line.id());
        }
        InventoryLot lot = current.inventoryLot(command.lotId());
        var location = current.locations().get(lot.locationId());
        if (location == null || !location.active()) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE,
                    "Inventory location is not active: " + lot.locationId());
        }
        var requiredProduct = current.product(line.productId());
        var reservedProduct = current.product(lot.productId());
        if (!requiredProduct.active() || !reservedProduct.active()) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE, "Inactive products cannot be reserved");
        }
        SubstitutionId substitutionId = null;
        Quantity physicalQuantity = command.fulfillmentQuantity();
        if (!line.productId().equals(lot.productId())) {
            ProductSubstitution rule = current.approvedSubstitution(line.productId(), lot.productId(), command.occurredAt())
                    .orElseThrow(() -> new DomainViolation(DomainViolation.Code.UNQUALIFIED_SUBSTITUTE,
                            "No approved substitution from " + line.productId() + " to " + lot.productId()));
            substitutionId = rule.id();
            physicalQuantity = command.fulfillmentQuantity().multipliedBy(rule.substituteUnitsPerRequiredUnit());
        }
        if (lot.qualityStatus() != LotQualityStatus.AVAILABLE) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE,
                    "Inventory lot is not eligible for reservation: " + lot.id() + " (" + lot.qualityStatus() + ")");
        }
        if (lot.expiresAt() != null && !lot.expiresAt().isAfter(command.occurredAt())) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE,
                    "Expired inventory lot cannot be reserved: " + lot.id());
        }
        Quantity available = current.availableInventory(lot.id(), command.occurredAt());
        if (available.compareTo(physicalQuantity) < 0) {
            throw new DomainViolation(DomainViolation.Code.INSUFFICIENT_INVENTORY,
                    "Insufficient available inventory in lot " + lot.id() + ": available=" + available
                            + ", required=" + physicalQuantity);
        }
        InventoryReservation reservation = new InventoryReservation(
                command.reservationId(), order.id(), line.id(), lot.id(), lot.productId(), substitutionId,
                physicalQuantity, command.fulfillmentQuantity(), ReservationStatus.ACTIVE, command.occurredAt());
        effects.add(effect(SimulationEffectType.INVENTORY_RESERVED,
                "Inventory reserved in the simulation model", Set.of(order.id().toString(), line.id().toString(),
                        lot.id().toString(), reservation.id().toString()),
                Map.of("physicalQuantity", physicalQuantity.toString(),
                        "fulfillmentQuantity", command.fulfillmentQuantity().toString())));
        return advance(current, command.occurredAt(), builder -> builder.reservation(reservation));
    }

    private SupplyChainSnapshot releaseReservation(SupplyChainSnapshot current,
            SimulationCommand.ReleaseReservation command, List<SimulationEffect> effects) {
        InventoryReservation reservation = current.reservation(command.reservationId());
        if (reservation.status() != ReservationStatus.ACTIVE) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE, "Only active reservations can be released");
        }
        InventoryReservation released = reservation.release();
        effects.add(effect(SimulationEffectType.RESERVATION_RELEASED,
                "Inventory reservation released in the simulation model", Set.of(reservation.id().toString()), Map.of()));
        return advance(current, command.occurredAt(), builder -> builder.replaceReservation(released));
    }

    private SupplyChainSnapshot changeOrderDemand(SupplyChainSnapshot current,
            SimulationCommand.ChangeOrderDemand command, List<SimulationEffect> effects) {
        CustomerOrder order = current.customerOrder(command.orderId());
        if (order.status() != OrderStatus.OPEN && order.status() != OrderStatus.IN_PROGRESS) {
            throw new DomainViolation(DomainViolation.Code.INVALID_STATE, "Order demand cannot change in status " + order.status());
        }
        var line = order.lines().get(command.orderLineId());
        if (line == null) throw new DomainViolation(DomainViolation.Code.NOT_FOUND, "Order line not found: " + command.orderLineId());
        if (line.requestedQuantity().unit() != command.newRequestedQuantity().unit()) {
            throw new DomainViolation(DomainViolation.Code.INVALID_QUANTITY, "Demand quantity unit mismatch");
        }
        Quantity alreadyReserved = current.reservedFulfillmentQuantity(order.id(), line.id());
        Quantity alreadyInFlight = current.committedShipmentFulfillmentQuantity(order.id(), line.id());
        Quantity minimum = line.fulfilledQuantity().plus(alreadyReserved).plus(alreadyInFlight);
        if (command.newRequestedQuantity().compareTo(minimum) < 0) {
            throw new DomainViolation(DomainViolation.Code.ORDER_QUANTITY_EXCEEDED,
                    "New demand cannot be lower than fulfilled, actively reserved and in-flight shipment quantities");
        }
        CustomerOrder changed = order.withLine(line.withRequestedQuantity(command.newRequestedQuantity()));
        effects.add(effect(SimulationEffectType.ORDER_DEMAND_CHANGED,
                "Customer-order demand changed in the simulation model",
                Set.of(order.id().toString(), line.id().toString()),
                Map.of("previousDemand", line.requestedQuantity().toString(),
                        "newDemand", command.newRequestedQuantity().toString())));
        return advance(current, command.occurredAt(), builder -> builder.replaceCustomerOrder(changed));
    }

    private SupplyChainSnapshot recordDisruption(SupplyChainSnapshot current,
            SimulationCommand.RecordDisruption command, List<SimulationEffect> effects) {
        if (current.disruptions().containsKey(command.disruption().id())) {
            throw new DomainViolation(DomainViolation.Code.DUPLICATE_ID,
                    "Disruption already exists: " + command.disruption().id());
        }
        effects.add(effect(SimulationEffectType.DISRUPTION_RECORDED,
                "Disruption observation recorded in the simulation snapshot",
                Set.of(command.disruption().id().toString()), Map.of("type", command.disruption().type().name(),
                        "severity", command.disruption().severity().name())));
        return advance(current, command.occurredAt(), builder -> builder.disruption(command.disruption()));
    }

    private static SupplyChainSnapshot advance(SupplyChainSnapshot current, Instant at,
            Consumer<SupplyChainSnapshot.Builder> mutation) {
        Objects.requireNonNull(at, "occurredAt");
        Instant capturedAt = at.isAfter(current.capturedAt()) ? at : current.capturedAt();
        SupplyChainSnapshot.Builder builder = current.toBuilder()
                .version(current.version() + 1)
                .capturedAt(capturedAt);
        mutation.accept(builder);
        return builder.build();
    }

    private static SimulationEffect effect(SimulationEffectType type, String description,
                                           Set<String> affected, Map<String, String> attributes) {
        return SimulationEffect.of(type, description, affected, attributes);
    }
}
