package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import java.time.Instant;

/**
 * Independently operated evidence-verification dependencies for TRAIN artifact admission.
 * None has a permissive default: study execution cannot start without the relevant verifier.
 */
public record MechanismTrainingArtifactVerificationServices(
        B1OutcomeVerifier b1OutcomeVerifier,
        B2ProcedureVerifier b2ProcedureVerifier,
        B3CoalitionOutcomeVerifier b3CoalitionOutcomeVerifier,
        SimulatorUncertaintyLabelVerifier b6LabelVerifier) {

    @FunctionalInterface
    public interface B1OutcomeVerifier {
        boolean verify(DownstreamOutcomeEvidence outcome);
    }

    @FunctionalInterface
    public interface B2ProcedureVerifier {
        boolean verify(ReusableRecoveryProcedure procedure, Instant verificationTime);
    }

    @FunctionalInterface
    public interface B3CoalitionOutcomeVerifier {
        boolean verify(WorkspaceCoalitionOutcomeEvidence outcome);
    }
}
