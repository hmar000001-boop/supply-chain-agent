package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSession;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.WorldTransition;
import dev.supplychain.agent.runtime.cognitive.decision.PlanningArtifactStore;
import java.util.List;
import java.util.Objects;

/** Replays the exact selected proposal against fresh simulator/evaluator instances, without dispatch. */
public final class IndependentSimulatorPlanVerifier {
    private final IndependentSimulatorReplayFactory replayFactory;
    public IndependentSimulatorPlanVerifier(IndependentSimulatorReplayFactory replayFactory) {
        this.replayFactory = Objects.requireNonNull(replayFactory, "replayFactory");
    }

    public SimulatorPlanOutcomeEvidence verify(FullGraphExperimentSession session, CognitiveCycleState state) throws Exception {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(state, "state");
        String candidateSetId = state.text(CognitiveCycleState.CANDIDATE_SET_ID);
        if (candidateSetId.isBlank()) return noPlan(session, "", "");
        PlanningArtifactStore.PlanningArtifacts artifacts = session.runtime().planningArtifactStore().find(candidateSetId)
                .orElseThrow(() -> new SecurityException("selected candidate set has no complete planner artifact"));
        if (!artifacts.executionId().equals(session.unit().fixture().planningRequest().executionId()))
            throw new SecurityException("planner artifact belongs to a different execution");
        String selectedId = state.text(CognitiveCycleState.SELECTED_CANDIDATE_ID);
        if (selectedId.isBlank()) return noPlan(session, candidateSetId, "");
        PlanCandidate candidate = artifacts.result().candidates().stream()
                .filter(value -> value.candidateId().equals(selectedId)).findFirst()
                .orElseThrow(() -> new SecurityException("graph selected a candidate absent from the planner artifact"));
        var replay = replayFactory.create(session);
        if (replay.worldModel() == session.ports().worldModel()
                || replay.candidateEvaluator() == session.ports().candidateEvaluator()
                || replay.worldModel() == replay.candidateEvaluator())
            throw new SecurityException("independent replay must use distinct simulator/evaluator instances");
        if (!replay.worldModel().configurationFingerprint().equals(session.ports().worldModel().configurationFingerprint())
                || !replay.candidateEvaluator().configurationFingerprint().equals(session.ports().candidateEvaluator().configurationFingerprint()))
            throw new SecurityException("independent replay implementation does not match the graph's frozen simulator/evaluator versions");
        var request = artifacts.request();
        PlanningState current = replay.worldModel().initialState(request);
        if (!current.actionPath().isEmpty()) throw new SecurityException("independent replay world did not start from the request root state");
        for (PlanningAction action : candidate.actions()) {
            boolean registeredInWorld = replay.worldModel().availableActions(request, current).stream()
                    .anyMatch(available -> sameAction(available, action));
            if (!registeredInWorld) throw new SecurityException("selected plan action is not available in independent replay state: " + action.planningActionId());
            WorldTransition transition = replay.worldModel().apply(request, current, action);
            if (!(transition instanceof WorldTransition.Applied applied)) {
                String reason = transition instanceof WorldTransition.Rejected rejected ? rejected.code() + ":" + rejected.reason() : "unknown";
                return SimulatorPlanOutcomeEvidence.create(session.arm(), session.mechanism(),
                        session.unit().effectiveInputFingerprint(), candidateSetId, selectedId,
                        replay.worldModel().configurationFingerprint(), replay.candidateEvaluator().configurationFingerprint(),
                        SimulatorPlanOutcomeEvidence.Status.REPLAY_REJECTED, 0, false, 0, 0, 1, 0, List.of(reason));
            }
            current = applied.state();
        }
        if (!current.actionPath().equals(candidate.actions()) || current.depth() != candidate.actions().size())
            throw new SecurityException("independent replay action path differs from the exact selected proposal");
        var evaluation = replay.candidateEvaluator().evaluate(request, current);
        if (!evaluation.equals(candidate.evaluation()))
            throw new SecurityException("independent replay evaluation does not reproduce the planner's stored evaluation");
        return SimulatorPlanOutcomeEvidence.create(session.arm(), session.mechanism(),
                session.unit().effectiveInputFingerprint(), candidateSetId, selectedId,
                replay.worldModel().configurationFingerprint(), replay.candidateEvaluator().configurationFingerprint(),
                SimulatorPlanOutcomeEvidence.Status.VERIFIED_SIMULATOR_REPLAY, current.depth(), evaluation.feasible(),
                evaluation.score(), evaluation.expectedFulfillmentProgress(), evaluation.aggregateRisk(),
                evaluation.robustness(), evaluation.violations());
    }

    private static boolean sameAction(PlanningAction left, PlanningAction right) {
        return left.planningActionId().equals(right.planningActionId())
                && left.descriptorId().equals(right.descriptorId())
                && left.simulationCommand().equals(right.simulationCommand());
    }
    private static SimulatorPlanOutcomeEvidence noPlan(FullGraphExperimentSession session, String candidateSet, String candidate) {
        return SimulatorPlanOutcomeEvidence.create(session.arm(), session.mechanism(),
                session.unit().effectiveInputFingerprint(), candidateSet, candidate,
                session.ports().worldModel().configurationFingerprint(),
                session.ports().candidateEvaluator().configurationFingerprint(),
                SimulatorPlanOutcomeEvidence.Status.NO_SELECTED_PLAN, 0, false, 0, 0, 0, 0, List.of());
    }
}
