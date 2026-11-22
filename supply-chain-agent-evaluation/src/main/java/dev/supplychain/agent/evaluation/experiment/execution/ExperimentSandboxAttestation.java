package dev.supplychain.agent.evaluation.experiment.execution;

import java.util.Objects;

/**
 * Explicit declaration by a trusted evaluation harness that one graph session is disposable and
 * disconnected from operational side effects. The adapter independently checks the run-time audit
 * counters after graph execution; this declaration is not itself treated as proof of zero effects.
 */
public record ExperimentSandboxAttestation(
        String attestationId,
        boolean inMemoryOnly,
        boolean externalConnectorsDisabled,
        boolean productionCredentialsAbsent,
        boolean stateChangingDispatchRequiresApproval) {
    public ExperimentSandboxAttestation {
        attestationId = required(attestationId, "attestationId");
        if (!inMemoryOnly || !externalConnectorsDisabled || !productionCredentialsAbsent
                || !stateChangingDispatchRequiresApproval) {
            throw new SecurityException("full-graph experiment requires an isolated in-memory sandbox with external effects disabled");
        }
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
