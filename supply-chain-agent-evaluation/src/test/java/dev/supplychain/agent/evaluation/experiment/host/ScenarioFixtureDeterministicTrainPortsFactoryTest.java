package dev.supplychain.agent.evaluation.experiment.host;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioFixtureDeterministicTrainPortsFactoryTest {
    @Test
    void buildsFreshDeterministicPortsForEachMatchedArm() throws Exception {
        var unit = M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN).stream()
                .filter(value -> value.fixtureProfile().equals("MULTI_ALTERNATIVE")).findFirst().orElseThrow();
        var factory = new ScenarioFixtureDeterministicTrainPortsFactory();
        var baseline = factory.create(unit, ExperimentMechanism.B1_MEMORY_LEARNING, ExperimentArm.BASELINE);
        var treatment = factory.create(unit, ExperimentMechanism.B1_MEMORY_LEARNING, ExperimentArm.TREATMENT);

        factory.executionProfile().requireAdmissibleForTrain();
        assertEquals(DeterministicTrainExecutionProfile.ModelExecution.DISABLED,
                factory.executionProfile().modelExecution());
        assertNotSame(baseline.ports(), treatment.ports());
        assertNotSame(baseline.ports().decisionDomain(), treatment.ports().decisionDomain());
        assertNotSame(baseline.ports().lifecycleDomain(), treatment.ports().lifecycleDomain());
        assertNotSame(baseline.ports().authority(), treatment.ports().authority());
        assertNotSame(baseline.ports().actionRegistry(), treatment.ports().actionRegistry());
        assertNotSame(baseline.ports().workspaceConsumers().get("planner"),
                treatment.ports().workspaceConsumers().get("planner"));
        assertNotSame(baseline.ports().worldModel(), treatment.ports().worldModel());
        assertNotSame(baseline.ports().candidateEvaluator(), treatment.ports().candidateEvaluator());
        assertEquals(baseline.ports().actionRegistry().descriptors(), treatment.ports().actionRegistry().descriptors());
        assertEquals(baseline.ports().workspaceConsumers().keySet(), treatment.ports().workspaceConsumers().keySet());
        assertNotEquals(baseline.conditionFingerprint(), treatment.conditionFingerprint());
        assertNotSame(baseline.sandboxAudit(), treatment.sandboxAudit());
        assertNotSame(baseline.metricCollector(), treatment.metricCollector());
        assertNotNull(baseline.ports().r2Workflow());
        assertNotSame(baseline.ports().r2Workflow().recoveryDraftRepository(),
                treatment.ports().r2Workflow().recoveryDraftRepository());
        assertNotSame(baseline.ports().r2Workflow().b3OutcomeEvidenceRepository(),
                treatment.ports().r2Workflow().b3OutcomeEvidenceRepository());
    }

    @Test
    void rejectsNonTrainSplitBeforeBuildingPorts() {
        var holdout = M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.HOLDOUT).getFirst();
        var factory = new ScenarioFixtureDeterministicTrainPortsFactory();
        assertThrows(SecurityException.class,
                () -> factory.create(holdout, ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION, ExperimentArm.BASELINE));
    }
}
