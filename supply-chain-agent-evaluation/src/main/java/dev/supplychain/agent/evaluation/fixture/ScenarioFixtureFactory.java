package dev.supplychain.agent.evaluation.fixture;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.domain.catalog.Product;
import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.common.UnitOfMeasure;
import dev.supplychain.agent.domain.inventory.InventoryLocation;
import dev.supplychain.agent.domain.inventory.InventoryLocationId;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.InventoryReservation;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.inventory.ReservationStatus;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.disruption.Disruption;
import dev.supplychain.agent.domain.disruption.DisruptionId;
import dev.supplychain.agent.domain.disruption.DisruptionSeverity;
import dev.supplychain.agent.domain.disruption.DisruptionStatus;
import dev.supplychain.agent.domain.disruption.DisruptionType;
import dev.supplychain.agent.domain.logistics.Carrier;
import dev.supplychain.agent.domain.logistics.CarrierId;
import dev.supplychain.agent.domain.logistics.Route;
import dev.supplychain.agent.domain.logistics.RouteId;
import dev.supplychain.agent.domain.logistics.Shipment;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import dev.supplychain.agent.domain.logistics.ShipmentLine;
import dev.supplychain.agent.domain.logistics.ShipmentStatus;
import dev.supplychain.agent.domain.supplier.Supplier;
import dev.supplychain.agent.domain.supplier.SupplierId;
import dev.supplychain.agent.domain.supplier.SupplierProductCapability;
import dev.supplychain.agent.domain.supplier.SupplierStatus;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLine;
import dev.supplychain.agent.domain.orders.OrderLineId;
import dev.supplychain.agent.domain.orders.OrderPriority;
import dev.supplychain.agent.domain.orders.OrderStatus;
import dev.supplychain.agent.domain.recovery.RecoveryAction;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.catalog.ScenarioDefinition;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioParameters;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.planning.api.ActionImpactEstimate;
import dev.supplychain.agent.planning.api.DeterministicCandidateEvaluator;
import dev.supplychain.agent.planning.api.PlannerBudget;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningAuthorityPolicy;
import dev.supplychain.agent.planning.api.PlanningComplexity;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningRiskPolicy;
import dev.supplychain.agent.planning.api.PlanningScoringProfile;
import dev.supplychain.agent.planning.api.SimulatorBackedPlanningWorldModel;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import dev.supplychain.agent.simulator.command.SimulationCommand;

import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

/** Builds real M1/M5 domain snapshots and proposal contracts from the versioned M8 corpus. */
public final class ScenarioFixtureFactory {
    public static final Instant FIXTURE_AS_OF = Instant.parse("2026-11-20T12:00:00Z");
    public static final String WORLD_MODEL_FINGERPRINT = "m8-b1-supply-chain-simulator-world-v1";
    public static final String EVALUATOR_FINGERPRINT = "deterministic-candidate-evaluator-v1";
    public static final String STANDARD_BUDGET_PROFILE = "STANDARD-24-EXPANSION-32-EVALUATION-v1";
    public static final String TIGHT_BUDGET_PROFILE = "TIGHT-1-EXPANSION-1-EVALUATION-v1";
    public static final ActionId RESERVE_INVENTORY = new ActionId("supply-chain.grounding.inventory.reserve");

    private final ScenarioCatalog catalog;

    public ScenarioFixtureFactory(ScenarioCatalog catalog) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * A moving clock whose timeline is anchored at the corpus timestamp. Fixtures remain stable
     * across calendar dates, while planner runtime/deadline guards still advance during a run.
     */
    public Clock advancingFixtureClock() {
        Instant wallClockAnchor = Instant.now();
        return Clock.offset(Clock.systemUTC(), Duration.between(wallClockAnchor, FIXTURE_AS_OF));
    }

    public List<ScenarioFixture> createAll() {
        return catalog.scenarios().stream().map(this::create).toList();
    }

