package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.memory.common.EvidenceReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Search state: authoritative simulated world plus the immutable path of proposed actions. */
public record PlanningState(RecoveryObjectiveId objectiveId, long rootWorldVersion,
                            SupplyChainSnapshot world, List<PlanningAction> actionPath,
                            List<EvidenceReference> evidence) {
    public PlanningState {
        objectiveId = Objects.requireNonNull(objectiveId, "objectiveId");
        if (rootWorldVersion < 0) throw new IllegalArgumentException("rootWorldVersion cannot be negative");
        world = Objects.requireNonNull(world, "world");
        actionPath = List.copyOf(Objects.requireNonNull(actionPath, "actionPath"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (world.version() != rootWorldVersion + actionPath.size()) {
            throw new IllegalArgumentException("world version must advance exactly once for each action in the path");
        }
        if (actionPath.stream().map(PlanningAction::planningActionId).distinct().count() != actionPath.size()) {
            throw new IllegalArgumentException("an action identifier cannot occur twice in one plan path");
        }
    }
    public int depth() { return actionPath.size(); }
    public PlanningState withAdditionalEvidence(List<EvidenceReference> additional) {
        var refs = new LinkedHashSet<>(evidence);
        refs.addAll(Objects.requireNonNull(additional, "additional"));
        return new PlanningState(objectiveId, rootWorldVersion, world, actionPath, List.copyOf(refs));
    }
    public PlanningState advance(SupplyChainSnapshot nextWorld, PlanningAction action) {
        var actions = new ArrayList<>(actionPath);
        actions.add(Objects.requireNonNull(action, "action"));
        var refs = new LinkedHashSet<>(evidence);
        refs.addAll(action.supportingEvidence());
        return new PlanningState(objectiveId, rootWorldVersion, nextWorld, actions, List.copyOf(refs));
    }
}
