package dev.supplychain.agent.planning;

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
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
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
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.planning.api.ActionImpactEstimate;
import dev.supplychain.agent.planning.api.B6PlannerResourceMeter;
import dev.supplychain.agent.planning.api.PlannerBudget;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningActionProvider;
import dev.supplychain.agent.planning.api.PlanningAuthorityPolicy;
import dev.supplychain.agent.planning.api.PlanningComplexity;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningRiskPolicy;
import dev.supplychain.agent.planning.api.PlanningScoringProfile;
import dev.supplychain.agent.planning.api.SimulatorBackedPlanningWorldModel;
import dev.supplychain.agent.planning.api.DeterministicCandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.PlannerResourceMeter;
import dev.supplychain.agent.planning.session.InMemoryPlannerSessionStore;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanningTestFixtures {
    public static final Instant NOW = Instant.parse("2026-11-17T14:00:00Z");
    public static final ActionId RESERVE_ID = new ActionId("supply-chain.grounding.inventory.reserve");
    public static final ProductId PRODUCT = new ProductId("SKU-PLAN");
    public static final CustomerOrderId ORDER = new CustomerOrderId("SO-PLAN");
    public static final OrderLineId LINE = new OrderLineId("SO-PLAN-1");
    public static final InventoryLotId LOT_A = new InventoryLotId("LOT-A");
    public static final InventoryLotId LOT_B = new InventoryLotId("LOT-B");
    public static final RecoveryObjectiveId OBJECTIVE = new RecoveryObjectiveId("REC-PLAN");

    private PlanningTestFixtures() { }

    public static Quantity q(String value) { return Quantity.of(value, UnitOfMeasure.EACH); }

    public static PlanningRequest request(String executionId, PlannerBudget budget) {
        Product product = new Product(PRODUCT, "SKU-PLAN", "Planning valve", UnitOfMeasure.EACH, true);
        InventoryLocation location = new InventoryLocation(new InventoryLocationId("WH-PLAN"), "WH-PLAN", "Montreal", true);
        InventoryLot lotA = new InventoryLot(LOT_A, PRODUCT, location.id(), q("10"), LotQualityStatus.AVAILABLE,
                NOW.plus(Duration.ofDays(60)), null, null, 0);
        InventoryLot lotB = new InventoryLot(LOT_B, PRODUCT, location.id(), q("10"), LotQualityStatus.AVAILABLE,
                NOW.plus(Duration.ofDays(60)), null, null, 0);
        OrderLine line = new OrderLine(LINE, PRODUCT, q("20"), q("0"));
        CustomerOrder order = new CustomerOrder(ORDER, "CUSTOMER-PLAN", OrderPriority.HIGH,
                OrderStatus.OPEN, NOW.plus(Duration.ofDays(5)), Map.of(LINE, line), 0);
        var constraints = Set.of(RecoveryConstraint.NO_UNQUALIFIED_SUBSTITUTION,
                RecoveryConstraint.NO_OVER_ALLOCATION, RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT);
        RecoveryObjective objective = new RecoveryObjective(OBJECTIVE, "Restore SO-PLAN fulfillment", Set.of(ORDER),
                NOW.plus(Duration.ofDays(5)), constraints, RecoveryObjectiveStatus.ACTIVE, 0);
        SupplyChainSnapshot world = SupplyChainSnapshot.builder(0, NOW)
                .product(product).location(location).inventoryLot(lotA).inventoryLot(lotB)
                .customerOrder(order).recoveryObjective(objective).build();
        var actionDescriptor = new ActionDescriptor(RESERVE_ID, "Reserve inventory", "Reserve qualified available stock",
                ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true);
        return new PlanningRequest(executionId, objective, world,
                WorkingMemorySnapshot.empty(new MemoryScopeId("scope-" + executionId), NOW, NOW.plus(Duration.ofDays(1))),
                List.of(new EvidenceReference("inventory-snapshot", "SNAP-0")), List.of(actionDescriptor), constraints,
                PlanningScoringProfile.balancedCad("1000"), new PlanningRiskPolicy(0.80, true),
                new PlanningAuthorityPolicy("approval-policy-v1", Set.of(Capability.INVENTORY_RESERVE), Set.of(Capability.INVENTORY_RESERVE)),
                new PlanningComplexity(2, 2, 0.10, 0.30), budget, NOW, null);
    }

    public static PlanningRequest withBudgetAndResume(PlanningRequest original, PlannerBudget budget, String sessionId) {
        return new PlanningRequest(original.executionId(), original.objective(), original.initialWorld(), original.workingMemory(),
                original.evidence(), original.availableActionDefinitions(), original.hardConstraints(), original.scoringProfile(),
                original.riskPolicy(), original.authorityPolicy(), original.complexity(), budget, original.requestedAt(), sessionId,
                original.uncertaintyAssessmentReference());
    }

    public static PlannerBudget budget(int expansions, int evaluations, int modelCalls, int rollouts, int maxDepth) {
        return new PlannerBudget(maxDepth, expansions, evaluations, modelCalls, rollouts, 10,
                Duration.ofMinutes(2), NOW.plus(Duration.ofMinutes(2)));
    }

    public static PlanningActionProvider actionProvider() {
        return (request, state) -> {
            var result = new ArrayList<PlanningAction>();
            var order = state.world().customerOrder(ORDER);
            var line = order.lines().get(LINE);
            Quantity committed = state.world().reservedFulfillmentQuantity(ORDER, LINE)
                    .plus(state.world().committedShipmentFulfillmentQuantity(ORDER, LINE));
            Quantity outstanding = line.requestedQuantity().minus(committed);
            if (outstanding.isZero()) return List.of();
            for (InventoryLotId lotId : List.of(LOT_A, LOT_B)) {
                Quantity available = state.world().availableInventory(lotId, request.requestedAt());
                if (available.isZero()) continue;
                Quantity amount = available.compareTo(outstanding) <= 0 ? available : outstanding;
                String actionId = "reserve-" + lotId.value() + "-v" + state.world().version();
                var recovery = new RecoveryAction.ReserveInventory(ORDER, LINE, lotId, amount);
                var command = new SimulationCommand.ReserveInventory(actionId,
                        new dev.supplychain.agent.domain.inventory.ReservationId("RSV-" + actionId),
                        ORDER, LINE, lotId, amount, request.requestedAt().plusSeconds(state.depth()));
                boolean cheaper = lotId.equals(LOT_A);
                var impact = new ActionImpactEstimate(amount.value().divide(line.requestedQuantity().value(), java.math.MathContext.DECIMAL64).doubleValue(),
                        Money.of(cheaper ? "10" : "15", "CAD"), Duration.ofHours(cheaper ? 6 : 8),
                        cheaper ? 0.05 : 0.03, cheaper ? 0.90 : 0.95);
                result.add(new PlanningAction(actionId, RESERVE_ID, "Reserve " + amount + " from " + lotId,
                        recovery, command, impact,
                        List.of(new EvidenceReference("inventory-lot", lotId.value())), List.of()));
            }
            return List.copyOf(result);
        };
    }

    public static PlanningWorldModel worldModel() {
        return new SimulatorBackedPlanningWorldModel(new SupplyChainSimulator(), actionProvider(),
                "supply-chain-simulator-v1|fixture-action-proposer-v1");
    }

    public static DeterministicCandidateEvaluator evaluator(PlanningWorldModel model) {
        return new DeterministicCandidateEvaluator(model);
    }

    public static InMemoryResourceBudgetLedger ledger(String executionId, PlannerBudget budget) {
        var limits = new EnumMap<ResourceUnit, Long>(ResourceUnit.class);
        limits.put(ResourceUnit.MODEL_CALLS, Math.max(100, budget.maxModelCalls() + 10L));
        limits.put(ResourceUnit.REASONING_EVALUATIONS, Math.max(1000, budget.maxEvaluations() + 20L));
        limits.put(ResourceUnit.SEARCH_EXPANSIONS, Math.max(1000, budget.maxExpansions() + 20L));
        limits.put(ResourceUnit.MCTS_ROLLOUTS, Math.max(1000, budget.maxRollouts() + 20L));
        var ledger = new InMemoryResourceBudgetLedger();
        ledger.register(executionId, new ResourceBudget(limits, budget.deadline()));
        return ledger;
    }

    public static PlannerResourceMeter meter(InMemoryResourceBudgetLedger ledger) {
        return new B6PlannerResourceMeter(ledger);
    }
    public static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    public static InMemoryPlannerSessionStore sessionStore() { return new InMemoryPlannerSessionStore(); }
}