    public ScenarioFixture create(String scenarioId) {
        return create(catalog.require(scenarioId));
    }

    public ScenarioFixture create(ScenarioDefinition definition) {
        return create(definition, null);
    }

    /** Builds a seeded parameterized fixture; null parameters preserve the frozen legacy fixtures. */
    public ScenarioFixture create(ScenarioDefinition definition, ExperimentScenarioParameters variant) {
        java.util.Objects.requireNonNull(definition, "definition");
        String suffix = definition.scenarioId().replace('-', '_');
        ProductId productId = new ProductId("SKU-" + suffix);
        CustomerOrderId orderId = new CustomerOrderId("SO-" + suffix);
        OrderLineId lineId = new OrderLineId("LINE-" + suffix);
        RecoveryObjectiveId objectiveId = new RecoveryObjectiveId("REC-" + suffix);
        InventoryLocationId locationId = new InventoryLocationId("WH-" + suffix);
        InventoryLotId primaryLotId = new InventoryLotId("LOT-A-" + suffix);
        InventoryLotId secondaryLotId = new InventoryLotId("LOT-B-" + suffix);

        FixtureProfile profile = FixtureProfile.from(definition.fixtureProfile());
        String expectedBudgetProfile = profile.budgetExhaustion()
                ? TIGHT_BUDGET_PROFILE : STANDARD_BUDGET_PROFILE;
        if (!expectedBudgetProfile.equals(definition.budgetProfile())) {
            throw new IllegalArgumentException("scenario " + definition.scenarioId() + " declares budget profile "
                    + definition.budgetProfile() + " but fixture profile " + profile.name()
                    + " requires " + expectedBudgetProfile);
        }
        String demandUnits = profile.requestedQuantity();
        if (variant != null) {
            java.math.BigDecimal demand = java.math.BigDecimal.valueOf(variant.demandQuantityUnits());
            if (profile.name().equals("DEMAND_SURGE")) {
                demand = demand.multiply(java.math.BigDecimal.valueOf(variant.demandSurgeMultiplierTenths(), 1));
            }
            demandUnits = demand.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        }
        String primaryStockUnits = profile.primaryStock();
        String secondaryStockUnits = profile.secondaryStock();
        if (variant != null && !profile.name().equals("NO_FEASIBLE_CANDIDATE")) {
            int inventoryRatioTenths = profile.name().equals("COMPETING_ALLOCATION")
                    ? Math.max(8, variant.inventoryCapacityRatioTenths()) : variant.inventoryCapacityRatioTenths();
            primaryStockUnits = scaleUnits(primaryStockUnits, inventoryRatioTenths);
            secondaryStockUnits = scaleUnits(secondaryStockUnits, inventoryRatioTenths);
        }
        Quantity requested = Quantity.of(demandUnits, UnitOfMeasure.EACH);
        Quantity primaryStock = Quantity.of(primaryStockUnits, UnitOfMeasure.EACH);
        Quantity secondaryStock = Quantity.of(secondaryStockUnits, UnitOfMeasure.EACH);
        LotQualityStatus primaryQuality = profile.primaryQuality();
        LotQualityStatus secondaryQuality = profile.secondaryQuality();
        Instant target = FIXTURE_AS_OF.plus(profile.targetDays(), java.time.temporal.ChronoUnit.DAYS);

        Product product = new Product(productId, "SKU-" + suffix, "M8 deterministic evaluation item", UnitOfMeasure.EACH, true);
        InventoryLocation location = new InventoryLocation(locationId, "WH-" + suffix, "Montreal", true);
        InventoryLot primaryLot = new InventoryLot(primaryLotId, productId, locationId, primaryStock,
                primaryQuality, FIXTURE_AS_OF.plus(Duration.ofDays(90)),
                primaryQuality == LotQualityStatus.QUALITY_HOLD ? FIXTURE_AS_OF : null,
                primaryQuality == LotQualityStatus.QUALITY_HOLD ? "M8 fixture quality alert" : null, 0);
        InventoryLot secondaryLot = new InventoryLot(secondaryLotId, productId, locationId, secondaryStock,
                secondaryQuality, FIXTURE_AS_OF.plus(Duration.ofDays(90)),
                secondaryQuality == LotQualityStatus.QUALITY_HOLD ? FIXTURE_AS_OF : null,
                secondaryQuality == LotQualityStatus.QUALITY_HOLD ? "M8 fixture quality alert" : null, 0);
        OrderLine line = new OrderLine(lineId, productId, requested, Quantity.zero(UnitOfMeasure.EACH));
        CustomerOrder order = new CustomerOrder(orderId, "CUSTOMER-M8", OrderPriority.HIGH, OrderStatus.OPEN,
                target, java.util.Map.of(lineId, line), 0);
        Set<RecoveryConstraint> constraints = Set.of(RecoveryConstraint.NO_UNQUALIFIED_SUBSTITUTION,
                RecoveryConstraint.NO_OVER_ALLOCATION,
                RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT);
        RecoveryObjective objective = new RecoveryObjective(objectiveId,
                "Restore fulfillment for " + orderId.value(), Set.of(orderId), target, constraints,
                RecoveryObjectiveStatus.ACTIVE, 0);
        SupplierId supplierId = new SupplierId("SUPPLIER-" + suffix);
        SupplierStatus supplierStatus = profile.name().equals("SUPPLIER_OUTAGE")
                ? SupplierStatus.SUSPENDED : SupplierStatus.ACTIVE;
        String supplierCapacity = switch (profile.name()) {
            case "SUPPLIER_DELAY" -> "5";
            case "SUPPLIER_OUTAGE" -> "0";
            case "COMPOUND_RESOURCE_PRESSURE" -> "8";
            default -> "40";
        };
        int supplierLeadDays = switch (profile.name()) {
            case "SUPPLIER_DELAY" -> 15;
            case "SUPPLIER_OUTAGE" -> 30;
            case "COMPOUND_RESOURCE_PRESSURE" -> 12;
            default -> 3;
        };
        if (variant != null) {
            supplierCapacity = profile.name().equals("SUPPLIER_OUTAGE")
                    ? "0" : scaleUnits(supplierCapacity, variant.supplierCapacityRatioTenths());
            supplierLeadDays = profile.name().equals("SUPPLIER_OUTAGE")
                    ? 30 : variant.supplierOrRouteDelayDays();
        }
        Supplier supplier = new Supplier(supplierId, "M8 deterministic supplier " + suffix, supplierStatus,
                java.util.Map.of(productId, new SupplierProductCapability(productId,
                        Quantity.of(supplierCapacity, UnitOfMeasure.EACH), Duration.ofDays(supplierLeadDays), true)), 0);

        SupplyChainSnapshot.Builder worldBuilder = SupplyChainSnapshot.builder(0, FIXTURE_AS_OF)
                .product(product).supplier(supplier).location(location).inventoryLot(primaryLot).inventoryLot(secondaryLot)
                .customerOrder(order).recoveryObjective(objective);
        disruptionFor(definition, profile).ifPresent(worldBuilder::disruption);

        if (profile.name().equals("COMPETING_ALLOCATION")) {
            int competingCount = variant == null ? 1 : variant.competingReservationCount();
            for (int index = 1; index <= competingCount; index++) {
                String competingSuffix = variant == null ? "" : "-" + index;
                CustomerOrderId competingOrderId = new CustomerOrderId("SO-COMPETING-" + suffix + competingSuffix);
                OrderLineId competingLineId = new OrderLineId("LINE-COMPETING-" + suffix + competingSuffix);
                CustomerOrder competingOrder = new CustomerOrder(competingOrderId,
                        variant == null ? "CUSTOMER-M8-COMPETING" : "CUSTOMER-M8-COMPETING-" + index,
                        OrderPriority.CRITICAL, OrderStatus.OPEN, target.plus(Duration.ofDays(1)),
                        java.util.Map.of(competingLineId, new OrderLine(competingLineId, productId,
                                Quantity.of("15", UnitOfMeasure.EACH), Quantity.zero(UnitOfMeasure.EACH))), 0);
                worldBuilder.customerOrder(competingOrder);
                String reservationUnits = variant == null ? "15" : "3";
                worldBuilder.reservation(new InventoryReservation(new ReservationId("RSV-COMPETING-" + suffix + competingSuffix),
                        competingOrderId, competingLineId, primaryLotId, productId, null,
                        Quantity.of(reservationUnits, UnitOfMeasure.EACH), Quantity.of(reservationUnits, UnitOfMeasure.EACH),
                        ReservationStatus.ACTIVE, FIXTURE_AS_OF.minus(Duration.ofMinutes(5))));
            }
        }

        if (profile.name().equals("BLOCKED_ROUTE")) {
            RouteId disruptedRouteId = new RouteId("ROUTE-BLOCKED-" + suffix);
            RouteId alternativeRouteId = new RouteId("ROUTE-ALT-" + suffix);
            CarrierId carrierId = new CarrierId("CARRIER-" + suffix);
            int delayDays = variant == null ? 2 : Math.max(1, variant.supplierOrRouteDelayDays());
            boolean alternativeAvailable = variant == null || variant.routeCapacityRatioTenths() > 0;
            worldBuilder.route(new Route(disruptedRouteId, "Quebec City", "Montreal", Duration.ofDays(delayDays), false));
            worldBuilder.route(new Route(alternativeRouteId, "Quebec City", "Montreal", Duration.ofDays(Math.max(1, delayDays / 2)), alternativeAvailable));
            worldBuilder.carrier(new Carrier(carrierId, "M8 deterministic carrier", Set.of(disruptedRouteId, alternativeRouteId), true));
            Quantity alreadyShipped = Quantity.of("10", UnitOfMeasure.EACH);
            worldBuilder.shipment(new Shipment(new ShipmentId("SHIPMENT-" + suffix), carrierId, disruptedRouteId,
                    List.of(new ShipmentLine(orderId, lineId, productId, null, alreadyShipped, alreadyShipped,
                            FIXTURE_AS_OF.minus(Duration.ofHours(2)))),
                    target.plus(Duration.ofDays(2)), null, ShipmentStatus.DELAYED, 0));
        }
        SupplyChainSnapshot world = worldBuilder.build();

        List<ActionDescriptor> actions = profile.readOnly() ? List.of() : List.of(new ActionDescriptor(
                RESERVE_INVENTORY, "Reserve inventory", "Reserve qualified available stock",
                ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true));
        var evidence = evidenceFor(definition);
        String executionId = "m8-eval:" + definition.scenarioId();
        var workingMemory = WorkingMemorySnapshot.empty(new MemoryScopeId("tenant:m8-eval:" + suffix),
                FIXTURE_AS_OF, FIXTURE_AS_OF.plus(Duration.ofDays(1)));
        int maxDepth = profile.budgetExhaustion() ? 1 : 3;
        int maxExpansions = profile.budgetExhaustion() ? 1 : 24;
        int maxEvaluations = profile.budgetExhaustion() ? 1 : 32;
        int maxRollouts = profile.budgetExhaustion() ? 1 : 12;
        if (variant != null && !profile.budgetExhaustion()) {
            maxDepth = scaleLimit(maxDepth, variant.budgetRemainingPercent());
            maxExpansions = scaleLimit(maxExpansions, variant.budgetRemainingPercent());
            maxEvaluations = scaleLimit(maxEvaluations, variant.budgetRemainingPercent());
            maxRollouts = scaleLimit(maxRollouts, variant.budgetRemainingPercent());
        }
        PlannerBudget budget = new PlannerBudget(maxDepth, maxExpansions, maxEvaluations, 0, maxRollouts,
                8, Duration.ofSeconds(3), FIXTURE_AS_OF.plusSeconds(3));
        PlanningRequest request = new PlanningRequest(executionId, objective, world, workingMemory, evidence,
                actions, constraints, PlanningScoringProfile.balancedCad("5000"),
                new PlanningRiskPolicy(0.85, true),
                new PlanningAuthorityPolicy("m8-evaluation-policy-v1",
                        profile.readOnly() ? Set.of() : Set.of(Capability.INVENTORY_RESERVE),
                        profile.readOnly() ? Set.of() : Set.of(Capability.INVENTORY_RESERVE)),
                new PlanningComplexity(variant != null && profile.name().equals("MULTI_ALTERNATIVE")
                        ? variant.multiAlternativeCount() : profile.branchingFactor(),
                        profile.horizon(), profile.uncertainty(), profile.severity()),
                budget, FIXTURE_AS_OF, null);

        String worldFingerprint = sha256(canonicalWorld(world));
        String fixtureFingerprint = sha256(definition.fixtureVersion() + "|" + definition.scenarioId() + "|"
                + request.fingerprint() + "|" + worldFingerprint + "|" + WORLD_MODEL_FINGERPRINT + "|" + definition.seed()
                + "|" + definition.budgetProfile() + (variant == null ? "" : "|variant=" + variant));
        return new ScenarioFixture(definition, request, worldFingerprint, fixtureFingerprint);
    }

