package dev.supplychain.agent.planning.session;

import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.WorldTransition;
import java.util.UUID;

public final class PlannerSessionSupport {
    private PlannerSessionSupport() { }

    public static String sessionId(PlanningRequest request) {
        return request.resumeSessionId() == null ? UUID.randomUUID().toString() : request.resumeSessionId();
    }

    public static PlannerSessionCheckpoint loadIfRequested(PlanningRequest request, PlannerId plannerId,
                                                             String algorithmConfigurationFingerprint,
                                                             PlannerSessionStore store) {
        if (request.resumeSessionId() == null) return null;
        PlannerSessionCheckpoint checkpoint = store.find(request.resumeSessionId())
                .orElseThrow(() -> new PlannerCheckpointConflictException("planner session does not exist: " + request.resumeSessionId()));
        if (checkpoint.plannerId() != plannerId) {
            throw new PlannerCheckpointConflictException("planner session belongs to " + checkpoint.plannerId());
        }
        if (!checkpoint.requestFingerprint().equals(request.fingerprint())) {
            throw new PlannerCheckpointConflictException("planning request differs from the saved session fingerprint");
        }
        if (!checkpoint.algorithmConfigurationFingerprint().equals(algorithmConfigurationFingerprint)) {
            throw new PlannerCheckpointConflictException("planner configuration differs from the saved session");
        }
        if (checkpoint.status() == PlannerSessionStatus.COMPLETED) {
            throw new PlannerCheckpointConflictException("planner session is already completed");
        }
        return checkpoint;
    }

    public static PlanningState replayPath(PlanningRequest request, PlanningWorldModel worldModel,
                                           java.util.List<String> actionIds) {
        PlanningState state = worldModel.initialState(request);
        for (String actionId : actionIds) {
            var available = worldModel.availableActions(request, state);
            var action = available.stream().filter(candidate -> candidate.planningActionId().equals(actionId)).findFirst()
                    .orElseThrow(() -> new PlannerCheckpointConflictException(
                            "saved search path is no longer available during replay: " + actionId));
            WorldTransition transition = worldModel.apply(request, state, action);
            if (transition instanceof WorldTransition.Rejected rejected) {
                throw new PlannerCheckpointConflictException("saved search path no longer replays: "
                        + rejected.code() + " " + rejected.reason());
            }
            state = ((WorldTransition.Applied) transition).state();
        }
        return state;
    }

    public static long save(PlannerSessionStore store, PlannerSessionCheckpoint checkpoint, long expectedRevision) {
        store.save(checkpoint, expectedRevision);
        return checkpoint.revision();
    }
}
