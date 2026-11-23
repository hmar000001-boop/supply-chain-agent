package dev.supplychain.agent.app.durable;

import dev.supplychain.agent.mechanisms.b2.RecoveryDraftEvidenceVerifier;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureApprovalAuthority;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidenceVerifier;
import dev.supplychain.agent.mechanisms.b3.ActiveB3CoalitionFeedbackPolicy;
import dev.supplychain.agent.mechanisms.feedback.ActiveOutcomeFeedbackPolicy;
import dev.supplychain.agent.runtime.cognitive.lifecycle.RecoveryProcedureDraftRequestFactory;

import java.util.Objects;

/**
 * Trusted application policy ports for B2/B3 durable evidence. The host supplies the PostgreSQL
 * repositories itself; this record contains only independently governed verifier/authority policy.
 */
public record DurableR2Configuration(
        RecoveryDraftEvidenceVerifier recoveryDraftEvidenceVerifier,
        RecoveryProcedureApprovalAuthority recoveryProcedureApprovalAuthority,
        RecoveryProcedureDraftRequestFactory recoveryDraftRequestFactory,
        DownstreamOutcomeEvidenceVerifier downstreamOutcomeEvidenceVerifier,
        String admissionPolicyVersion,
        String outcomeVerifierId,
        ActiveOutcomeFeedbackPolicy activeB1FeedbackPolicy,
        ActiveB3CoalitionFeedbackPolicy activeB3CoalitionFeedbackPolicy) {
    public DurableR2Configuration {
        Objects.requireNonNull(recoveryDraftEvidenceVerifier, "recoveryDraftEvidenceVerifier");
        Objects.requireNonNull(recoveryProcedureApprovalAuthority, "recoveryProcedureApprovalAuthority");
        Objects.requireNonNull(recoveryDraftRequestFactory, "recoveryDraftRequestFactory");
        Objects.requireNonNull(downstreamOutcomeEvidenceVerifier, "downstreamOutcomeEvidenceVerifier");
        admissionPolicyVersion = required(admissionPolicyVersion, "admissionPolicyVersion");
        outcomeVerifierId = required(outcomeVerifierId, "outcomeVerifierId");
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