    public PlanningWorldModel worldModel() {
        return new SimulatorBackedPlanningWorldModel(new SupplyChainSimulator(), this::availableActions,
                WORLD_MODEL_FINGERPRINT);
    }

    public DeterministicCandidateEvaluator evaluator(PlanningWorldModel worldModel) {
        return new DeterministicCandidateEvaluator(worldModel);
    }

    private List<PlanningAction> availableActions(PlanningRequest request, PlanningState state) {
        if (!request.actionDefinitionsById().containsKey(RESERVE_INVENTORY)) return List.of();
        var order = state.world().customerOrder(request.objective().affectedOrders().iterator().next());
        if (order.lines().size() != 1) throw new IllegalStateException("M8-B1 fixture expects one line per test order");
        var line = order.lines().values().iterator().next();
        Quantity alreadyCommitted = state.world().reservedFulfillmentQuantity(order.id(), line.id())
                .plus(state.world().committedShipmentFulfillmentQuantity(order.id(), line.id()));
        Quantity outstanding = line.requestedQuantity().minus(alreadyCommitted);
        if (outstanding.isZero()) return List.of();
        var actions = new ArrayList<PlanningAction>();
        for (InventoryLot lot : state.world().inventoryLots().values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.id().value())).toList()) {
            Quantity available = state.world().availableInventory(lot.id(), request.requestedAt());
            if (available.isZero() || !lot.productId().equals(line.productId())) continue;
            Quantity amount = available.compareTo(outstanding) <= 0 ? available : outstanding;
            String actionId = "reserve-" + lot.id().value() + "-v" + state.world().version();
            var recovery = new RecoveryAction.ReserveInventory(order.id(), line.id(), lot.id(), amount);
            var command = new SimulationCommand.ReserveInventory(actionId,
                    new dev.supplychain.agent.domain.inventory.ReservationId("RSV-" + actionId),
                    order.id(), line.id(), lot.id(), amount, request.requestedAt().plusSeconds(state.depth()));
            double progress = amount.value().divide(line.requestedQuantity().value(), MathContext.DECIMAL64).doubleValue();
            double cost = amount.value().multiply(new java.math.BigDecimal("1.25"), MathContext.DECIMAL64).doubleValue();
            var impact = new ActionImpactEstimate(progress, Money.of(java.math.BigDecimal.valueOf(cost).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), "CAD"),
                    Duration.ofHours(4 + state.depth()), 0.05, 0.90);
            actions.add(new PlanningAction(actionId, RESERVE_INVENTORY, "Reserve " + amount + " from " + lot.id(),
                    recovery, command, impact,
                    List.of(new EvidenceReference("inventory-lot", lot.id().value())), List.of()));
        }
        return List.copyOf(actions);
    }


    private static String scaleUnits(String baseUnits, int ratioTenths) {
        return new java.math.BigDecimal(baseUnits)
                .multiply(java.math.BigDecimal.valueOf(ratioTenths, 1))
                .setScale(1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static int scaleLimit(int base, int percent) {
        return Math.max(1, java.math.BigDecimal.valueOf(base)
                .multiply(java.math.BigDecimal.valueOf(percent))
                .divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP)
                .intValueExact());
    }

    private static Optional<Disruption> disruptionFor(ScenarioDefinition definition, FixtureProfile profile) {
        DisruptionType type = switch (profile.name()) {
            case "SUPPLIER_DELAY" -> DisruptionType.SUPPLIER_CAPACITY_REDUCTION;
            case "SUPPLIER_OUTAGE" -> DisruptionType.SUPPLIER_OUTAGE;
            case "STALE_CONFLICTING_EVIDENCE" -> DisruptionType.INVENTORY_CONFLICT;
            case "BLOCKED_ROUTE" -> DisruptionType.SHIPMENT_DELAY;
            case "QUALITY_HOLD" -> DisruptionType.QUALITY_HOLD;
            case "DEMAND_SURGE" -> DisruptionType.DEMAND_CHANGE;
            case "COMPOUND_RESOURCE_PRESSURE" -> DisruptionType.COMPOSITE;
            case "COMPETING_ALLOCATION", "BUDGET_EXHAUSTION", "NO_FEASIBLE_CANDIDATE" -> DisruptionType.INVENTORY_SHORTAGE;
            case "MULTI_ALTERNATIVE" -> DisruptionType.INVENTORY_SHORTAGE;
            case "REASSESS_EXISTING_PLAN" -> DisruptionType.INVENTORY_CONFLICT;
            case "STALE_APPROVAL", "APPROVAL_DENIED", "AUTHORIZED_WRITE_PROPOSAL", "READ_ONLY",
                    "UNKNOWN_EXTERNAL_OUTCOME", "PERSISTENT_INTENTION_RESTART", "STALE_OBSERVATION_RACE",
                    "DUPLICATE_DELIVERY", "MODEL_UNAVAILABLE" -> null;
            default -> throw new IllegalArgumentException("no disruption mapping for M8 profile: " + profile.name());
        };
        if (type == null) return Optional.empty();
        double severity = profile.severity();
        DisruptionSeverity level = severity >= 0.85 ? DisruptionSeverity.CRITICAL
                : severity >= 0.65 ? DisruptionSeverity.HIGH
                : severity >= 0.35 ? DisruptionSeverity.MEDIUM : DisruptionSeverity.LOW;
        return Optional.of(new Disruption(new DisruptionId("DISRUPTION-" + definition.scenarioId()), type, level,
                DisruptionStatus.OPEN, definition.scenario(), FIXTURE_AS_OF,
                Set.of("scenario:" + definition.scenarioId(), "fixture-profile:" + profile.name())));
    }

    private static List<EvidenceReference> evidenceFor(ScenarioDefinition definition) {
        var evidence = new LinkedHashSet<EvidenceReference>();
        evidence.add(new EvidenceReference("scenario-fixture", definition.scenarioId()));
        evidence.add(new EvidenceReference("inventory-snapshot", "snapshot:" + definition.scenarioId() + ":v0"));
        switch (definition.fixtureProfile()) {
            case "STALE_CONFLICTING_EVIDENCE" -> {
                evidence.add(new EvidenceReference("inventory-observation", "observation:stale:" + definition.scenarioId()));
                evidence.add(new EvidenceReference("inventory-observation", "observation:current:" + definition.scenarioId()));
            }
            case "SUPPLIER_DELAY", "SUPPLIER_OUTAGE" -> evidence.add(new EvidenceReference(
                    "supplier-status", "supplier:" + definition.scenarioId() + ":v0"));
            case "BLOCKED_ROUTE" -> {
                evidence.add(new EvidenceReference("shipment-status", "shipment:" + definition.scenarioId() + ":v0"));
                evidence.add(new EvidenceReference("route-status", "route:" + definition.scenarioId() + ":v0"));
            }
            case "QUALITY_HOLD" -> evidence.add(new EvidenceReference("quality-alert", "quality:" + definition.scenarioId() + ":v0"));
            case "DEMAND_SURGE" -> evidence.add(new EvidenceReference("demand-observation", "demand:" + definition.scenarioId() + ":v0"));
            default -> { }
        }
        return List.copyOf(evidence);
    }

    private static String canonicalWorld(SupplyChainSnapshot world) {
        StringBuilder out = new StringBuilder("SupplyChainSnapshot|")
                .append(world.version()).append("|capturedAt=").append(canonicalValue(world.capturedAt()));
        appendMap(out, "P", world.products());
        appendMap(out, "SUB", world.substitutions());
        appendMap(out, "S", world.suppliers());
        appendMap(out, "L", world.locations());
        appendMap(out, "I", world.inventoryLots());
        appendMap(out, "O", world.customerOrders());
        appendMap(out, "PO", world.purchaseOrders());
        appendMap(out, "C", world.carriers());
        appendMap(out, "RT", world.routes());
        appendMap(out, "SH", world.shipments());
        appendMap(out, "RS", world.reservations());
        appendMap(out, "D", world.disruptions());
        appendMap(out, "R", world.recoveryObjectives());
        appendMap(out, "RC", world.recoveryCandidates());
        return out.toString();
    }

    private static void appendMap(StringBuilder out, String label, java.util.Map<?, ?> values) {
        values.entrySet().stream()
                .map(entry -> java.util.Map.entry(canonicalValue(entry.getKey()), canonicalValue(entry.getValue())))
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> out.append('|').append(label).append(':')
                        .append(entry.getKey()).append('=').append(entry.getValue()));
    }

    /** Canonicalizes records recursively and sorts sets/maps so fingerprints are JVM-run independent. */
    private static String canonicalValue(Object value) {
        if (value == null) return "null";
        if (value instanceof java.util.Optional<?> optional) {
            return optional.map(item -> "Optional[" + canonicalValue(item) + "]").orElse("Optional.empty");
        }
        if (value instanceof java.util.Map<?, ?> map) {
            var entries = map.entrySet().stream()
                    .map(entry -> canonicalValue(entry.getKey()) + "=" + canonicalValue(entry.getValue()))
                    .sorted().toList();
            return "Map[" + String.join(",", entries) + "]";
        }
        if (value instanceof java.util.Set<?> set) {
            return "Set[" + set.stream().map(ScenarioFixtureFactory::canonicalValue).sorted()
                    .collect(java.util.stream.Collectors.joining(",")) + "]";
        }
        if (value instanceof java.util.Collection<?> collection) {
            return "Collection[" + collection.stream().map(ScenarioFixtureFactory::canonicalValue)
                    .collect(java.util.stream.Collectors.joining(",")) + "]";
        }
        if (value.getClass().isRecord()) {
            var components = new StringBuilder(value.getClass().getName()).append('[');
            try {
                for (var component : value.getClass().getRecordComponents()) {
                    components.append(component.getName()).append('=')
                            .append(canonicalValue(component.getAccessor().invoke(value))).append(';');
                }
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("could not canonicalize domain record " + value.getClass().getName(), ex);
            }
            return components.append(']').toString();
        }
        if (value instanceof Enum<?> enumeration) {
            return value.getClass().getName() + '#' + enumeration.name();
        }
        String text = String.valueOf(value);
        return value.getClass().getName() + '#' + text.length() + ':' + text;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record FixtureProfile(String name, String requestedQuantity, String primaryStock,
                                  String secondaryStock, LotQualityStatus primaryQuality,
                                  LotQualityStatus secondaryQuality, int targetDays,
                                  int branchingFactor, int horizon, double uncertainty,
                                  double severity, boolean readOnly, boolean budgetExhaustion) {
        static FixtureProfile from(String profile) {
            return switch (profile) {
                case "SUPPLIER_DELAY" -> standard(profile, "30", "20", "10", 6, 2, 3, 0.30, 0.60);
                case "SUPPLIER_OUTAGE" -> standard(profile, "30", "0", "10", 7, 2, 3, 0.60, 0.70);
                case "STALE_CONFLICTING_EVIDENCE" -> standard(profile, "25", "15", "10", 5, 2, 3, 0.85, 0.70);
                case "BLOCKED_ROUTE" -> standard(profile, "30", "15", "15", 6, 2, 4, 0.45, 0.70);
                case "QUALITY_HOLD" -> new FixtureProfile(profile, "20", "10", "15", LotQualityStatus.QUALITY_HOLD,
                        LotQualityStatus.AVAILABLE, 5, 2, 3, 0.25, 0.75, false, false);
                case "DEMAND_SURGE" -> standard(profile, "60", "20", "20", 5, 2, 4, 0.40, 0.75);
                case "MULTI_ALTERNATIVE" -> standard(profile, "30", "20", "20", 5, 3, 4, 0.20, 0.40);
                case "REASSESS_EXISTING_PLAN" -> standard(profile, "35", "20", "15", 5, 2, 4, 0.65, 0.70);
                case "AUTHORIZED_WRITE_PROPOSAL" -> standard(profile, "20", "20", "10", 5, 2, 2, 0.10, 0.80);
                case "READ_ONLY" -> new FixtureProfile(profile, "20", "20", "10", LotQualityStatus.AVAILABLE,
                        LotQualityStatus.AVAILABLE, 5, 0, 1, 0.10, 0.10, true, false);
                case "COMPOUND_RESOURCE_PRESSURE" -> standard(profile, "60", "15", "10", 3, 3, 5, 0.80, 0.95);
                case "STALE_APPROVAL", "APPROVAL_DENIED" -> standard(profile, "20", "20", "10", 5, 2, 3, 0.10, 0.90);
                case "COMPETING_ALLOCATION" -> standard(profile, "40", "20", "0", 5, 2, 3, 0.40, 0.90);
                case "UNKNOWN_EXTERNAL_OUTCOME" -> standard(profile, "20", "10", "10", 5, 2, 3, 0.30, 0.90);
                case "PERSISTENT_INTENTION_RESTART" -> standard(profile, "30", "15", "15", 5, 2, 4, 0.40, 0.70);
                case "BUDGET_EXHAUSTION" -> new FixtureProfile(profile, "60", "20", "20", LotQualityStatus.AVAILABLE,
                        LotQualityStatus.AVAILABLE, 4, 4, 5, 0.70, 0.90, false, true);
                case "NO_FEASIBLE_CANDIDATE" -> standard(profile, "40", "0", "0", 2, 2, 3, 0.80, 0.95);
                case "STALE_OBSERVATION_RACE", "DUPLICATE_DELIVERY", "MODEL_UNAVAILABLE" -> standard(profile,
                        "25", "15", "10", 5, 2, 3, 0.60, 0.80);
                default -> throw new IllegalArgumentException("unsupported M8 fixture profile: " + profile);
            };
        }

        private static FixtureProfile standard(String name, String demand, String first, String second,
                                               int days, int branching, int horizon, double uncertainty,
                                               double severity) {
            return new FixtureProfile(name, demand, first, second, LotQualityStatus.AVAILABLE,
                    LotQualityStatus.AVAILABLE, days, branching, horizon, uncertainty, severity, false, false);
        }
    }
}
