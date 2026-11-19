package dev.supplychain.agent.evaluation.host;

import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.CapabilityAuthorizer;
import dev.supplychain.agent.actions.api.InMemoryActionExecutionLedger;
import dev.supplychain.agent.app.cognitive.CognitiveCycleApplicationGraph;
import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.core.interpretation.RequestInterpreter;
import dev.supplychain.agent.core.interpretation.RequestInterpretation;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.inmemory.InMemoryEpisodeRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryProceduralMemoryRepository;
import dev.supplychain.agent.memory.inmemory.InMemorySemanticFactRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryWorkingMemoryRepository;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateFactory;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.ExponentialActivationLatencyModel;
import dev.supplychain.agent.mechanisms.b1.RecencyFrequencyActivationEstimator;
import dev.supplychain.agent.mechanisms.b2.DeterministicImpasseDetector;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionService;
import dev.supplychain.agent.mechanisms.b2.InMemoryImpasseCaseRepository;
import dev.supplychain.agent.mechanisms.b3.InMemoryWorkspaceRepository;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastService;
import dev.supplychain.agent.mechanisms.b4.InMemoryRecoveryIntentionRepository;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionService;
import dev.supplychain.agent.mechanisms.b6.MetaControlGovernor;
import dev.supplychain.agent.mechanisms.b6.MetaControlPolicy;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanEvaluation;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.WorldTransition;
import dev.supplychain.agent.planning.routing.DefaultPlannerSelectionPolicy;
import dev.supplychain.agent.planning.routing.PlannerRouter;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionPort;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import dev.supplychain.agent.runtime.cognitive.decision.InMemoryPlanningArtifactStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecycleServices;
import dev.supplychain.agent.runtime.cognitive.lifecycle.InMemoryActionExecutionBatchStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.InMemoryAuthorizedActionPlanStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.InMemoryImpasseChildArtifactStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.InMemoryObservationStore;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Test-only composition host for the actual full cognitive-cycle graph.
 *
 * <p>{@link #compileFull(Ports)} assembles the same {@code compileFull(...)} application entry point
 * used by the application. All stateful adapters are in-memory and scoped to one host instance. The
 * internally created {@link ActionRegistry} is deliberately empty: no external action handler is
 * registered by this host. A caller must explicitly provide the domain and authority ports, simulator
 * world model, candidate evaluator and production planner strategies before the compiled graph can be
 * invoked as a scenario. {@link #compileFailClosedProbe()} exists only to verify composition; all
 * host-domain, decision-domain and authority calls from that probe fail closed.
 *
 * <p>This class is not the M8-B4 end-to-end workflow runner and does not claim that a compiled graph
 * has processed a business scenario. M8-B1 uses the independent planner-only harness for matched
 * algorithm comparisons; full lifecycle scenario execution remains a later M8 workstream.</p>
 */
public final class FullGraphEvaluationHost {
    public static final Instant HOST_TIME = Instant.parse("2026-11-20T12:00:00Z");

    /** Explicit trusted dependencies for a full-graph evaluation host. */
    public record Ports(
            CognitiveCycleDecisionPort decisionDomain,
            CognitiveCycleLifecyclePort lifecycleDomain,
            CognitiveCycleAuthorityPort authority,
            RequestInterpreter interpreter,
            PlanningWorldModel worldModel,
            CandidateEvaluator candidateEvaluator,
            List<PlanningStrategy> plannerStrategies,
            Clock clock) {
        public Ports {
            Objects.requireNonNull(decisionDomain, "decisionDomain");
            Objects.requireNonNull(lifecycleDomain, "lifecycleDomain");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(interpreter, "interpreter");
            Objects.requireNonNull(worldModel, "worldModel");
            Objects.requireNonNull(candidateEvaluator, "candidateEvaluator");
            plannerStrategies = List.copyOf(Objects.requireNonNull(plannerStrategies, "plannerStrategies"));
            Objects.requireNonNull(clock, "clock");
            var actualIds = plannerStrategies.stream().map(PlanningStrategy::id).toList();
            if (actualIds.size() != PlannerId.values().length
                    || actualIds.stream().distinct().count() != PlannerId.values().length
                    || !actualIds.containsAll(List.of(PlannerId.values()))) {
                throw new IllegalArgumentException("full-graph evaluation requires exactly one strategy for every PlannerId: "
                        + List.of(PlannerId.values()));
            }
        }
    }

    /** Compile a deliberately inert graph to verify that the full application composition is wired. */
    public CompiledGraph<CognitiveCycleState> compileFailClosedProbe() throws GraphStateException {
        return compileFull(failClosedProbePorts());
    }

    /**
     * Compile the real application graph using caller-supplied, explicit scenario adapters.
     * No action handlers are accepted or registered by this test host.
     */
    public CompiledGraph<CognitiveCycleState> compileFull(Ports ports) throws GraphStateException {
        Objects.requireNonNull(ports, "ports");
        var workingMemoryRepository = new InMemoryWorkingMemoryRepository();
        var episodicRepository = new InMemoryEpisodeRepository();
        var retrieval = new MemoryRetrievalService(workingMemoryRepository, episodicRepository,
                new InMemorySemanticFactRepository(), new InMemoryProceduralMemoryRepository());
        var decisionServices = new CognitiveCycleDecisionServices(
                retrieval,
                new B1MemoryCandidateFactory(),
                new B1RetrievalSelector(new RecencyFrequencyActivationEstimator(0.5, 1.0),
                        new ExponentialActivationLatencyModel(100, 0.5, 1, 10_000)),
                new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()),
                new WorkspaceBroadcastService(Map.of()),
                new PlannerRouter(ports.plannerStrategies(), new DefaultPlannerSelectionPolicy()),
                ports.worldModel(), ports.candidateEvaluator(), new InMemoryPlanningArtifactStore(),
                ports.decisionDomain(), ports.clock());

        // The same repository instances back write/read memory services and decision-side retrieval.
        // This prevents a test host from accidentally validating two disconnected in-memory stores.
        var actionRegistry = new ActionRegistry();
        var lifecycleServices = new CognitiveCycleLifecycleServices(
                new WorkingMemoryService(workingMemoryRepository, Duration.ofDays(1)),
                new EpisodicMemoryService(episodicRepository, Duration.ofDays(7)),
                new ImpasseResolutionService(new DeterministicImpasseDetector(), new InMemoryImpasseCaseRepository()),
                new RecoveryIntentionService(new InMemoryRecoveryIntentionRepository()),
                new MetaControlGovernor(new MetaControlPolicy(0.4, 0.8, 0.2)),
                new ActionDispatcher(actionRegistry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger()),
                actionRegistry, ports.interpreter(), decisionServices, ports.lifecycleDomain(), ports.authority(),
                new InMemoryAuthorizedActionPlanStore(), new InMemoryImpasseChildArtifactStore(),
                new InMemoryActionExecutionBatchStore(), new InMemoryObservationStore(), ports.clock());
        return CognitiveCycleApplicationGraph.compileFull(decisionServices, lifecycleServices, new MemorySaver());
    }

    private static Ports failClosedProbePorts() {
        Clock clock = Clock.fixed(HOST_TIME, ZoneOffset.UTC);
        var strategies = List.of(PlannerId.COT_SEQUENTIAL, PlannerId.TOT_BOUNDED_SEARCH,
                PlannerId.RAP_INSPIRED_MCTS).stream().<PlanningStrategy>map(id -> new PlanningStrategy() {
                    @Override public PlannerId id() { return id; }
                    @Override public PlanningResult plan(PlanningRequest request) {
                        throw unexpected("planner." + id);
                    }
                }).toList();
        PlanningWorldModel world = new PlanningWorldModel() {
            @Override public String configurationFingerprint() { return "m8-b1-fail-closed-probe-world-v1"; }
            @Override public PlanningState initialState(PlanningRequest request) { throw unexpected("world.initialState"); }
            @Override public List<PlanningAction> availableActions(PlanningRequest request, PlanningState state) {
                throw unexpected("world.availableActions");
            }
            @Override public WorldTransition apply(PlanningRequest request, PlanningState state, PlanningAction action) {
                throw unexpected("world.apply");
            }
        };
        CandidateEvaluator evaluator = new CandidateEvaluator() {
            @Override public String configurationFingerprint() { return "m8-b1-fail-closed-probe-evaluator-v1"; }
            @Override public PlanEvaluation evaluate(PlanningRequest request, PlanningState state) {
                throw unexpected("candidateEvaluator.evaluate");
            }
        };
        RequestInterpreter interpreter = request -> new RequestInterpretation(
                InterpretationOutcome.READY, request.originalText(), "M8-B1 fail-closed composition probe");
        return new Ports(failClosed(CognitiveCycleDecisionPort.class), failClosed(CognitiveCycleLifecyclePort.class),
                failClosed(CognitiveCycleAuthorityPort.class), interpreter, world, evaluator, strategies, clock);
    }

    private static <T> T failClosed(Class<T> portType) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "FailClosed(" + portType.getSimpleName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> throw unexpected("Object." + method.getName());
                };
            }
            throw new UnsupportedOperationException("M8-B1 composition probe requires an explicit scenario adapter before invoking "
                    + portType.getSimpleName() + "." + method.getName());
        };
        return portType.cast(Proxy.newProxyInstance(portType.getClassLoader(), new Class<?>[]{portType}, handler));
    }

    private static UnsupportedOperationException unexpected(String operation) {
        return new UnsupportedOperationException("M8-B1 fail-closed composition probe did not configure operation: " + operation);
    }
}
