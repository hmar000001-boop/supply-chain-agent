package dev.supplychain.agent.evaluation.host;

import dev.supplychain.agent.mechanisms.b2.RecoveryDraftEvidenceVerifier;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureApprovalAuthority;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraftRepository;
import dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceRepository;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidenceVerifier;
import dev.supplychain.agent.mechanisms.feedback.ActiveOutcomeFeedbackPolicy;
import dev.supplychain.agent.runtime.cognitive.lifecycle.RecoveryProcedureDraftRequestFactory;
import java.util.Objects;

/** Explicit R2 dependencies for the actual full cognitive-cycle graph composition. */
public record R2WorkflowConfiguration(
        RecoveryProcedureDraftRepository recoveryDraftRepository,
        RecoveryDraftEvidenceVerifier recoveryDraftEvidenceVerifier,
        RecoveryProcedureApprovalAuthority recoveryProcedureApprovalAuthority,
        RecoveryProcedureDraftRequestFactory recoveryDraftRequestFactory,
        B3OutcomeEvidenceRepository b3OutcomeEvidenceRepository,
        DownstreamOutcomeEvidenceVerifier downstreamOutcomeEvidenceVerifier,
        String admissionPolicyVersion, String outcomeVerifierId,
        ActiveOutcomeFeedbackPolicy activeFeedbackPolicy) {
    public R2WorkflowConfiguration {
        Objects.requireNonNull(recoveryDraftRepository, "recoveryDraftRepository");
        Objects.requireNonNull(recoveryDraftEvidenceVerifier, "recoveryDraftEvidenceVerifier");
        Objects.requireNonNull(recoveryProcedureApprovalAuthority, "recoveryProcedureApprovalAuthority");
        Objects.requireNonNull(recoveryDraftRequestFactory, "recoveryDraftRequestFactory");
        Objects.requireNonNull(b3OutcomeEvidenceRepository, "b3OutcomeEvidenceRepository");
        Objects.requireNonNull(downstreamOutcomeEvidenceVerifier, "downstreamOutcomeEvidenceVerifier");
        admissionPolicyVersion = required(admissionPolicyVersion, "admissionPolicyVersion");
        outcomeVerifierId = required(outcomeVerifierId, "outcomeVerifierId");
    }
    private static String required(String value, String name) {
        String result=Objects.requireNonNull(value,name).trim();
        if(result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
