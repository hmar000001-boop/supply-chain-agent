package dev.supplychain.agent.planning.mcts;

import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanCandidateFactory;
import dev.supplychain.agent.planning.api.PlannerExecutionContext;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerTraceNode;
import dev.supplychain.agent.planning.api.PlannerResourceMeter;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.WorldTransition;
import dev.supplychain.agent.planning.session.PlannerNodeCheckpoint;
import dev.supplychain.agent.planning.session.PlannerPathCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionStatus;
import dev.supplychain.agent.planning.session.PlannerSessionStore;
import dev.supplychain.agent.planning.session.PlannerSessionSupport;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;

/** RAP-inspired MCTS over the deterministic supply-chain simulator; this is not a faithful RAP reproduction. */
public final class MctsSearchPlanner implements PlanningStrategy {
    private static final String ROOT_ID = "root";

    private static final class Node {
        final String id;
        final String parentId;
        final String incomingActionId;
        final PlanningState state;
        final Set<String> attemptedActionIds = new LinkedHashSet<>();
        final List<Node> children = new ArrayList<>();
        long visits;
        double totalReward;
        boolean fullyExpanded;

        Node(String id, String parentId, String incomingActionId, PlanningState state) {
            this.id = id;
            this.parentId = parentId;
            this.incomingActionId = incomingActionId;
            this.state = state;
        }
        double meanReward() { return visits == 0 ? 0.0 : totalReward / visits; }
    }

    private final PlanningWorldModel worldModel;
    private final CandidateEvaluator evaluator;
    private final PlannerResourceMeter resourceMeter;
    private final PlannerSessionStore sessions;
    private final MctsParameters parameters;
    private final Clock clock;

