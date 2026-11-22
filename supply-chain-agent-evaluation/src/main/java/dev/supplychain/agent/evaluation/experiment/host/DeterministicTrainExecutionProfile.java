package dev.supplychain.agent.evaluation.experiment.host;

import java.util.Objects;

/**
 * Auditable declaration of the execution profile used by the primary deterministic M8 TRAIN
 * track. It is deliberately separate from the sandbox attestation: a run must be both
 * deterministic/model-controlled and disconnected from operational side effects.
 */
public record DeterministicTrainExecutionProfile(
        String profileId,
        String requestInterpreterId,
        String reasoningProfileId,
        String plannerProfileId,
        String worldSimulatorProfileId,
        ModelExecution modelExecution,
        boolean deterministicRequestInterpretation,
        boolean deterministicReasoning,
        boolean deterministicPlanning,
        boolean deterministicWorldSimulation,
        boolean externalConnectorsDisabled,
        boolean productionCredentialsAbsent) {

    public enum ModelExecution {
        DISABLED,
        SCRIPTED_DETERMINISTIC,
        LIVE_MODEL
    }

    public DeterministicTrainExecutionProfile {
        profileId = required(profileId, "profileId");
        requestInterpreterId = required(requestInterpreterId, "requestInterpreterId");
        reasoningProfileId = required(reasoningProfileId, "reasoningProfileId");
        plannerProfileId = required(plannerProfileId, "plannerProfileId");
        worldSimulatorProfileId = required(worldSimulatorProfileId, "worldSimulatorProfileId");
        modelExecution = Objects.requireNonNull(modelExecution, "modelExecution");
    }

    /** Rejects any host configuration that could contaminate the deterministic primary track. */
    public void requireAdmissibleForTrain() {
        if (modelExecution == ModelExecution.LIVE_MODEL
                || !deterministicRequestInterpretation
                || !deterministicReasoning
                || !deterministicPlanning
                || !deterministicWorldSimulation
                || !externalConnectorsDisabled
                || !productionCredentialsAbsent) {
            throw new SecurityException("primary M8 TRAIN requires scripted/disabled model execution, deterministic planning and simulation, and an isolated credential-free host");
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
