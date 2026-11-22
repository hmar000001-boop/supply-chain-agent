package dev.supplychain.agent.evaluation.experiment.host;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentSandboxAttestation;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSession;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.host.FullGraphEvaluationHost;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import org.bsc.langgraph4j.RunnableConfig;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Concrete LangGraph4j session composer for the deterministic TRAIN track. The only scenario-
 * specific dependency is the trusted factory that supplies fresh deterministic domain/authority
 * ports. This class itself supplies the full application graph composition, checkpoint isolation,
 * sandbox attestation and canonical graph input.
 */
public final class ComposedDeterministicTrainSessionFactory implements DeterministicTrainSessionFactory {
    private final DeterministicTrainPortsFactory portsFactory;
    private final DeterministicTrainExecutionProfile executionProfile;
    private final FullGraphEvaluationHost graphHost;

    public ComposedDeterministicTrainSessionFactory(DeterministicTrainPortsFactory portsFactory) {
        this(portsFactory, new FullGraphEvaluationHost());
    }

    public ComposedDeterministicTrainSessionFactory(DeterministicTrainPortsFactory portsFactory,
            FullGraphEvaluationHost graphHost) {
        this.portsFactory = Objects.requireNonNull(portsFactory, "portsFactory");
        this.executionProfile = Objects.requireNonNull(portsFactory.executionProfile(), "executionProfile");
        this.executionProfile.requireAdmissibleForTrain();
        this.graphHost = Objects.requireNonNull(graphHost, "graphHost");
    }

    @Override
    public DeterministicTrainExecutionProfile executionProfile() {
        return executionProfile;
    }

    @Override
    public FullGraphExperimentSession create(ExperimentScenarioUnit unit,
            ExperimentMechanism mechanism, ExperimentArm arm) throws Exception {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(arm, "arm");
        if (unit.split() != ExperimentSplit.TRAIN)
            throw new SecurityException("deterministic TRAIN session factory accepts TRAIN units only");
        if (!dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory.PROTOCOL_ID
                .equals(unit.protocolId()))
            throw new SecurityException("deterministic TRAIN session uses a foreign experiment protocol");

        DeterministicTrainSessionConfiguration configuration = Objects.requireNonNull(
                portsFactory.create(unit, mechanism, arm), "TRAIN ports factory returned null");
        String unique = unit.experimentUnitId() + ":" + mechanism.name() + ":" + arm.name() + ":" + UUID.randomUUID();
        String sessionId = "m8-train-session:" + unique;
        String isolationNamespace = "m8-train-sandbox:" + unique;
        String checkpointThreadId = "m8-train-checkpoint:" + unique;
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(checkpointThreadId).build();
        var fixtureRequest = unit.fixture().planningRequest();
        Map<String, Object> graphInput = Map.of(
                CognitiveCycleState.REQUEST_ID, "request:" + unit.experimentUnitId(),
                CognitiveCycleState.EXECUTION_ID, fixtureRequest.executionId(),
                CognitiveCycleState.MEMORY_SCOPE_ID, fixtureRequest.workingMemory().scopeId().value(),
                CognitiveCycleState.REQUEST, "Assess and recover the supply-chain disruption for "
                        + unit.experimentUnitId());
        var sandbox = new ExperimentSandboxAttestation("m8-train-attestation:" + UUID.randomUUID(),
                true, true, true, true);
        return FullGraphExperimentSession.compile(unit, mechanism, arm,
                sessionId, isolationNamespace, checkpointThreadId,
                configuration.conditionFingerprint(), graphHost, configuration.ports(),
                runnableConfig, graphInput, sandbox, configuration.sandboxAudit(),
                configuration.metricCollector(), null, configuration.b1FeatureSeeds(),
                configuration.b3ShadowAdjustment(), configuration.b2ProcedureSeeds());
    }
}
