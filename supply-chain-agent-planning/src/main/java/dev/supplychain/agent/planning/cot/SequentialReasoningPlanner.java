package dev.supplychain.agent.planning.cot;

import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanCandidateFactory;
import dev.supplychain.agent.planning.api.PlannerExecutionContext;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerResourceMeter;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlannerTraceNode;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.WorldTransition;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** CoT-style sequential baseline: repeatedly choose one next action, with no branching tree. */
public final class SequentialReasoningPlanner implements PlanningStrategy {
    private final PlanningWorldModel worldModel;
    private final CandidateEvaluator evaluator;
    private final SequentialDecisionProvider decisionProvider;
    private final PlannerResourceMeter resourceMeter;
    private final Clock clock;

    public SequentialReasoningPlanner(PlanningWorldModel worldModel, CandidateEvaluator evaluator,
                                      SequentialDecisionProvider decisionProvider,
                                      PlannerResourceMeter resourceMeter, Clock clock) {
        this.worldModel = Objects.requireNonNull(worldModel, "worldModel");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.decisionProvider = Objects.requireNonNull(decisionProvider, "decisionProvider");
        this.resourceMeter = Objects.requireNonNull(resourceMeter, "resourceMeter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public PlannerId id() { return PlannerId.COT_SEQUENTIAL; }

    @Override
    public PlanningResult plan(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.resumeSessionId() != null) {
            throw new IllegalArgumentException("the sequential baseline does not persist a resumable search session");
        }
        var context = new PlannerExecutionContext(request, id(), resourceMeter, clock);
        PlanningState state = worldModel.initialState(request);
        var trace = new ArrayList<PlannerTraceNode>();
        var rationale = new ArrayList<String>();
        var assumptions = new LinkedHashSet<String>();
        PlanCandidate finalCandidate = null;
        PlannerTerminationReason reason = PlannerTerminationReason.COMPLETED;
        String parentId = null;
        for (int step = 0; step < request.budget().maxDepth(); step++) {
            List<dev.supplychain.agent.planning.api.PlanningAction> available = worldModel.availableActions(request, state).stream()
                    .sorted(java.util.Comparator
                            .comparing((dev.supplychain.agent.planning.api.PlanningAction action) -> request.isReusableRecoveryAction(action)).reversed()
                            .thenComparing(dev.supplychain.agent.planning.api.PlanningAction::planningActionId))
                    .toList();
            if (available.isEmpty()) {
                reason = finalCandidate == null ? PlannerTerminationReason.NO_AVAILABLE_ACTIONS : PlannerTerminationReason.COMPLETED;
                break;
            }
            var decisionCharges = new java.util.EnumMap<ResourceUnit, Long>(ResourceUnit.class);
            decisionCharges.put(ResourceUnit.REASONING_EVALUATIONS, 1L);
            if (decisionProvider.modelBacked()) decisionCharges.put(ResourceUnit.MODEL_CALLS, 1L);
            if (!context.consume(new ResourceUsage(decisionCharges))) {
                reason = context.stopReason();
                break;
            }
            SequentialDecision decision = Objects.requireNonNull(
                    decisionProvider.chooseNext(request, state, available), "decisionProvider result");
            rationale.add(decision.conciseRationale());
            assumptions.addAll(decision.assumptions());
            var permittedEvidence = new LinkedHashSet<>(request.evidence());
            permittedEvidence.addAll(request.workingMemory().sourceEvidence());
            permittedEvidence.addAll(state.evidence());
            if (!permittedEvidence.containsAll(decision.supportingEvidence())) {
                reason = PlannerTerminationReason.INVALID_PROPOSAL;
                context.branchPruned();
                break;
            }
            state = state.withAdditionalEvidence(decision.supportingEvidence());
            if (decision.stop()) {
                reason = PlannerTerminationReason.STOPPED_BY_STRATEGY;
                break;
            }
            var selected = available.stream()
                    .filter(action -> action.planningActionId().equals(decision.selectedPlanningActionId()))
                    .findFirst();
            if (selected.isEmpty()) {
                reason = PlannerTerminationReason.INVALID_PROPOSAL;
                context.branchPruned();
                break;
            }
            WorldTransition transition = worldModel.apply(request, state, selected.get());
            if (transition instanceof WorldTransition.Rejected) {
                reason = PlannerTerminationReason.NO_FEASIBLE_CANDIDATES;
                context.branchPruned();
                break;
            }
            state = ((WorldTransition.Applied) transition).state();
            if (!context.consume(ResourceUsage.of(ResourceUnit.REASONING_EVALUATIONS, 1))) {
                reason = context.stopReason();
                break;
            }
            var evaluation = evaluator.evaluate(request, state);
            String nodeId = PlanCandidateFactory.stateNodeId(request, state.actionPath());
            trace.add(new PlannerTraceNode(nodeId, parentId, selected.get().planningActionId(), state.depth(),
                    evaluation.score(), 0, 0.0, evaluation.feasible() ? "accepted" : "rejected", List.of()));
            parentId = nodeId;
            if (!evaluation.feasible()) {
                reason = PlannerTerminationReason.NO_FEASIBLE_CANDIDATES;
                context.branchPruned();
                finalCandidate = null;
                break;
            }
            finalCandidate = PlanCandidateFactory.create(request, state, evaluation,
                    String.join(" ", rationale), List.copyOf(assumptions));
            if (step + 1 == request.budget().maxDepth()) reason = PlannerTerminationReason.MAX_DEPTH_REACHED;
            if (context.consumed(ResourceUnit.REASONING_EVALUATIONS) >= request.budget().maxEvaluations()) {
                reason = PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED;
                break;
            }
        }
        return new PlanningResult(id(), finalCandidate == null ? List.of() : List.of(finalCandidate),
                reason, context.statistics(), "cot-" + UUID.randomUUID(),
                "Sequential baseline: one action is proposed, evaluated and then the next decision is requested; no branching search is performed. The returned run identifier is diagnostic and is not a resumable planner session.", trace);
    }
}
