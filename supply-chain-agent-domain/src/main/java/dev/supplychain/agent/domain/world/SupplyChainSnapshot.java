package dev.supplychain.agent.domain.world;

import dev.supplychain.agent.domain.catalog.Product;
import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.catalog.ProductSubstitution;
import dev.supplychain.agent.domain.catalog.SubstitutionId;
import dev.supplychain.agent.domain.common.DomainViolation;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.disruption.Disruption;
import dev.supplychain.agent.domain.disruption.DisruptionId;
import dev.supplychain.agent.domain.inventory.InventoryLocation;
import dev.supplychain.agent.domain.inventory.InventoryLocationId;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.InventoryReservation;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.inventory.ReservationStatus;
import dev.supplychain.agent.domain.logistics.Carrier;
import dev.supplychain.agent.domain.logistics.CarrierId;
import dev.supplychain.agent.domain.logistics.Route;
import dev.supplychain.agent.domain.logistics.RouteId;
import dev.supplychain.agent.domain.logistics.Shipment;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLineId;
import dev.supplychain.agent.domain.procurement.PurchaseOrder;
import dev.supplychain.agent.domain.procurement.PurchaseOrderId;
import dev.supplychain.agent.domain.procurement.PurchaseOrderStatus;
import dev.supplychain.agent.domain.recovery.RecoveryCandidate;
import dev.supplychain.agent.domain.recovery.RecoveryCandidateId;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.supplier.Supplier;
import dev.supplychain.agent.domain.supplier.SupplierId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, versioned view of the modeled supply-chain world. It is a simulation snapshot,
 * not a transactional database and not an authorization boundary.
 */
public final class SupplyChainSnapshot {
    private final long version;
    private final Instant capturedAt;
    private final Map<ProductId, Product> products;
    private final Map<SubstitutionId, ProductSubstitution> substitutions;
    private final Map<SupplierId, Supplier> suppliers;
    private final Map<InventoryLocationId, InventoryLocation> locations;
    private final Map<InventoryLotId, InventoryLot> inventoryLots;
    private final Map<CustomerOrderId, CustomerOrder> customerOrders;
    private final Map<PurchaseOrderId, PurchaseOrder> purchaseOrders;
    private final Map<CarrierId, Carrier> carriers;
    private final Map<RouteId, Route> routes;
    private final Map<ShipmentId, Shipment> shipments;
    private final Map<ReservationId, InventoryReservation> reservations;
    private final Map<DisruptionId, Disruption> disruptions;
    private final Map<RecoveryObjectiveId, RecoveryObjective> recoveryObjectives;
    private final Map<RecoveryCandidateId, RecoveryCandidate> recoveryCandidates;

    private SupplyChainSnapshot(Builder builder) {
        if (builder.version < 0) throw new IllegalArgumentException("version cannot be negative");
        this.version = builder.version;
        this.capturedAt = Objects.requireNonNull(builder.capturedAt, "capturedAt");
        this.products = Map.copyOf(builder.products);
        this.substitutions = Map.copyOf(builder.substitutions);
        this.suppliers = Map.copyOf(builder.suppliers);
        this.locations = Map.copyOf(builder.locations);
        this.inventoryLots = Map.copyOf(builder.inventoryLots);
        this.customerOrders = Map.copyOf(builder.customerOrders);
        this.purchaseOrders = Map.copyOf(builder.purchaseOrders);
        this.carriers = Map.copyOf(builder.carriers);
        this.routes = Map.copyOf(builder.routes);
        this.shipments = Map.copyOf(builder.shipments);
        this.reservations = Map.copyOf(builder.reservations);
        this.disruptions = Map.copyOf(builder.disruptions);
        this.recoveryObjectives = Map.copyOf(builder.recoveryObjectives);
        this.recoveryCandidates = Map.copyOf(builder.recoveryCandidates);
        validateReferencesAndBalances();
    }