    public MctsSearchPlanner(PlanningWorldModel worldModel, CandidateEvaluator evaluator,
                             PlannerResourceMeter resourceMeter, PlannerSessionStore sessions,
                             MctsParameters parameters, Clock clock) {
        this.worldModel = Objects.requireNonNull(worldModel, "worldModel");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.resourceMeter = Objects.requireNonNull(resourceMeter, "resourceMeter");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public PlannerId id() { return PlannerId.RAP_INSPIRED_MCTS; }

    @Override
    public PlanningResult plan(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        var context = new PlannerExecutionContext(request, id(), resourceMeter, clock);
        String sessionId = PlannerSessionSupport.sessionId(request);
        PlannerSessionCheckpoint checkpoint = PlannerSessionSupport.loadIfRequested(
                request, id(), configurationFingerprint(), sessions);
        long revision = checkpoint == null ? 0 : checkpoint.revision();
        if (checkpoint != null) {
            context.restoreCounters(checkpoint.expansions(), checkpoint.evaluations(),
                    checkpoint.modelCalls(), checkpoint.rollouts(), checkpoint.prunedBranches(),
                    checkpoint.backtracks(), checkpoint.cumulativeElapsed());
        }

        Map<String, Node> nodes = checkpoint == null ? createRoot(request) : restoreTree(request, checkpoint);
        Node root = nodes.get(ROOT_ID);
        if (root == null) throw new IllegalStateException("MCTS tree has no root node");
        var candidates = checkpoint == null ? new LinkedHashMap<String, PlanCandidate>()
                : restoreCandidates(request, checkpoint);
        long iteration = checkpoint == null ? 0 : checkpoint.rollouts();
        long targetIterations = request.budget().maxRollouts();
        PlannerTerminationReason reason = PlannerTerminationReason.COMPLETED;
        boolean stopped = false;

        if (worldModel.availableActions(request, root.state).isEmpty()) {
            reason = PlannerTerminationReason.NO_AVAILABLE_ACTIONS;
        }

        while (reason != PlannerTerminationReason.NO_AVAILABLE_ACTIONS && iteration < targetIterations) {
            Node selected = selectAndExpand(request, root, nodes, context, iteration);
            if (context.stopReason() != null) {
                reason = context.stopReason();
                stopped = true;
                break;
            }
            var iterationCharges = new java.util.EnumMap<ResourceUnit, Long>(ResourceUnit.class);
            iterationCharges.put(ResourceUnit.MCTS_ROLLOUTS, 1L);
            iterationCharges.put(ResourceUnit.REASONING_EVALUATIONS, 1L);
            if (!context.consume(new ResourceUsage(iterationCharges))) {
                reason = context.stopReason();
                stopped = true;
                break;
            }
            iteration++;

            PlanningState rolloutState = selected.state;
            var rolloutRandom = new SplittableRandom(parameters.deterministicSeed() + iteration);
            int rolloutSteps = 0;
            while (rolloutSteps < parameters.rolloutDepth()
                    && rolloutState.depth() < request.budget().maxDepth()) {
                List<PlanningAction> available = worldModel.availableActions(request, rolloutState);
                if (available.isEmpty()) break;
                var shuffled = new ArrayList<>(available);
                for (int index = shuffled.size() - 1; index > 0; index--) {
                    int swap = rolloutRandom.nextInt(index + 1);
                    var temp = shuffled.get(index);
                    shuffled.set(index, shuffled.get(swap));
                    shuffled.set(swap, temp);
                }
                PlanningState next = null;
                for (PlanningAction action : shuffled) {
                    WorldTransition transition = worldModel.apply(request, rolloutState, action);
                    if (transition instanceof WorldTransition.Applied applied) {
                        next = applied.state();
                        break;
                    }
                }
                if (next == null) break;
                rolloutState = next;
                rolloutSteps++;
            }

            var evaluation = evaluator.evaluate(request, rolloutState);
            double reward = evaluation.feasible() && !rolloutState.actionPath().isEmpty()
                    ? evaluation.score() : 0.0;
            if (evaluation.feasible() && !rolloutState.actionPath().isEmpty()) {
                PlanCandidate candidate = PlanCandidateFactory.create(request, rolloutState, evaluation,
                        "RAP-inspired MCTS rollout evaluated against deterministic supply-chain transitions.", List.of());
                candidates.put(candidate.candidateId(), candidate);
            } else if (!rolloutState.actionPath().isEmpty()) {
                context.branchPruned();
            }
            backpropagate(selected, nodes, reward);

            if (iteration % parameters.checkpointEveryIterations() == 0) {
                revision = persist(request, sessionId, revision, nodes, candidates, context,
                        PlannerSessionStatus.ACTIVE);
            }
            if (selected == root && worldModel.availableActions(request, root.state).isEmpty()) {
                reason = PlannerTerminationReason.NO_AVAILABLE_ACTIONS;
                break;
            }
        }

        if (!stopped) {
            if (reason != PlannerTerminationReason.NO_AVAILABLE_ACTIONS) {
                if (targetIterations == 0) {
                    reason = PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED;
                } else if (candidates.isEmpty()) {
                    reason = PlannerTerminationReason.NO_FEASIBLE_CANDIDATES;
                } else if (iteration >= targetIterations) {
                    reason = PlannerTerminationReason.COMPLETED;
                }
            }
            persist(request, sessionId, revision, nodes, candidates, context, PlannerSessionStatus.COMPLETED);
        } else {
            persist(request, sessionId, revision, nodes, candidates, context, PlannerSessionStatus.ACTIVE);
        }

        List<PlanCandidate> ordered = candidates.values().stream()
                .sorted(Comparator.comparingDouble((PlanCandidate candidate) -> candidate.evaluation().score()).reversed()
                        .thenComparing(PlanCandidate::candidateId))
                .limit(request.budget().maxCandidates()).toList();
        List<PlannerTraceNode> trace = nodes.values().stream()
                .sorted(Comparator.comparingInt((Node node) -> node.state.depth()).thenComparing(node -> node.id))
                .map(node -> new PlannerTraceNode(node.id, node.parentId, node.incomingActionId,
                        node.state.depth(), candidateScore(request, node, candidates), node.visits,
                        node.meanReward(), node.fullyExpanded ? "EXPANDED" : "OPEN",
                        node.children.stream().map(child -> child.id).toList()))
                .toList();
        return new PlanningResult(id(), ordered, reason, context.statistics(), sessionId,
                "RAP-inspired, domain-grounded MCTS adaptation (not a faithful RAP reproduction): seeded expansion, UCT selection, deterministic simulator-backed transitions, bounded rollouts and reward backpropagation. The simulator—not the LLM—determines modeled world transitions.", trace);
    }

    private Map<String, Node> createRoot(PlanningRequest request) {
        var nodes = new LinkedHashMap<String, Node>();
        nodes.put(ROOT_ID, new Node(ROOT_ID, null, null, worldModel.initialState(request)));
        return nodes;
    }

    private Node selectAndExpand(PlanningRequest request, Node root, Map<String, Node> nodes,
                                 dev.supplychain.agent.planning.api.PlannerExecutionContext context,
                                 long iteration) {
        Node current = root;
        while (true) {
            if (current.state.depth() >= request.budget().maxDepth()) {
                current.fullyExpanded = true;
                return current;
            }
            List<PlanningAction> available = worldModel.availableActions(request, current.state);
            Node expanding = current;
            List<PlanningAction> untried = available.stream()
                    .filter(action -> !expanding.attemptedActionIds.contains(action.planningActionId())).toList();
            if (!untried.isEmpty()) {
                var random = new SplittableRandom(parameters.deterministicSeed() ^ iteration ^ current.id.hashCode());
                PlanningAction action = untried.get(random.nextInt(untried.size()));
                if (!context.consume(ResourceUsage.of(ResourceUnit.SEARCH_EXPANSIONS, 1))) return current;
                current.attemptedActionIds.add(action.planningActionId());
                WorldTransition transition = worldModel.apply(request, current.state, action);
                if (transition instanceof WorldTransition.Rejected) {
                    context.branchPruned();
                    if (current.attemptedActionIds.size() >= available.size()) current.fullyExpanded = true;
                    continue;
                }
                PlanningState childState = ((WorldTransition.Applied) transition).state();
                String childId = PlanCandidateFactory.stateNodeId(request, childState.actionPath());
                Node child = nodes.get(childId);
                if (child == null) {
                    child = new Node(childId, current.id, action.planningActionId(), childState);
                    nodes.put(childId, child);
                }
                Node parent = current;
                Node childNode = child;
                if (parent.children.stream().noneMatch(existing -> existing.id.equals(childNode.id))) {
                    parent.children.add(childNode);
                }
                if (parent.attemptedActionIds.size() >= available.size()) parent.fullyExpanded = true;
                return child;
            }

            current.fullyExpanded = true;
            if (current.children.isEmpty()) return current;
            Node parent = current;
            current = parent.children.stream().max(Comparator
                    .comparingDouble((Node child) -> uct(parent, child))
                    .thenComparing(child -> child.id, Comparator.reverseOrder())).orElseThrow();
        }
    }

    private double uct(Node parent, Node child) {
        if (child.visits == 0) return Double.POSITIVE_INFINITY;
        double exploration = parameters.explorationCoefficient()
                * Math.sqrt(Math.log(Math.max(1.0, parent.visits)) / child.visits);
        return child.meanReward() + exploration;
    }

    private static void backpropagate(Node node, Map<String, Node> nodes, double reward) {
        Node current = node;
        while (current != null) {
            current.visits++;
            current.totalReward += reward;
            current = current.parentId == null ? null : nodes.get(current.parentId);
        }
    }

    private Map<String, Node> restoreTree(PlanningRequest request, PlannerSessionCheckpoint checkpoint) {
        var nodes = new LinkedHashMap<String, Node>();
        List<PlannerNodeCheckpoint> ordered = checkpoint.treeNodes().stream()
                .sorted(Comparator.comparingInt((PlannerNodeCheckpoint node) -> node.actionIds().size())
                        .thenComparing(PlannerNodeCheckpoint::nodeId)).toList();
        for (PlannerNodeCheckpoint saved : ordered) {
            PlanningState state = PlannerSessionSupport.replayPath(request, worldModel, saved.actionIds());
            String expectedId = saved.actionIds().isEmpty() ? ROOT_ID
                    : PlanCandidateFactory.stateNodeId(request, state.actionPath());
            if (!expectedId.equals(saved.nodeId())) {
                throw new IllegalStateException("MCTS checkpoint node identity does not match its replayed action path");
            }
            String expectedParent = saved.actionIds().isEmpty() ? null
                    : (saved.actionIds().size() == 1 ? ROOT_ID : PlanCandidateFactory.stateNodeId(request,
                            state.actionPath().subList(0, state.actionPath().size() - 1)));
            if (!Objects.equals(expectedParent, saved.parentNodeId())) {
                throw new IllegalStateException("MCTS checkpoint parent does not match the saved action path");
            }
            Node node = new Node(saved.nodeId(), saved.parentNodeId(),
                    saved.actionIds().isEmpty() ? null : saved.actionIds().get(saved.actionIds().size() - 1), state);
            node.visits = saved.visits();
            node.totalReward = saved.totalReward();
            node.attemptedActionIds.addAll(saved.expandedActionIds());
            node.fullyExpanded = saved.fullyExpanded();
            if (nodes.putIfAbsent(node.id, node) != null) throw new IllegalStateException("duplicate MCTS checkpoint node: " + node.id);
            if (node.parentId != null) {
                Node parent = nodes.get(node.parentId);
                if (parent == null) throw new IllegalStateException("MCTS checkpoint parent is missing: " + node.parentId);
                parent.children.add(node);
            }
        }
        if (!nodes.containsKey(ROOT_ID)) throw new IllegalStateException("MCTS checkpoint has no root node");
        return nodes;
    }

    private LinkedHashMap<String, PlanCandidate> restoreCandidates(PlanningRequest request,
                                                                    PlannerSessionCheckpoint checkpoint) {
        var candidates = new LinkedHashMap<String, PlanCandidate>();
        for (PlannerPathCheckpoint saved : checkpoint.frontier()) {
            PlanningState state = PlannerSessionSupport.replayPath(request, worldModel, saved.actionIds());
            String expectedId = PlanCandidateFactory.candidateId(request, state.actionPath());
            if (!expectedId.equals(saved.nodeId())) {
                throw new IllegalStateException("MCTS candidate checkpoint identity does not match its action path");
            }
            var evaluation = evaluator.evaluate(request, state);
            if (evaluation.feasible() && !state.actionPath().isEmpty()) {
                PlanCandidate candidate = PlanCandidateFactory.create(request, state, evaluation,
                        "Restored best candidate from an earlier MCTS rollout.", List.of());
                candidates.put(candidate.candidateId(), candidate);
            }
        }
        return candidates;
    }

    private long persist(PlanningRequest request, String sessionId, long revision, Map<String, Node> nodes,
                         Map<String, PlanCandidate> candidates, dev.supplychain.agent.planning.api.PlannerExecutionContext context,
                         PlannerSessionStatus status) {
        List<PlannerNodeCheckpoint> savedNodes = nodes.values().stream().map(node -> new PlannerNodeCheckpoint(
                node.id, node.parentId,
                node.state.actionPath().stream().map(PlanningAction::planningActionId).toList(),
                node.visits, node.totalReward, List.copyOf(node.attemptedActionIds), node.fullyExpanded)).toList();
        List<PlannerPathCheckpoint> savedCandidates = candidates.values().stream()
                .sorted(Comparator.comparingDouble((PlanCandidate candidate) -> candidate.evaluation().score()).reversed()
                        .thenComparing(PlanCandidate::candidateId))
                .limit(request.budget().maxCandidates())
                .map(candidate -> new PlannerPathCheckpoint(candidate.candidateId(),
                        candidate.actions().stream().map(action -> action.planningActionId()).toList(),
                        candidate.evaluation().score())).toList();
        var stats = context.statistics();
        var checkpoint = new PlannerSessionCheckpoint(sessionId, id(), request.fingerprint(),
                configurationFingerprint(), revision + 1, status, savedCandidates, savedNodes,
                stats.expansions(), stats.evaluations(), stats.modelCalls(), stats.rollouts(),
                stats.prunedBranches(), stats.backtracks(), stats.elapsed(), clock.instant());
        PlannerSessionSupport.save(sessions, checkpoint, revision);
        return checkpoint.revision();
    }

    private static double candidateScore(PlanningRequest request, Node node, Map<String, PlanCandidate> candidates) {
        if (node.state.actionPath().isEmpty()) return 0.0;
        PlanCandidate candidate = candidates.get(PlanCandidateFactory.candidateId(request, node.state.actionPath()));
        return candidate == null ? node.meanReward() : candidate.evaluation().score();
    }

    private String configurationFingerprint() {
        return "mcts-v3|" + parameters + "|world=" + worldModel.configurationFingerprint()
                + "|evaluator=" + evaluator.configurationFingerprint();
    }
}
