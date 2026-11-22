package dev.supplychain.agent.evaluation.experiment.host;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSession;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicTrainExperimentHostTest {
    private static final String CANDIDATE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyScriptedOrDisabledDeterministicExecutionProfiles() {
        var profile = validProfile(DeterministicTrainExecutionProfile.ModelExecution.SCRIPTED_DETERMINISTIC);
        profile.requireAdmissibleForTrain();
        assertEquals("m8-deterministic-train-v1", profile.profileId());

        var live = validProfile(DeterministicTrainExecutionProfile.ModelExecution.LIVE_MODEL);
        assertThrows(SecurityException.class, live::requireAdmissibleForTrain);
    }

    @Test
    void refusesNonDeterministicPlanningEvenWhenWorldSimulatorIsDeterministic() {
        var profile = new DeterministicTrainExecutionProfile("profile", "scripted-interpreter",
                "scripted-reasoner", "planner-set-v1", "supply-chain-simulator-v1",
                DeterministicTrainExecutionProfile.ModelExecution.SCRIPTED_DETERMINISTIC,
                true, true, false, true, true, true);
        assertThrows(SecurityException.class, profile::requireAdmissibleForTrain);
    }

    @Test
    void hostValidatesProfileWithoutCreatingAnyGraphSession() {
        var calls = new AtomicInteger();
        DeterministicTrainSessionFactory factory = new DeterministicTrainSessionFactory() {
            @Override
            public DeterministicTrainExecutionProfile executionProfile() {
                return validProfile(DeterministicTrainExecutionProfile.ModelExecution.DISABLED);
            }

            @Override
            public FullGraphExperimentSession create(ExperimentScenarioUnit unit,
                    ExperimentMechanism mechanism, ExperimentArm arm) {
                calls.incrementAndGet();
                throw new AssertionError("host construction must not execute experiments");
            }
        };

        var host = new DeterministicTrainExperimentHost(CANDIDATE, factory);
        assertEquals(CANDIDATE, host.candidateCodeFingerprint());
        assertEquals("m8-deterministic-train-v1", host.executionProfile().profileId());
        assertEquals(0, calls.get());
    }

    @Test
    void refusesToReuseExistingOutputDirectoryBeforeGraphExecution() throws Exception {
        var calls = new AtomicInteger();
        DeterministicTrainSessionFactory factory = new DeterministicTrainSessionFactory() {
            @Override
            public DeterministicTrainExecutionProfile executionProfile() {
                return validProfile(DeterministicTrainExecutionProfile.ModelExecution.DISABLED);
            }

            @Override
            public FullGraphExperimentSession create(ExperimentScenarioUnit unit,
                    ExperimentMechanism mechanism, ExperimentArm arm) {
                calls.incrementAndGet();
                throw new AssertionError("existing-output preflight must happen before graph creation");
            }
        };
        var host = new DeterministicTrainExperimentHost(CANDIDATE, factory);
        Path existing = Files.createDirectory(temporaryDirectory.resolve("already-used"));
        assertThrows(java.io.IOException.class, () -> host.runAll(existing));
        assertTrue(Files.isDirectory(existing));
        assertEquals(0, calls.get());
    }

    private static DeterministicTrainExecutionProfile validProfile(
            DeterministicTrainExecutionProfile.ModelExecution modelExecution) {
        return new DeterministicTrainExecutionProfile("m8-deterministic-train-v1",
                "scripted-request-interpreter-v1", "scripted-reasoning-v1", "planner-set-v1",
                "deterministic-supply-chain-simulator-v1", modelExecution,
                true, true, true, true, true, true);
    }
}
