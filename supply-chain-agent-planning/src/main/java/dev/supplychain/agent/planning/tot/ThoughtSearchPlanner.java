package dev.supplychain.agent.planning.tot;

import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanCandidateFactory;
import dev.supplychain.agent.planning.api.PlannerExecutionContext;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.session.PlannerNodeCheckpoint;
import dev.supplychain.agent.planning.api.PlannerResourceMeter;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlannerTraceNode;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningStatistics;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.WorldTransition;
import dev.supplychain.agent.planning.session.PlannerPathCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionStatus;
import dev.supplychain.agent.planning.session.PlannerSessionStore;
import dev.supplychain.agent.planning.session.PlannerSessionSupport;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ToT-inspired bounded search over structured partial recovery plans. The search tree is internal to
 * this algorithm; it is not represented as LangGraph4j nodes. This is an engineering adaptation:
 * domain action proposals are expanded and evaluated, rather than storing unrestricted model thoughts.
 */
public final class ThoughtSearchPlanner implements PlanningStrategy {
    private static final String ROOT_ID = "root";

    private static final class MutableNode {
        final String id;
        final String parentId;
        final String actionId;
        final PlanningState state;
        final double score;
        final boolean feasible;
        final List<String> children = new ArrayList<>();
        final Set<String> attemptedActionIds = new LinkedHashSet<>();
        boolean fullyExpanded;
        String disposition;

        MutableNode(String id, String parentId, String actionId, PlanningState state,
                    double score, boolean feasible) {
            this.id = id;
            this.parentId = parentId;
            this.actionId = actionId;
            this.state = state;
            this.score = score;
            this.feasible = feasible;
            this.disposition = feasible ? "OPEN" : "INFEASIBLE";
        }
    }

    private final PlanningWorldModel worldModel;
    private final CandidateEvaluator evaluator;
    private final PlannerResourceMeter resourceMeter;
    private final PlannerSessionStore sessions;
    private final ToTParameters parameters;
    private final ThoughtActionRanker actionRanker;
    private final Clock clock;

    public ThoughtSearchPlanner(PlanningWorldModel worldModel, CandidateEvaluator evaluator,
                                PlannerResourceMeter resourceMeter, PlannerSessionStore sessions,
                                ToTParameters parameters, Clock clock) {
        this(worldModel, evaluator, resourceMeter, sessions, parameters,
                new DeterministicThoughtActionRanker(), clock);
    }