    public static Builder builder(long version, Instant capturedAt) {
        return new Builder(version, capturedAt);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public long version() { return version; }
    public Instant capturedAt() { return capturedAt; }
    public Map<ProductId, Product> products() { return products; }
    public Map<SubstitutionId, ProductSubstitution> substitutions() { return substitutions; }
    public Map<SupplierId, Supplier> suppliers() { return suppliers; }
    public Map<InventoryLocationId, InventoryLocation> locations() { return locations; }
    public Map<InventoryLotId, InventoryLot> inventoryLots() { return inventoryLots; }
    public Map<CustomerOrderId, CustomerOrder> customerOrders() { return customerOrders; }
    public Map<PurchaseOrderId, PurchaseOrder> purchaseOrders() { return purchaseOrders; }
    public Map<CarrierId, Carrier> carriers() { return carriers; }
    public Map<RouteId, Route> routes() { return routes; }
    public Map<ShipmentId, Shipment> shipments() { return shipments; }
    public Map<ReservationId, InventoryReservation> reservations() { return reservations; }
    public Map<DisruptionId, Disruption> disruptions() { return disruptions; }
    public Map<RecoveryObjectiveId, RecoveryObjective> recoveryObjectives() { return recoveryObjectives; }
    public Map<RecoveryCandidateId, RecoveryCandidate> recoveryCandidates() { return recoveryCandidates; }

    public Product product(ProductId id) { return require(products, id, "product"); }
    public Supplier supplier(SupplierId id) { return require(suppliers, id, "supplier"); }
    public InventoryLot inventoryLot(InventoryLotId id) { return require(inventoryLots, id, "inventory lot"); }
    public CustomerOrder customerOrder(CustomerOrderId id) { return require(customerOrders, id, "customer order"); }
    public PurchaseOrder purchaseOrder(PurchaseOrderId id) { return require(purchaseOrders, id, "purchase order"); }
    public Shipment shipment(ShipmentId id) { return require(shipments, id, "shipment"); }
    public InventoryReservation reservation(ReservationId id) { return require(reservations, id, "reservation"); }

    public Optional<ProductSubstitution> approvedSubstitution(
            ProductId requiredProductId, ProductId substituteProductId, Instant at) {
        return substitutions.values().stream()
                .filter(rule -> rule.requiredProductId().equals(requiredProductId))
                .filter(rule -> rule.substituteProductId().equals(substituteProductId))
                .filter(rule -> rule.approvedAt(at))
                .findFirst();
    }

    public Quantity reservedPhysicalQuantity(InventoryLotId lotId) {
        InventoryLot lot = inventoryLot(lotId);
        return reservations.values().stream()
                .filter(reservation -> reservation.lotId().equals(lotId))
                .filter(reservation -> reservation.status() == ReservationStatus.ACTIVE)
                .map(InventoryReservation::reservedQuantity)
                .reduce(Quantity.zero(lot.quantityOnHand().unit()), Quantity::plus);
    }

    public Quantity availableInventory(InventoryLotId lotId, Instant at) {
        InventoryLot lot = inventoryLot(lotId);
        return lot.availableQuantity(reservedPhysicalQuantity(lotId), at);
    }

    public Quantity reservedFulfillmentQuantity(CustomerOrderId orderId, OrderLineId lineId) {
        var order = customerOrder(orderId);
        var line = order.lines().get(lineId);
        if (line == null) throw new DomainViolation(DomainViolation.Code.NOT_FOUND, "Order line not found: " + lineId);
        return reservations.values().stream()
                .filter(reservation -> reservation.orderId().equals(orderId))
                .filter(reservation -> reservation.orderLineId().equals(lineId))
                .filter(reservation -> reservation.status() == ReservationStatus.ACTIVE)
                .map(InventoryReservation::fulfillmentQuantity)
                .reduce(Quantity.zero(line.requestedQuantity().unit()), Quantity::plus);
    }

    /** Quantity already committed to non-terminal shipments for this customer-order line. */
    public Quantity committedShipmentFulfillmentQuantity(CustomerOrderId orderId, OrderLineId lineId) {
        var order = customerOrder(orderId);
        var line = order.lines().get(lineId);
        if (line == null) throw new DomainViolation(DomainViolation.Code.NOT_FOUND, "Order line not found: " + lineId);
        return shipments.values().stream()
                .filter(SupplyChainSnapshot::shipmentConsumesDemand)
                .flatMap(shipment -> shipment.lines().stream())
                .filter(shipmentLine -> shipmentLine.orderId().equals(orderId))
                .filter(shipmentLine -> shipmentLine.orderLineId().equals(lineId))
                .map(shipmentLine -> shipmentLine.fulfillmentQuantity())
                .reduce(Quantity.zero(line.requestedQuantity().unit()), Quantity::plus);
    }

    /** Quantity already delivered according to terminal shipment records for this order line. */
    public Quantity deliveredShipmentQuantity(CustomerOrderId orderId, OrderLineId lineId) {
        var order = customerOrder(orderId);
        var line = order.lines().get(lineId);
        if (line == null) throw new DomainViolation(DomainViolation.Code.NOT_FOUND, "Order line not found: " + lineId);
        return shipments.values().stream()
                .filter(shipment -> shipment.status() == dev.supplychain.agent.domain.logistics.ShipmentStatus.DELIVERED)
                .flatMap(shipment -> shipment.lines().stream())
                .filter(shipmentLine -> shipmentLine.orderId().equals(orderId))
                .filter(shipmentLine -> shipmentLine.orderLineId().equals(lineId))
                .map(shipmentLine -> shipmentLine.fulfillmentQuantity())
                .reduce(Quantity.zero(line.requestedQuantity().unit()), Quantity::plus);
    }

    private static boolean shipmentConsumesDemand(dev.supplychain.agent.domain.logistics.Shipment shipment) {
        return shipment.status() == dev.supplychain.agent.domain.logistics.ShipmentStatus.PLANNED
                || shipment.status() == dev.supplychain.agent.domain.logistics.ShipmentStatus.DISPATCHED
                || shipment.status() == dev.supplychain.agent.domain.logistics.ShipmentStatus.IN_TRANSIT
                || shipment.status() == dev.supplychain.agent.domain.logistics.ShipmentStatus.DELAYED;
    }

    public Quantity openSupplierCommitment(SupplierId supplierId, ProductId productId) {
        Supplier supplier = suppliers.get(supplierId);
        if (supplier == null) throw new DomainViolation(DomainViolation.Code.NOT_FOUND, "Supplier not found: " + supplierId);
        Quantity zero = supplier.capability(productId)
                .map(capability -> Quantity.zero(capability.capacity().unit()))
                .orElseThrow(() -> new DomainViolation(DomainViolation.Code.UNQUALIFIED_SUPPLIER,
                        "Supplier is not qualified for product " + productId));
        return purchaseOrders.values().stream()
                .filter(order -> order.supplierId().equals(supplierId))
                .filter(order -> order.status().consumesSupplierCapacity())
                .flatMap(order -> order.lines().stream())
                .filter(line -> line.productId().equals(productId))
                .map(line -> line.openQuantity())
                .reduce(zero, Quantity::plus);
    }

    private void validateReferencesAndBalances() {
        uniqueProductSkus();
        suppliers.values().forEach(supplier -> supplier.capabilities().values().forEach(capability -> {
            Product product = require(products, capability.productId(), "supplier capability product");
            if (product.unit() != capability.capacity().unit()) invalidRef("Supplier capacity unit mismatch for " + product.sku());
        }));
        inventoryLots.values().forEach(lot -> {
            Product product = require(products, lot.productId(), "inventory product");
            require(locations, lot.locationId(), "inventory location");
            if (product.unit() != lot.quantityOnHand().unit()) invalidRef("Inventory unit mismatch for lot " + lot.id());
        });
        substitutions.values().forEach(rule -> {
            Product requiredProduct = require(products, rule.requiredProductId(), "substitution required product");
            Product substituteProduct = require(products, rule.substituteProductId(), "substitution product");
            if (requiredProduct.unit() != substituteProduct.unit()) {
                invalidRef("M1 substitution requires identical units for " + rule.id());
            }
        });
        validateSubstitutionWindows();
        customerOrders.values().forEach(order -> order.lines().values().forEach(line -> {
            Product product = require(products, line.productId(), "order-line product");
            if (product.unit() != line.requestedQuantity().unit()) invalidRef("Order-line unit mismatch: " + line.id());
        }));
        purchaseOrders.values().forEach(order -> {
            require(suppliers, order.supplierId(), "purchase-order supplier");
            order.lines().forEach(line -> {
                Product product = require(products, line.productId(), "purchase-order product");
                if (product.unit() != line.orderedQuantity().unit()) invalidRef("Purchase-order unit mismatch: " + line.productId());
            });
        });
        carriers.values().forEach(carrier -> carrier.supportedRoutes().forEach(routeId -> require(routes, routeId, "carrier route")));
        shipments.values().forEach(shipment -> {
            Carrier carrier = require(carriers, shipment.carrierId(), "shipment carrier");
            Route route = require(routes, shipment.routeId(), "shipment route");
            if (!carrier.supportedRoutes().contains(route.id())) invalidRef("Carrier does not support shipment route: " + shipment.id());
            shipment.lines().forEach(shipmentLine -> {
                CustomerOrder order = require(customerOrders, shipmentLine.orderId(), "shipment order");
                var orderLine = order.lines().get(shipmentLine.orderLineId());
                if (orderLine == null) invalidRef("Shipment line references an unknown customer order line: " + shipment.id());
                Product product = require(products, shipmentLine.productId(), "shipment product");
                Product requiredProduct = require(products, orderLine.productId(), "shipment required product");
                if (product.unit() != shipmentLine.shippedQuantity().unit()
                        || requiredProduct.unit() != shipmentLine.fulfillmentQuantity().unit()) {
                    invalidRef("Shipment quantity unit mismatch");
                }
                if (shipmentLine.productId().equals(orderLine.productId())) {
                    if (shipmentLine.substitutionId() != null
                            || shipmentLine.shippedQuantity().compareTo(shipmentLine.fulfillmentQuantity()) != 0) {
                        invalidRef("Exact-product shipment must not cite a substitution and quantities must match");
                    }
                } else {
                    if (shipmentLine.substitutionId() == null) invalidRef("Substitute shipment requires a substitution rule");
                    ProductSubstitution rule = require(substitutions, shipmentLine.substitutionId(), "shipment substitution rule");
                    if (!rule.requiredProductId().equals(orderLine.productId())
                            || !rule.substituteProductId().equals(shipmentLine.productId())
                            || !rule.approvedAt(shipmentLine.allocatedAt())) {
                        invalidRef("Shipment substitution rule was not valid when the shipment line was allocated");
                    }
                    Quantity expectedShipped = shipmentLine.fulfillmentQuantity()
                            .multipliedBy(rule.substituteUnitsPerRequiredUnit());
                    if (expectedShipped.compareTo(shipmentLine.shippedQuantity()) != 0) {
                        invalidRef("Shipment substitute quantity does not match the approved ratio");
                    }
                }
            });
        });
        validateReservations();
        validateOrderFulfillmentCommitments();
        recoveryObjectives.values().forEach(objective -> objective.affectedOrders()
                .forEach(orderId -> require(customerOrders, orderId, "recovery objective order")));
        recoveryCandidates.values().forEach(candidate -> require(recoveryObjectives, candidate.objectiveId(), "recovery candidate objective"));
    }

    private void validateSubstitutionWindows() {
        var approved = substitutions.values().stream()
                .filter(rule -> rule.status() == dev.supplychain.agent.domain.catalog.SubstitutionStatus.APPROVED)
                .toList();
        for (int i = 0; i < approved.size(); i++) {
            for (int j = i + 1; j < approved.size(); j++) {
                var first = approved.get(i);
                var second = approved.get(j);
                if (first.requiredProductId().equals(second.requiredProductId())
                        && first.substituteProductId().equals(second.substituteProductId())
                        && intervalsOverlap(first.validFrom(), first.validUntil(), second.validFrom(), second.validUntil())) {
                    throw new DomainViolation(DomainViolation.Code.CONFLICT,
                            "Overlapping approved substitution rules for " + first.requiredProductId()
                                    + " -> " + first.substituteProductId());
                }
            }
        }
    }

    private static boolean intervalsOverlap(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        boolean firstBeforeSecondEnd = secondEnd == null || firstStart.isBefore(secondEnd);
        boolean secondBeforeFirstEnd = firstEnd == null || secondStart.isBefore(firstEnd);
        return firstBeforeSecondEnd && secondBeforeFirstEnd;
    }

    private void validateOrderFulfillmentCommitments() {
        for (CustomerOrder order : customerOrders.values()) {
            for (var line : order.lines().values()) {
                Quantity reserved = reservations.values().stream()
                        .filter(reservation -> reservation.status() == ReservationStatus.ACTIVE)
                        .filter(reservation -> reservation.orderId().equals(order.id()))
                        .filter(reservation -> reservation.orderLineId().equals(line.id()))
                        .map(InventoryReservation::fulfillmentQuantity)
                        .reduce(Quantity.zero(line.requestedQuantity().unit()), Quantity::plus);
                Quantity shipped = committedShipmentFulfillmentQuantity(order.id(), line.id());
                Quantity delivered = deliveredShipmentQuantity(order.id(), line.id());
                if (delivered.compareTo(line.fulfilledQuantity()) > 0) {
                    throw new DomainViolation(DomainViolation.Code.CONFLICT,
                            "Delivered shipment quantity exceeds recorded fulfillment for " + order.id() + "/" + line.id());
                }
                Quantity committed = line.fulfilledQuantity().plus(reserved).plus(shipped);
                if (committed.compareTo(line.requestedQuantity()) > 0) {
                    throw new DomainViolation(DomainViolation.Code.ORDER_QUANTITY_EXCEEDED,
                            "Fulfilled, reserved and in-flight shipment quantities exceed demand for "
                                    + order.id() + "/" + line.id());
                }
                if (order.status() == dev.supplychain.agent.domain.orders.OrderStatus.FULFILLED
                        && line.fulfilledQuantity().compareTo(line.requestedQuantity()) != 0) {
                    invalidRef("FULFILLED order contains an unfulfilled line: " + order.id() + "/" + line.id());
                }
            }
        }
    }

    private void validateReservations() {
        Map<InventoryLotId, Quantity> physicalTotals = new LinkedHashMap<>();
        Map<ReservationLineKey, Quantity> fulfillmentTotals = new LinkedHashMap<>();
        for (InventoryReservation reservation : reservations.values()) {
            InventoryLot lot = require(inventoryLots, reservation.lotId(), "reservation lot");
            CustomerOrder order = require(customerOrders, reservation.orderId(), "reservation order");
            var line = order.lines().get(reservation.orderLineId());
            if (line == null) invalidRef("Reservation references an unknown order line: " + reservation.id());
            if (!lot.productId().equals(reservation.reservedProductId())) invalidRef("Reservation product does not match lot");
            Product actualProduct = require(products, reservation.reservedProductId(), "reservation product");
            Product requiredProduct = require(products, line.productId(), "reservation required product");
            if (actualProduct.unit() != reservation.reservedQuantity().unit()
                    || requiredProduct.unit() != reservation.fulfillmentQuantity().unit()) {
                invalidRef("Reservation quantity unit mismatch: " + reservation.id());
            }
            if (reservation.reservedProductId().equals(line.productId())) {
                if (reservation.substitutionId() != null) invalidRef("Exact-product reservation must not name a substitution rule");
                if (reservation.reservedQuantity().compareTo(reservation.fulfillmentQuantity()) != 0) {
                    invalidRef("Exact-product reservation quantities must match");
                }
            } else {
                if (reservation.substitutionId() == null) invalidRef("Substitute reservation requires an approved substitution rule");
                ProductSubstitution rule = require(substitutions, reservation.substitutionId(), "reservation substitution rule");
                if (!rule.requiredProductId().equals(line.productId())
                        || !rule.substituteProductId().equals(reservation.reservedProductId())
                        || !rule.approvedAt(reservation.createdAt())) {
                    invalidRef("Reservation substitution rule was not valid at reservation time");
                }
                Quantity expected = reservation.fulfillmentQuantity().multipliedBy(rule.substituteUnitsPerRequiredUnit());
                if (expected.compareTo(reservation.reservedQuantity()) != 0) invalidRef("Substitution quantity ratio mismatch");
            }
            if (reservation.status() == ReservationStatus.ACTIVE) {
                physicalTotals.merge(lot.id(), reservation.reservedQuantity(), Quantity::plus);
                ReservationLineKey key = new ReservationLineKey(reservation.orderId(), reservation.orderLineId());
                fulfillmentTotals.merge(key, reservation.fulfillmentQuantity(), Quantity::plus);
            }
        }
        physicalTotals.forEach((lotId, reserved) -> {
            Quantity onHand = inventoryLots.get(lotId).quantityOnHand();
            if (reserved.compareTo(onHand) > 0) {
                throw new DomainViolation(DomainViolation.Code.INSUFFICIENT_INVENTORY,
                        "Active reservations exceed on-hand quantity for lot " + lotId);
            }
        });
        fulfillmentTotals.forEach((key, reserved) -> {
            CustomerOrder order = customerOrders.get(key.orderId());
            var line = order.lines().get(key.lineId());
            Quantity total = line.fulfilledQuantity().plus(reserved);
            if (total.compareTo(line.requestedQuantity()) > 0) {
                throw new DomainViolation(DomainViolation.Code.ORDER_QUANTITY_EXCEEDED,
                        "Fulfilled plus reserved quantity exceeds demand for " + key);
            }
        });
    }

    private void uniqueProductSkus() {
        Map<String, ProductId> seen = new LinkedHashMap<>();
        products.values().forEach(product -> {
            String key = product.sku().toUpperCase(java.util.Locale.ROOT);
            ProductId previous = seen.putIfAbsent(key, product.id());
            if (previous != null) throw new DomainViolation(DomainViolation.Code.DUPLICATE_SKU,
                    "Duplicate product SKU " + product.sku());
        });
    }

    private static <K, V> V require(Map<K, V> values, K key, String description) {
        V value = values.get(key);
        if (value == null) throw new DomainViolation(DomainViolation.Code.INVALID_REFERENCE,
                "Unknown " + description + ": " + key);
        return value;
    }

    private static void invalidRef(String message) {
        throw new DomainViolation(DomainViolation.Code.INVALID_REFERENCE, message);
    }

    private record ReservationLineKey(CustomerOrderId orderId, OrderLineId lineId) { }

    public static final class Builder {
        private long version;
        private Instant capturedAt;
        private final Map<ProductId, Product> products = new LinkedHashMap<>();
        private final Map<SubstitutionId, ProductSubstitution> substitutions = new LinkedHashMap<>();
        private final Map<SupplierId, Supplier> suppliers = new LinkedHashMap<>();
        private final Map<InventoryLocationId, InventoryLocation> locations = new LinkedHashMap<>();
        private final Map<InventoryLotId, InventoryLot> inventoryLots = new LinkedHashMap<>();
        private final Map<CustomerOrderId, CustomerOrder> customerOrders = new LinkedHashMap<>();
        private final Map<PurchaseOrderId, PurchaseOrder> purchaseOrders = new LinkedHashMap<>();
        private final Map<CarrierId, Carrier> carriers = new LinkedHashMap<>();
        private final Map<RouteId, Route> routes = new LinkedHashMap<>();
        private final Map<ShipmentId, Shipment> shipments = new LinkedHashMap<>();
        private final Map<ReservationId, InventoryReservation> reservations = new LinkedHashMap<>();
        private final Map<DisruptionId, Disruption> disruptions = new LinkedHashMap<>();
        private final Map<RecoveryObjectiveId, RecoveryObjective> recoveryObjectives = new LinkedHashMap<>();
        private final Map<RecoveryCandidateId, RecoveryCandidate> recoveryCandidates = new LinkedHashMap<>();

        private Builder(long version, Instant capturedAt) {
            this.version = version;
            this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        }
        private Builder(SupplyChainSnapshot snapshot) {
            this(snapshot.version, snapshot.capturedAt);
            products.putAll(snapshot.products); substitutions.putAll(snapshot.substitutions);
            suppliers.putAll(snapshot.suppliers); locations.putAll(snapshot.locations);
            inventoryLots.putAll(snapshot.inventoryLots); customerOrders.putAll(snapshot.customerOrders);
            purchaseOrders.putAll(snapshot.purchaseOrders); carriers.putAll(snapshot.carriers);
            routes.putAll(snapshot.routes); shipments.putAll(snapshot.shipments);
            reservations.putAll(snapshot.reservations); disruptions.putAll(snapshot.disruptions);
            recoveryObjectives.putAll(snapshot.recoveryObjectives); recoveryCandidates.putAll(snapshot.recoveryCandidates);
        }
        public Builder version(long value) { this.version = value; return this; }
        public Builder capturedAt(Instant value) { this.capturedAt = Objects.requireNonNull(value); return this; }
        public Builder product(Product value) { put(products, value.id(), value); return this; }
        public Builder substitution(ProductSubstitution value) { put(substitutions, value.id(), value); return this; }
        public Builder supplier(Supplier value) { put(suppliers, value.id(), value); return this; }
        public Builder location(InventoryLocation value) { put(locations, value.id(), value); return this; }
        public Builder inventoryLot(InventoryLot value) { put(inventoryLots, value.id(), value); return this; }
        public Builder customerOrder(CustomerOrder value) { put(customerOrders, value.id(), value); return this; }
        public Builder purchaseOrder(PurchaseOrder value) { put(purchaseOrders, value.id(), value); return this; }
        public Builder carrier(Carrier value) { put(carriers, value.id(), value); return this; }
        public Builder route(Route value) { put(routes, value.id(), value); return this; }
        public Builder shipment(Shipment value) { put(shipments, value.id(), value); return this; }
        public Builder reservation(InventoryReservation value) { put(reservations, value.id(), value); return this; }
        public Builder disruption(Disruption value) { put(disruptions, value.id(), value); return this; }
        public Builder recoveryObjective(RecoveryObjective value) { put(recoveryObjectives, value.id(), value); return this; }
        public Builder recoveryCandidate(RecoveryCandidate value) { put(recoveryCandidates, value.id(), value); return this; }
        public Builder replaceSupplier(Supplier value) { replace(suppliers, value.id(), value); return this; }
        public Builder replaceInventoryLot(InventoryLot value) { replace(inventoryLots, value.id(), value); return this; }
        public Builder replaceCustomerOrder(CustomerOrder value) { replace(customerOrders, value.id(), value); return this; }
        public Builder replaceShipment(Shipment value) { replace(shipments, value.id(), value); return this; }
        public Builder replaceReservation(InventoryReservation value) { replace(reservations, value.id(), value); return this; }
        public SupplyChainSnapshot build() { return new SupplyChainSnapshot(this); }
        private static <K, V> void put(Map<K, V> target, K key, V value) {
            Objects.requireNonNull(key, "key"); Objects.requireNonNull(value, "value");
            if (target.putIfAbsent(key, value) != null) {
                throw new DomainViolation(DomainViolation.Code.DUPLICATE_ID, "Duplicate domain identifier: " + key);
            }
        }
        private static <K, V> void replace(Map<K, V> target, K key, V value) {
            Objects.requireNonNull(key, "key"); Objects.requireNonNull(value, "value");
            if (!target.containsKey(key)) {
                throw new DomainViolation(DomainViolation.Code.NOT_FOUND, "Cannot replace missing domain identifier: " + key);
            }
            target.put(key, value);
        }
    }
}
