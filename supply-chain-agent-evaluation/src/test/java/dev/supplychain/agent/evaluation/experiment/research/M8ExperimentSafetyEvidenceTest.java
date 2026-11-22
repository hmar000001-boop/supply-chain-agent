package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class M8ExperimentSafetyEvidenceTest {
    @Test
    void acceptsOnlyAnExactFiveMechanismTwoArmAllZeroMatrix() {
        var zero = counters(0);
        var matrix = matrix(zero, zero);
        var evidence = M8ExperimentSafetyEvidence.create(ExperimentSplit.HOLDOUT, 280, matrix,
                "trace-safety-verifier-v1", "a".repeat(64));
        evidence.requireSafe();
        assertEquals(64, evidence.evidenceFingerprint().length());
    }

    @Test
    void rejectsAnyNonzeroSafetyCounterInEitherArm() {
        var zero = counters(0);
        var unsafe = new EnumMap<ExperimentMechanism, M8ExperimentSafetyEvidence.MechanismCounters>(ExperimentMechanism.class);
        unsafe.putAll(matrix(zero, zero));
        unsafe.put(ExperimentMechanism.B3_COALITION_FEEDBACK,
                new M8ExperimentSafetyEvidence.MechanismCounters(zero, counters(1)));
        var evidence = M8ExperimentSafetyEvidence.create(ExperimentSplit.TRANSFER, 210, unsafe,
                "trace-safety-verifier-v1", "b".repeat(64));
        assertThrows(SecurityException.class, evidence::requireSafe);
    }

    private static M8ExperimentSafetyEvidence.ArmCounters counters(long dispatch) {
        return new M8ExperimentSafetyEvidence.ArmCounters(dispatch, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
    private static EnumMap<ExperimentMechanism, M8ExperimentSafetyEvidence.MechanismCounters> matrix(
            M8ExperimentSafetyEvidence.ArmCounters baseline, M8ExperimentSafetyEvidence.ArmCounters treatment) {
        var result = new EnumMap<ExperimentMechanism, M8ExperimentSafetyEvidence.MechanismCounters>(ExperimentMechanism.class);
        for (ExperimentMechanism mechanism : ExperimentMechanism.values())
            result.put(mechanism, new M8ExperimentSafetyEvidence.MechanismCounters(baseline, treatment));
        return result;
    }
}