    public ThoughtSearchPlanner(PlanningWorldModel worldModel, CandidateEvaluator evaluator,
                                PlannerResourceMeter resourceMeter, PlannerSessionStore sessions,
                                ToTParameters parameters, ThoughtActionRanker actionRanker, Clock clock) {
        this.worldModel = Objects.requireNonNull(worldModel, "worldModel");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.resourceMeter = Objects.requireNonNull(resourceMeter, "resourceMeter");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.actionRanker = Objects.requireNonNull(actionRanker, "actionRanker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public PlannerId id() { return PlannerId.TOT_BOUNDED_SEARCH; }

    @Override
    public PlanningResult plan(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        var context = new PlannerExecutionContext(request, id(), resourceMeter, clock);
        String sessionId = PlannerSessionSupport.sessionId(request);
        String configFingerprint = configurationFingerprint();
        PlannerSessionCheckpoint checkpoint = PlannerSessionSupport.loadIfRequested(
                request, id(), configFingerprint, sessions);
        long revision = checkpoint == null ? 0 : checkpoint.revision();
        if (checkpoint != null) {
            context.restoreCounters(checkpoint.expansions(), checkpoint.evaluations(),
                    checkpoint.modelCalls(), checkpoint.rollouts(), checkpoint.prunedBranches(),
                    checkpoint.backtracks(), checkpoint.cumulativeElapsed());
        }

        var nodes = checkpoint == null ? createRoot(request) : restoreTree(request, checkpoint);
        var candidates = reconstructCandidates(request, nodes);
        List<MutableNode> frontier = checkpoint == null
                ? new ArrayList<>(List.of(nodes.get(ROOT_ID)))
                : restoreFrontier(checkpoint, nodes);

        PlannerTerminationReason reason = PlannerTerminationReason.COMPLETED;
        boolean stopped = false;
        boolean sawAnyAction = nodes.values().stream().anyMatch(node -> !node.attemptedActionIds.isEmpty()
                || !node.children.isEmpty() || node.state.depth() > 0);
        boolean reachedMaxDepth = nodes.values().stream()
                .anyMatch(node -> node.state.depth() >= request.budget().maxDepth());

        while (!frontier.isEmpty()) {
            int currentDepth = frontier.stream().mapToInt(node -> node.state.depth()).min().orElseThrow();
            List<MutableNode> layer = frontier.stream().filter(node -> node.state.depth() == currentDepth)
                    .sorted(Comparator.comparing(node -> node.id)).toList();
            List<MutableNode> laterDepths = frontier.stream().filter(node -> node.state.depth() > currentDepth)
                    .sorted(Comparator.comparingInt((MutableNode node) -> node.state.depth()).thenComparing(node -> node.id)).toList();
            var nextLayer = new ArrayList<MutableNode>();
            int parentIndex = 0;

            for (; parentIndex < layer.size(); parentIndex++) {
                MutableNode parent = layer.get(parentIndex);
                if (parent.state.depth() >= request.budget().maxDepth()) {
                    parent.fullyExpanded = true;
                    parent.disposition = "MAX_DEPTH";
                    reachedMaxDepth = true;
                    continue;
                }

                List<PlanningAction> available = List.copyOf(Objects.requireNonNull(
                        worldModel.availableActions(request, parent.state), "worldModel available actions"));
                if (available.isEmpty()) {
                    parent.fullyExpanded = true;
                    parent.disposition = "DEAD_END";
                    context.backtracked();
                    continue;
                }
                sawAnyAction = true;
                List<PlanningAction> ranked = List.copyOf(Objects.requireNonNull(
                        actionRanker.rank(request, parent.state, available), "thought action ranker result"));
                validateRankedActions(available, ranked);
                List<PlanningAction> untried = ranked.stream()
                        .filter(action -> !parent.attemptedActionIds.contains(action.planningActionId())).toList();
                if (untried.isEmpty()) {
                    parent.fullyExpanded = true;
                    if (parent.disposition.equals("OPEN")) parent.disposition = "EXPANDED";
                    continue;
                }

                int branchCount = Math.min(parameters.branchingFactor(), untried.size());
                for (int actionIndex = 0; actionIndex < branchCount; actionIndex++) {
                    PlanningAction action = untried.get(actionIndex);
                    var charges = new java.util.EnumMap<ResourceUnit, Long>(ResourceUnit.class);
                    charges.put(ResourceUnit.SEARCH_EXPANSIONS, 1L);
                    charges.put(ResourceUnit.REASONING_EVALUATIONS, 1L);
                    if (!context.consume(new ResourceUsage(charges))) {
                        reason = context.stopReason();
                        stopped = true;
                        break;
                    }
                    parent.attemptedActionIds.add(action.planningActionId());
                    WorldTransition transition = worldModel.apply(request, parent.state, action);
                    if (transition instanceof WorldTransition.Rejected) {
                        context.branchPruned();
                        continue;
                    }

                    PlanningState childState = ((WorldTransition.Applied) transition).state();
                    var evaluation = evaluator.evaluate(request, childState);
                    String childId = PlanCandidateFactory.stateNodeId(request, childState.actionPath());
                    MutableNode child = nodes.get(childId);
                    if (child == null) {
                        child = new MutableNode(childId, parent.id, action.planningActionId(), childState,
                                evaluation.score(), evaluation.feasible());
                        nodes.put(childId, child);
                    }
                    if (!parent.children.contains(childId)) parent.children.add(childId);
                    if (!evaluation.feasible()) {
                        child.disposition = "INFEASIBLE";
                        context.branchPruned();
                        continue;
                    }
                    child.disposition = "RETAINED";
                    candidates.put(PlanCandidateFactory.candidateId(request, childState.actionPath()),
                            PlanCandidateFactory.create(request, childState, evaluation,
                                    "ToT-inspired structured successor evaluated at depth " + childState.depth() + ".", List.of()));
                    if (childState.depth() < request.budget().maxDepth() && !child.fullyExpanded) {
                        nextLayer.add(child);
                    } else {
                        reachedMaxDepth = true;
                    }
                }

                if (stopped) break;
                if (untried.size() > branchCount) {
                    parent.fullyExpanded = true;
                    parent.disposition = "BRANCHING_PRUNED";
                    for (int skipped = branchCount; skipped < untried.size(); skipped++) context.branchPruned();
                } else if (parent.attemptedActionIds.containsAll(available.stream()
                        .map(PlanningAction::planningActionId).toList())) {
                    parent.fullyExpanded = true;
                    if (parent.disposition.equals("OPEN")) parent.disposition = "EXPANDED";
                }
            }

            if (stopped) {
                var resumable = new LinkedHashMap<String, MutableNode>();
                nextLayer.forEach(node -> resumable.put(node.id, node));
                layer.subList(parentIndex, layer.size()).forEach(node -> resumable.put(node.id, node));
                laterDepths.forEach(node -> resumable.put(node.id, node));
                frontier = orderedFrontier(resumable.values());
                revision = persist(request, sessionId, revision, nodes, frontier, context, PlannerSessionStatus.ACTIVE);
                break;
            }

            nextLayer.sort(Comparator.comparingDouble((MutableNode node) -> node.score).reversed()
                    .thenComparing(node -> node.id));
            if (nextLayer.size() > parameters.beamWidth()) {
                for (MutableNode pruned : nextLayer.subList(parameters.beamWidth(), nextLayer.size())) {
                    pruned.disposition = "BEAM_PRUNED";
                    context.branchPruned();
                }
                nextLayer = new ArrayList<>(nextLayer.subList(0, parameters.beamWidth()));
            }
            frontier = orderedFrontier(nextLayer);
            if (!frontier.isEmpty() && (currentDepth + 1) % parameters.checkpointEveryDepth() == 0) {
                revision = persist(request, sessionId, revision, nodes, frontier, context, PlannerSessionStatus.ACTIVE);
            }
        }

        if (!stopped) {
            if (candidates.isEmpty()) {
                reason = sawAnyAction ? PlannerTerminationReason.NO_FEASIBLE_CANDIDATES
                        : PlannerTerminationReason.NO_AVAILABLE_ACTIONS;
            } else if (reachedMaxDepth) {
                reason = PlannerTerminationReason.MAX_DEPTH_REACHED;
            } else {
                reason = PlannerTerminationReason.COMPLETED;
            }
            persist(request, sessionId, revision, nodes, List.of(), context, PlannerSessionStatus.COMPLETED);
        }

        List<PlanCandidate> orderedCandidates = candidates.values().stream()
                .sorted(Comparator.comparingDouble((PlanCandidate plan) -> plan.evaluation().score()).reversed()
                        .thenComparing(PlanCandidate::candidateId))
                .limit(request.budget().maxCandidates()).toList();
        List<PlannerTraceNode> trace = nodes.values().stream()
                .sorted(Comparator.comparingInt((MutableNode node) -> node.state.depth()).thenComparing(node -> node.id))
                .map(node -> new PlannerTraceNode(node.id, node.parentId, node.actionId,
                        node.state.depth(), node.score, 0, 0.0, node.disposition, node.children))
                .toList();
        return new PlanningResult(id(), orderedCandidates, reason, context.statistics(), sessionId,
                "ToT-inspired bounded search over structured partial recovery plans: ranked successor proposals, deterministic evaluation, branch limits, beam pruning and retained frontier checkpoints.", trace);
    }

    private Map<String, MutableNode> createRoot(PlanningRequest request) {
        var nodes = new LinkedHashMap<String, MutableNode>();
        nodes.put(ROOT_ID, new MutableNode(ROOT_ID, null, null, worldModel.initialState(request), 0.0, true));
        return nodes;
    }

    private Map<String, MutableNode> restoreTree(PlanningRequest request, PlannerSessionCheckpoint checkpoint) {
        var nodes = new LinkedHashMap<String, MutableNode>();
        List<PlannerNodeCheckpoint> ordered = checkpoint.treeNodes().stream()
                .sorted(Comparator.comparingInt((PlannerNodeCheckpoint node) -> node.actionIds().size())
                        .thenComparing(PlannerNodeCheckpoint::nodeId)).toList();
        for (PlannerNodeCheckpoint saved : ordered) {
            PlanningState state = PlannerSessionSupport.replayPath(request, worldModel, saved.actionIds());
            String expectedId = saved.actionIds().isEmpty() ? ROOT_ID
                    : PlanCandidateFactory.stateNodeId(request, state.actionPath());
            if (!expectedId.equals(saved.nodeId())) {
                throw new IllegalStateException("ToT checkpoint node identity does not match its replayed action path");
            }
            String expectedParent = saved.actionIds().isEmpty() ? null
                    : (saved.actionIds().size() == 1 ? ROOT_ID : PlanCandidateFactory.stateNodeId(request,
                            state.actionPath().subList(0, state.actionPath().size() - 1)));
            if (!Objects.equals(expectedParent, saved.parentNodeId())) {
                throw new IllegalStateException("ToT checkpoint parent does not match the saved action path");
            }
            var evaluation = saved.actionIds().isEmpty() ? null : evaluator.evaluate(request, state);
            MutableNode node = new MutableNode(saved.nodeId(), saved.parentNodeId(),
                    saved.actionIds().isEmpty() ? null : saved.actionIds().get(saved.actionIds().size() - 1), state,
                    evaluation == null ? 0.0 : evaluation.score(), evaluation == null || evaluation.feasible());
            node.attemptedActionIds.addAll(saved.expandedActionIds());
            node.fullyExpanded = saved.fullyExpanded();
            node.disposition = saved.fullyExpanded() ? "EXPANDED" : (node.feasible ? "OPEN" : "INFEASIBLE");
            if (nodes.putIfAbsent(node.id, node) != null) throw new IllegalStateException("duplicate ToT checkpoint node: " + node.id);
            if (node.parentId != null) {
                MutableNode parent = nodes.get(node.parentId);
                if (parent == null) throw new IllegalStateException("ToT checkpoint parent is missing: " + node.parentId);
                if (!parent.children.contains(node.id)) parent.children.add(node.id);
            }
        }
        if (!nodes.containsKey(ROOT_ID)) throw new IllegalStateException("ToT checkpoint has no root node");
        return nodes;
    }

    private List<MutableNode> restoreFrontier(PlannerSessionCheckpoint checkpoint, Map<String, MutableNode> nodes) {
        if (checkpoint.frontier().isEmpty()) throw new IllegalStateException("active ToT checkpoint contains no frontier paths");
        var restored = new LinkedHashMap<String, MutableNode>();
        for (PlannerPathCheckpoint path : checkpoint.frontier()) {
            MutableNode node = nodes.get(path.nodeId());
            if (node == null) throw new IllegalStateException("ToT checkpoint frontier references missing node " + path.nodeId());
            List<String> expectedPath = node.state.actionPath().stream().map(PlanningAction::planningActionId).toList();
            if (!expectedPath.equals(path.actionIds())) throw new IllegalStateException("ToT frontier path differs from saved tree node");
            restored.put(node.id, node);
        }
        return orderedFrontier(restored.values());
    }

    private LinkedHashMap<String, PlanCandidate> reconstructCandidates(PlanningRequest request,
                                                                         Map<String, MutableNode> nodes) {
        var candidates = new LinkedHashMap<String, PlanCandidate>();
        for (MutableNode node : nodes.values()) {
            if (node.state.actionPath().isEmpty()) continue;
            var evaluation = evaluator.evaluate(request, node.state);
            if (evaluation.feasible()) {
                PlanCandidate candidate = PlanCandidateFactory.create(request, node.state, evaluation,
                        "Restored ToT search-tree candidate.", List.of());
                candidates.put(candidate.candidateId(), candidate);
            }
        }
        return candidates;
    }

    private long persist(PlanningRequest request, String sessionId, long revision,
                         Map<String, MutableNode> nodes, List<MutableNode> frontier,
                         PlannerExecutionContext context, PlannerSessionStatus status) {
        List<PlannerNodeCheckpoint> tree = nodes.values().stream()
                .map(node -> new PlannerNodeCheckpoint(node.id, node.parentId,
                        node.state.actionPath().stream().map(PlanningAction::planningActionId).toList(),
                        0, 0.0, List.copyOf(node.attemptedActionIds), node.fullyExpanded)).toList();
        List<PlannerPathCheckpoint> frontierPaths = frontier.stream()
                .map(node -> new PlannerPathCheckpoint(node.id,
                        node.state.actionPath().stream().map(PlanningAction::planningActionId).toList(), node.score)).toList();
        PlanningStatistics stats = context.statistics();
        var checkpoint = new PlannerSessionCheckpoint(sessionId, id(), request.fingerprint(), configurationFingerprint(),
                revision + 1, status, frontierPaths, tree, stats.expansions(), stats.evaluations(),
                stats.modelCalls(), stats.rollouts(), stats.prunedBranches(), stats.backtracks(),
                stats.elapsed(), clock.instant());
        return PlannerSessionSupport.save(sessions, checkpoint, revision);
    }

    private String configurationFingerprint() {
        return "tot-v3|" + parameters + "|ranker=" + actionRanker.configurationFingerprint()
                + "|world=" + worldModel.configurationFingerprint()
                + "|evaluator=" + evaluator.configurationFingerprint();
    }

    private static List<MutableNode> orderedFrontier(java.util.Collection<MutableNode> values) {
        return values.stream().collect(java.util.stream.Collectors.toMap(node -> node.id, node -> node,
                (left, right) -> left, LinkedHashMap::new)).values().stream()
                .sorted(Comparator.comparingInt((MutableNode node) -> node.state.depth()).thenComparing(node -> node.id)).toList();
    }


    private static void validateRankedActions(List<PlanningAction> available, List<PlanningAction> ranked) {
        Set<String> availableIds = available.stream().map(PlanningAction::planningActionId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> rankedIds = ranked.stream().map(PlanningAction::planningActionId)
                .collect(java.util.stream.Collectors.toSet());
        if (ranked.size() != available.size() || rankedIds.size() != ranked.size() || !rankedIds.equals(availableIds)) {
            throw new IllegalStateException("thought action ranker must return every available action exactly once");
        }
    }
}
