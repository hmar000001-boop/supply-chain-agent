package dev.supplychain.agent.evaluation.planner;

import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanCandidateFactory;
import dev.supplychain.agent.planning.api.PlanEvaluation;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlannerTraceNode;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningStatistics;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.WorldTransition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Evaluation-only one-step greedy baseline. It expands every currently available action,
 * rejects any transition failing the shared evaluator, then selects the highest-scoring feasible
 * successor with a stable action-ID tie break. It is never registered in the production router.
 */
public final class GreedyDeterministicComparator implements EvaluationPlanner {
    public static final String ID = "GREEDY_DETERMINISTIC";

    private final PlanningWorldModel worldModel;
    private final CandidateEvaluator evaluator;
    private final Clock clock;

    public GreedyDeterministicComparator(PlanningWorldModel worldModel, CandidateEvaluator evaluator, Clock clock) {
        this.worldModel = Objects.requireNonNull(worldModel, "worldModel");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public String id() { return ID; }

    @Override
    public EvaluationPlannerResult plan(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        PlanningState state = worldModel.initialState(request);
        var trace = new ArrayList<PlannerTraceNode>();
        long expansions = 0;
        long evaluations = 0;
        long pruned = 0;
        PlannerTerminationReason termination = PlannerTerminationReason.COMPLETED;
        String parentId = null;
        PlanEvaluation finalEvaluation = null;

        for (int depth = 0; depth < request.budget().maxDepth(); depth++) {
            if (deadlineExceeded(request, startedAt, startedNanos)) {
                termination = PlannerTerminationReason.DEADLINE_EXCEEDED;
                break;
            }
            List<PlanningAction> actions = worldModel.availableActions(request, state);
            if (actions.isEmpty()) {
                termination = state.depth() == 0
                        ? PlannerTerminationReason.NO_AVAILABLE_ACTIONS
                        : PlannerTerminationReason.COMPLETED;
                break;
            }
            PlanningState bestState = null;
            PlanningAction bestAction = null;
            double bestScore = -1.0;
            PlanEvaluation bestEvaluation = null;
            for (PlanningAction action : actions.stream()
                    .sorted(Comparator.comparing(PlanningAction::planningActionId)).toList()) {
                if (deadlineExceeded(request, startedAt, startedNanos)) {
                    termination = PlannerTerminationReason.DEADLINE_EXCEEDED;
                    break;
                }
                if (expansions >= request.budget().maxExpansions()
                        || evaluations >= request.budget().maxEvaluations()) {
                    termination = PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED;
                    break;
                }
                expansions++;
                WorldTransition transition = worldModel.apply(request, state, action);
                if (!(transition instanceof WorldTransition.Applied applied)) {
                    pruned++;
                    continue;
                }
                evaluations++;
                var candidateState = applied.state();
                var score = evaluator.evaluate(request, candidateState);
                String nodeId = PlanCandidateFactory.stateNodeId(request, candidateState.actionPath());
                trace.add(new PlannerTraceNode(nodeId, parentId, action.planningActionId(), candidateState.depth(),
                        score.score(), 0, 0.0, score.feasible() ? "feasible-successor" : "hard-constraint-rejected", List.of()));
                if (!score.feasible()) {
                    pruned++;
                    continue;
                }
                if (score.score() > bestScore || (Double.compare(score.score(), bestScore) == 0
                        && (bestAction == null || action.planningActionId().compareTo(bestAction.planningActionId()) < 0))) {
                    bestState = candidateState;
                    bestAction = action;
                    bestScore = score.score();
                    bestEvaluation = score;
                }
            }
            if (termination == PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED
                    || termination == PlannerTerminationReason.RESOURCE_BUDGET_EXHAUSTED
                    || termination == PlannerTerminationReason.DEADLINE_EXCEEDED) break;
            if (bestState == null) {
                termination = PlannerTerminationReason.NO_FEASIBLE_CANDIDATES;
                break;
            }
            // Keep the evaluation paired with the exact state committed as the current greedy path.
            // If a later depth is interrupted, the last fully committed path remains a valid proposal.
            state = bestState;
            finalEvaluation = bestEvaluation;
            parentId = PlanCandidateFactory.stateNodeId(request, state.actionPath());
            if (state.depth() == request.budget().maxDepth()) termination = PlannerTerminationReason.MAX_DEPTH_REACHED;
        }

        List<PlanCandidate> candidates = List.of();
        if (!state.actionPath().isEmpty()) {
            var evaluation = finalEvaluation;
            if (evaluation != null && evaluation.feasible()) {
                candidates = List.of(PlanCandidateFactory.create(request, state, evaluation,
                        "Deterministic greedy baseline: selected the highest-scoring feasible one-step successor at each depth; ties are broken by action ID.",
                        List.of("Greedy local choice; no lookahead or backtracking beyond the selected path.")));
            } else if (termination == PlannerTerminationReason.COMPLETED) {
                termination = PlannerTerminationReason.NO_FEASIBLE_CANDIDATES;
            }
        }
        Duration elapsed = Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos));
        var statistics = new PlanningStatistics(expansions, evaluations, 0, 0, pruned, 0, elapsed);
        return new EvaluationPlannerResult(ID, null, candidates, termination, statistics,
                java.util.Map.of("SEARCH_EXPANSIONS", expansions, "REASONING_EVALUATIONS", evaluations),
                "Evaluation-only deterministic greedy baseline; all transitions use the same simulator and hard-constraint evaluator. No execution or dispatch is available.", trace);
    }

    private boolean deadlineExceeded(PlanningRequest request, Instant startedAt, long startedNanos) {
        Instant now = clock.instant();
        return !now.isBefore(request.budget().deadline())
                || Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos))
                .compareTo(request.budget().maxRuntime()) >= 0
                || Duration.between(startedAt, now).compareTo(request.budget().maxRuntime()) >= 0;
    }
}
