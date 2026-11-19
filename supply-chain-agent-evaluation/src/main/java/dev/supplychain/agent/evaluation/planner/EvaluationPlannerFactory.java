package dev.supplychain.agent.evaluation.planner;

import dev.supplychain.agent.evaluation.fixture.ScenarioFixture;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixtureFactory;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.cot.SequentialDecision;
import dev.supplychain.agent.planning.cot.SequentialReasoningPlanner;
import dev.supplychain.agent.planning.mcts.MctsParameters;
import dev.supplychain.agent.planning.mcts.MctsSearchPlanner;
import dev.supplychain.agent.planning.routing.DefaultPlannerSelectionPolicy;
import dev.supplychain.agent.planning.routing.PlannerRouter;
import dev.supplychain.agent.planning.session.InMemoryPlannerSessionStore;
import dev.supplychain.agent.planning.tot.DeterministicThoughtActionRanker;
import dev.supplychain.agent.planning.tot.ThoughtSearchPlanner;
import dev.supplychain.agent.planning.tot.ToTParameters;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Creates isolated planner instances so no search session or meter leaks between matched runs. */
public final class EvaluationPlannerFactory {
    public static final String HYBRID_ID = "HYBRID_ROUTER";
    private static final String CONFIGURATION = String.join("|",
            "sequential=deterministic-thought-action-ranker-v1;one-path;no-model-provider",
            "tot=beamWidth:4;branchingFactor:4;checkpointEveryDepth:1;deterministic-ranker:v1",
            "mcts=exploration:1.41;rolloutDepth:3;checkpointEveryIterations:1;seed=per-scenario",
            "greedy=one-step-best-feasible;action-id-tie-break;common-deterministic-evaluator-v1",
            "hybrid=DefaultPlannerSelectionPolicy;complexity-budget-thresholds-v1");
    private final ScenarioFixtureFactory fixtures;

    public EvaluationPlannerFactory(ScenarioFixtureFactory fixtures) {
        this.fixtures = Objects.requireNonNull(fixtures, "fixtures");
    }

    public EvaluationPlanner create(ScenarioFixture fixture, String requestedPlannerId) {
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(requestedPlannerId, "requestedPlannerId");
        if (GreedyDeterministicComparator.ID.equals(requestedPlannerId)) {
            var model = fixtures.worldModel();
            return new GreedyDeterministicComparator(model, fixtures.evaluator(model),
                    fixtures.advancingFixtureClock());
        }
        if (HYBRID_ID.equals(requestedPlannerId)) return new HybridRouterComparator(fixture);
        PlannerId plannerId = PlannerId.valueOf(requestedPlannerId);
        NativeStrategyBundle bundle = nativeStrategies(fixture);
        PlanningStrategy strategy = bundle.strategies().stream()
                .filter(value -> value.id() == plannerId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported evaluation planner: " + requestedPlannerId));
        return adapt(strategy, bundle.meters().get(plannerId));
    }

    public String configurationFingerprint() {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(CONFIGURATION.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public List<String> plannerIds() {
        return List.of(PlannerId.COT_SEQUENTIAL.name(), PlannerId.TOT_BOUNDED_SEARCH.name(),
                GreedyDeterministicComparator.ID, PlannerId.RAP_INSPIRED_MCTS.name(), HYBRID_ID);
    }

    private NativeStrategyBundle nativeStrategies(ScenarioFixture fixture) {
        var model = fixtures.worldModel();
        var evaluator = fixtures.evaluator(model);
        Clock clock = fixtures.advancingFixtureClock();
        var sequentialMeter = new RecordingPlannerResourceMeter();
        var totMeter = new RecordingPlannerResourceMeter();
        var mctsMeter = new RecordingPlannerResourceMeter();
        var ranker = new DeterministicThoughtActionRanker();
        var sequential = new SequentialReasoningPlanner(model, evaluator,
                (request, state, actions) -> {
                    var ranked = ranker.rank(request, state, actions);
                    return ranked.isEmpty() ? SequentialDecision.stop(request.evidence(), "No trusted action is currently available.")
                            : SequentialDecision.choose(ranked.getFirst().planningActionId(), request.evidence(), List.of(),
                            "Selected the first action from the shared deterministic impact ranker.");
                }, sequentialMeter, clock);
        var thoughtTree = new ThoughtSearchPlanner(model, evaluator, totMeter, new InMemoryPlannerSessionStore(),
                new ToTParameters(4, 4, 1), ranker, clock);
        var mcts = new MctsSearchPlanner(model, evaluator, mctsMeter, new InMemoryPlannerSessionStore(),
                new MctsParameters(1.41, 3, 1, fixture.definition().seed()), clock);
        return new NativeStrategyBundle(List.of(sequential, thoughtTree, mcts),
                java.util.Map.of(PlannerId.COT_SEQUENTIAL, sequentialMeter,
                        PlannerId.TOT_BOUNDED_SEARCH, totMeter, PlannerId.RAP_INSPIRED_MCTS, mctsMeter));
    }

    private EvaluationPlanner adapt(PlanningStrategy strategy, RecordingPlannerResourceMeter meter) {
        return new EvaluationPlanner() {
            @Override public String id() { return strategy.id().name(); }
            @Override public EvaluationPlannerResult plan(dev.supplychain.agent.planning.api.PlanningRequest request) {
                var result = strategy.plan(request);
                return new EvaluationPlannerResult(id(), null, result.candidates(), result.terminationReason(),
                        result.statistics(), resourceUsage(meter), result.selectionExplanation(), result.trace());
            }
        };
    }

    private static java.util.Map<String, Long> resourceUsage(RecordingPlannerResourceMeter meter) {
        var values = new java.util.TreeMap<String, Long>();
        meter.snapshot().forEach((unit, amount) -> values.put(unit.name(), amount));
        return java.util.Map.copyOf(values);
    }

    private record NativeStrategyBundle(List<PlanningStrategy> strategies,
                                        java.util.Map<PlannerId, RecordingPlannerResourceMeter> meters) { }

    private final class HybridRouterComparator implements EvaluationPlanner {
        private final ScenarioFixture fixture;
        private HybridRouterComparator(ScenarioFixture fixture) { this.fixture = fixture; }
        @Override public String id() { return HYBRID_ID; }
        @Override public EvaluationPlannerResult plan(dev.supplychain.agent.planning.api.PlanningRequest request) {
            NativeStrategyBundle bundle = nativeStrategies(fixture);
            var router = new PlannerRouter(bundle.strategies(), new DefaultPlannerSelectionPolicy());
            var selection = router.select(request);
            var result = router.plan(request);
            return new EvaluationPlannerResult(id(), selection.plannerId().name(), result.candidates(),
                    result.terminationReason(), result.statistics(), resourceUsage(bundle.meters().get(result.plannerId())),
                    selection.explanation(), result.trace());
        }
    }
}
