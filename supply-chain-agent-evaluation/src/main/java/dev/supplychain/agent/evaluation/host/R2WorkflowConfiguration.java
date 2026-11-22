package dev.supplychain.agent.evaluation.host;

import dev.supplychain.agent.mechanisms.b2.RecoveryDraftEvidenceVerifier;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureApprovalAuthority;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraftRepository;
import dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceRepository;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidenceVerifier;
import dev.supplychain.agent.mechanisms.b3.ActiveB3CoalitionFeedbackPolicy;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeAdjustment;
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
        ActiveOutcomeFeedbackPolicy activeFeedbackPolicy,
        ActiveB3CoalitionFeedbackPolicy activeCoalitionFeedbackPolicy,
        WorkspaceCoalitionOutcomeAdjustment experimentShadowCoalitionFeedbackAdjustment) {
    public R2WorkflowConfiguration {
        Objects.requireNonNull(recoveryDraftRepository, "recoveryDraftRepository");
        Objects.requireNonNull(recoveryDraftEvidenceVerifier, "recoveryDraftEvidenceVerifier");
        Objects.requireNonNull(recoveryProcedureApprovalAuthority, "recoveryProcedureApprovalAuthority");
        Objects.requireNonNull(recoveryDraftRequestFactory, "recoveryDraftRequestFactory");
        Objects.requireNonNull(b3OutcomeEvidenceRepository, "b3OutcomeEvidenceRepository");
        Objects.requireNonNull(downstreamOutcomeEvidenceVerifier, "downstreamOutcomeEvidenceVerifier");
        admissionPolicyVersion = required(admissionPolicyVersion, "admissionPolicyVersion");
        outcomeVerifierId = required(outcomeVerifierId, "outcomeVerifierId");
        if (activeCoalitionFeedbackPolicy != null && experimentShadowCoalitionFeedbackAdjustment != null)
            throw new SecurityException("active B3 feedback and experimental SHADOW overlay cannot be wired together");
    }

    /** Compatibility constructor before the experiment-only SHADOW overlay was introduced. */
    public R2WorkflowConfiguration(RecoveryProcedureDraftRepository recoveryDraftRepository,
            RecoveryDraftEvidenceVerifier recoveryDraftEvidenceVerifier,
            RecoveryProcedureApprovalAuthority recoveryProcedureApprovalAuthority,
            RecoveryProcedureDraftRequestFactory recoveryDraftRequestFactory,
            B3OutcomeEvidenceRepository b3OutcomeEvidenceRepository,
            DownstreamOutcomeEvidenceVerifier downstreamOutcomeEvidenceVerifier,
            String admissionPolicyVersion, String outcomeVerifierId,
            ActiveOutcomeFeedbackPolicy activeFeedbackPolicy,
            ActiveB3CoalitionFeedbackPolicy activeCoalitionFeedbackPolicy) {
        this(recoveryDraftRepository, recoveryDraftEvidenceVerifier, recoveryProcedureApprovalAuthority,
                recoveryDraftRequestFactory, b3OutcomeEvidenceRepository, downstreamOutcomeEvidenceVerifier,
                admissionPolicyVersion, outcomeVerifierId, activeFeedbackPolicy, activeCoalitionFeedbackPolicy, null);
    }

    /** Compatibility constructor before coalition-level B3 feedback was added. */
    public R2WorkflowConfiguration(RecoveryProcedureDraftRepository recoveryDraftRepository,
            RecoveryDraftEvidenceVerifier recoveryDraftEvidenceVerifier,
            RecoveryProcedureApprovalAuthority recoveryProcedureApprovalAuthority,
            RecoveryProcedureDraftRequestFactory recoveryDraftRequestFactory,
            B3OutcomeEvidenceRepository b3OutcomeEvidenceRepository,
            DownstreamOutcomeEvidenceVerifier downstreamOutcomeEvidenceVerifier,
            String admissionPolicyVersion, String outcomeVerifierId, ActiveOutcomeFeedbackPolicy activeFeedbackPolicy) {
        this(recoveryDraftRepository, recoveryDraftEvidenceVerifier, recoveryProcedureApprovalAuthority,
                recoveryDraftRequestFactory, b3OutcomeEvidenceRepository, downstreamOutcomeEvidenceVerifier,
                admissionPolicyVersion, outcomeVerifierId, activeFeedbackPolicy, null, null);
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
