package dev.supplychain.agent.runtime.cognitive.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionStatus;
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
import dev.supplychain.agent.domain.inventory.ReservationId;
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
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanEvaluation;
import dev.supplychain.agent.planning.api.PlannerBudget;
import dev.supplychain.agent.planning.api.PlanningAuthorityPolicy;
import dev.supplychain.agent.planning.api.PlanningComplexity;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningRiskPolicy;
import dev.supplychain.agent.planning.api.PlanningScoringProfile;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ActionExecutionBatchStoreTest {
    private static final Instant NOW = Instant.parse("2026-11-17T22:00:00Z");

    @Test
    void batchBindsToAuthenticatedPrincipalAndSupportsIdempotentStepCompletion() {
        AuthorizedActionPlan plan = plan();
        InMemoryActionExecutionBatchStore store = new InMemoryActionExecutionBatchStore();

        ActionExecutionBatch started = store.start(plan, "batch:m6b", "operator-a", NOW);
        assertEquals("operator-a", started.principalId());
        assertEquals(started, store.start(plan, "batch:m6b", "operator-a", NOW));
        assertThrows(SecurityException.class, () -> store.start(plan, "batch:m6b", "operator-b", NOW));

        var item = new ActionExecutionBatch.ActionExecutionItem(0,
                "supply-chain.grounding.inventory.reserve", ActionStatus.SUCCEEDED,
                "operation:m6b", "reservation:m6b", "", "", false);
        store.append("batch:m6b", item, ActionExecutionBatch.BatchStatus.STARTED, NOW.plusSeconds(1));
        store.append("batch:m6b", item, ActionExecutionBatch.BatchStatus.STARTED, NOW.plusSeconds(2));
        ActionExecutionBatch completed = store.complete("batch:m6b", NOW.plusSeconds(3));

        assertEquals(ActionExecutionBatch.BatchStatus.SUCCEEDED, completed.status());
        assertEquals(1, completed.items().size());
        assertThrows(IllegalStateException.class, () -> store.append("batch:m6b",
                new ActionExecutionBatch.ActionExecutionItem(1, "another-action", ActionStatus.FAILED,
                        "", "", "FAILED", "stop", false), ActionExecutionBatch.BatchStatus.FAILED, NOW.plusSeconds(4)));
    }

    private static AuthorizedActionPlan plan() {
        ProductId productId = new ProductId("SKU-M6B");
        InventoryLocationId locationId = new InventoryLocationId("WH-M6B");
        InventoryLotId lotId = new InventoryLotId("LOT-M6B");
        CustomerOrderId orderId = new CustomerOrderId("ORDER-M6B");
        OrderLineId lineId = new OrderLineId("LINE-M6B");
        RecoveryObjectiveId objectiveId = new RecoveryObjectiveId("OBJECTIVE-M6B");
        Quantity quantity = Quantity.of("10", UnitOfMeasure.EACH);
        var objective = new RecoveryObjective(objectiveId, "Restore test order", Set.of(orderId),
                NOW.plus(Duration.ofDays(2)), Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,
                RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT), RecoveryObjectiveStatus.ACTIVE, 0);
        var order = new CustomerOrder(orderId, "CUSTOMER-M6B", OrderPriority.NORMAL, OrderStatus.OPEN,
                NOW.plus(Duration.ofDays(3)), Map.of(lineId, new OrderLine(lineId, productId, quantity,
                Quantity.zero(UnitOfMeasure.EACH))), 0);
        var world = SupplyChainSnapshot.builder(0, NOW)
                .product(new Product(productId, "SKU-M6B", "M6-B product", UnitOfMeasure.EACH, true))
                .location(new InventoryLocation(locationId, "WH-M6B", "Montreal", true))
                .inventoryLot(new InventoryLot(lotId, productId, locationId, quantity, LotQualityStatus.AVAILABLE,
                        NOW.plus(Duration.ofDays(30)), null, null, 0))
                .customerOrder(order).recoveryObjective(objective).build();
        ActionId actionId = new ActionId("supply-chain.grounding.inventory.reserve");
        String planningActionId = "reserve-LOT-M6B-v0";
        var recoveryAction = new RecoveryAction.ReserveInventory(orderId, lineId, lotId, quantity);
        var simulationCommand = new SimulationCommand.ReserveInventory(planningActionId,
                new ReservationId("RSV-M6B"), orderId, lineId, lotId, quantity, NOW.plusSeconds(1));
        var planningAction = new PlanningAction(planningActionId, actionId, "Reserve available inventory",
                recoveryAction, simulationCommand,
                new ActionImpactEstimate(1.0, Money.of("10", "CAD"), Duration.ofHours(1), 0.05, 0.90),
                List.of(new EvidenceReference("inventory-lot", lotId.value())), List.of());
        var evaluation = new PlanEvaluation(true, 0.9, 1.0, Money.of("10", "CAD"), Duration.ofHours(1),
                0.05, 0.9, NOW.plus(Duration.ofHours(1)), false, true, List.of());
        var candidate = new PlanCandidate("candidate:m6b", objectiveId, List.of(planningAction),
                List.of(new EvidenceReference("inventory-lot", lotId.value())), List.of(),
                "Reserve qualified available stock", evaluation);
        var descriptor = new ActionDescriptor(actionId, "Reserve inventory", "Reserve stock",
                ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true);
        var request = new PlanningRequest("execution:m6b", objective, world,
                WorkingMemorySnapshot.empty(new MemoryScopeId("tenant:m6b"), NOW, NOW.plus(Duration.ofHours(1))),
                List.of(new EvidenceReference("evidence-set", "evidence:m6b")), List.of(descriptor),
                objective.hardConstraints(), PlanningScoringProfile.balancedCad("1000"),
                new PlanningRiskPolicy(0.8, true),
                new PlanningAuthorityPolicy("policy:m6b", Set.of(Capability.INVENTORY_RESERVE), Set.of(Capability.INVENTORY_RESERVE)),
                new PlanningComplexity(1, 1, 0.1, 0.1),
                new PlannerBudget(2, 4, 4, 0, 0, 4, Duration.ofSeconds(30), NOW.plusSeconds(30)), NOW, null);
        var command = new ActionDispatchCommand<>(planningActionId, actionId, String.class, String.class, "LOT-M6B:10");
        return new AuthorizedActionPlan("execution:m6b", "candidate-set:m6b", candidate.candidateId(),
                "intention:m6b", 2, "fingerprint:m6b", candidate, request,
                List.of(command), List.of(descriptor), true);
    }
}
