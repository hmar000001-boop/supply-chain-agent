package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.domain.common.DomainViolation;
import dev.supplychain.agent.domain.recovery.RecoveryAction;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Applies candidate transitions only through the deterministic M1 simulator. */
public final class SimulatorBackedPlanningWorldModel implements PlanningWorldModel {
    private final SupplyChainSimulator simulator;
    private final PlanningActionProvider actionProvider;
    private final String configurationFingerprint;

    public SimulatorBackedPlanningWorldModel(SupplyChainSimulator simulator,
                                             PlanningActionProvider actionProvider,
                                             String configurationFingerprint) {
        this.simulator = Objects.requireNonNull(simulator, "simulator");
        this.actionProvider = Objects.requireNonNull(actionProvider, "actionProvider");
        this.configurationFingerprint = require(configurationFingerprint);
    }

    @Override public String configurationFingerprint() { return configurationFingerprint; }

    private static String require(String value) {
        String result = Objects.requireNonNull(value, "configurationFingerprint").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("configurationFingerprint cannot be blank");
        return result;
    }

    @Override
    public PlanningState initialState(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        return new PlanningState(request.objective().id(), request.initialWorld().version(),
                request.initialWorld(), List.of(), request.evidence());
    }

    @Override
    public List<PlanningAction> availableActions(PlanningRequest request, PlanningState state) {
        requireState(request, state);
        var descriptors = request.actionDefinitionsById();
        List<PlanningAction> actions = List.copyOf(Objects.requireNonNull(
                actionProvider.availableActions(request, state), "actionProvider result"));
        var ids = new HashSet<String>();
        for (PlanningAction action : actions) {
            if (!ids.add(action.planningActionId())) throw new IllegalStateException("action provider returned duplicate planningActionId: " + action.planningActionId());
            validateAllowedAction(request, descriptors, action);
        }
        return actions.stream().sorted(Comparator
                .comparing((PlanningAction action) -> request.isReusableRecoveryAction(action)).reversed()
                .thenComparing(PlanningAction::planningActionId)).toList();
    }

    @Override
    public WorldTransition apply(PlanningRequest request, PlanningState state, PlanningAction action) {
        requireState(request, state);
        Objects.requireNonNull(action, "action");
        validateAllowedAction(request, request.actionDefinitionsById(), action);
        try {
            var result = simulator.simulate(state.world(), action.simulationCommand());
            PlanningState next = state.advance(result.snapshot(), action);
            return new WorldTransition.Applied(next);
        } catch (DomainViolation | IllegalArgumentException ex) {
            return new WorldTransition.Rejected("DOMAIN_TRANSITION_REJECTED", ex.getMessage() == null ? "domain rejected the transition" : ex.getMessage());
        }
    }

    private static void validateAllowedAction(PlanningRequest request,
            java.util.Map<ActionId, ActionDescriptor> descriptors, PlanningAction action) {
        ActionDescriptor descriptor = descriptors.get(action.descriptorId());
        if (descriptor == null) throw new IllegalArgumentException("action is not in the request's available action definitions: " + action.descriptorId());
        if (!request.authorityPolicy().proposalScope().contains(descriptor.requiredCapability())) {
            throw new IllegalArgumentException("action exceeds the trusted proposal scope: " + descriptor.requiredCapability());
        }
        if (!matches(action.recoveryAction(), action.simulationCommand())) {
            throw new IllegalArgumentException("recovery action and simulator command do not describe the same operation: " + action.planningActionId());
        }
    }

    private static boolean matches(RecoveryAction action, dev.supplychain.agent.simulator.command.SimulationCommand command) {
        if (action instanceof RecoveryAction.ReserveInventory a && command instanceof dev.supplychain.agent.simulator.command.SimulationCommand.ReserveInventory c) {
            return a.orderId().equals(c.orderId()) && a.orderLineId().equals(c.orderLineId())
                    && a.lotId().equals(c.lotId()) && a.fulfillmentQuantity().equals(c.fulfillmentQuantity());
        }
        if (action instanceof RecoveryAction.PlacePurchaseOrder a && command instanceof dev.supplychain.agent.simulator.command.SimulationCommand.PlacePurchaseOrder c) {
            return a.purchaseOrderId().equals(c.purchaseOrder().id());
        }
        if (action instanceof RecoveryAction.RerouteShipment a && command instanceof dev.supplychain.agent.simulator.command.SimulationCommand.RerouteShipment c) {
            return a.shipmentId().equals(c.shipmentId()) && a.alternativeRouteId().equals(c.newRouteId());
        }
        return false;
    }

    private static void requireState(PlanningRequest request, PlanningState state) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(state, "state");
        if (!request.objective().id().equals(state.objectiveId())) throw new IllegalArgumentException("planning state belongs to another objective");
        if (state.rootWorldVersion() != request.initialWorld().version()) throw new IllegalArgumentException("planning state has a different root world version");
    }
}
