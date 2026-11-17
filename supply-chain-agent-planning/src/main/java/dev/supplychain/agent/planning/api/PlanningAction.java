package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.domain.recovery.RecoveryAction;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import java.util.List;
import java.util.Objects;

/** A proposed action plus the deterministic simulator transition used only for planning. */
public record PlanningAction(String planningActionId, ActionId descriptorId, String description,
                             RecoveryAction recoveryAction, SimulationCommand simulationCommand,
                             ActionImpactEstimate impact, List<EvidenceReference> supportingEvidence,
                             List<String> assumptions) {
    public PlanningAction {
        planningActionId = require(planningActionId, "planningActionId");
        descriptorId = Objects.requireNonNull(descriptorId, "descriptorId");
        description = require(description, "description");
        recoveryAction = Objects.requireNonNull(recoveryAction, "recoveryAction");
        simulationCommand = Objects.requireNonNull(simulationCommand, "simulationCommand");
        if (!planningActionId.equals(simulationCommand.commandId())) {
            throw new IllegalArgumentException("planningActionId must equal simulator commandId");
        }
        impact = Objects.requireNonNull(impact, "impact");
        supportingEvidence = List.copyOf(Objects.requireNonNull(supportingEvidence, "supportingEvidence"));
        assumptions = Objects.requireNonNull(assumptions, "assumptions").stream().map(v -> require(v, "assumption")).distinct().toList();
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
