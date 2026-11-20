package dev.supplychain.agent.mechanisms.feedback;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Versioned candidate policy trained only from verified B3 downstream outcomes; always starts in SHADOW. */
public record OutcomeFeedbackPolicyCandidate(String policyId,long version,MemoryScopeId scopeId,String baselinePolicyVersion,
        Map<String,Double> candidateAdjustments,List<String> sourceOutcomeIds,Set<String> trainingCycleIds,
        int minimumObservationsPerCandidate,double maximumAbsoluteAdjustment,String sourceFingerprint,
        Instant trainedAt,PolicyDisposition disposition) {
    public OutcomeFeedbackPolicyCandidate {
        policyId=required(policyId,"policyId");if(version<1)throw new IllegalArgumentException("policy version must be positive");
        scopeId=Objects.requireNonNull(scopeId,"scopeId");
        baselinePolicyVersion=required(baselinePolicyVersion,"baselinePolicyVersion");
        if(minimumObservationsPerCandidate<1)throw new IllegalArgumentException("minimumObservationsPerCandidate must be positive");
        if(!Double.isFinite(maximumAbsoluteAdjustment)||maximumAbsoluteAdjustment<=0||maximumAbsoluteAdjustment>5.0)
            throw new IllegalArgumentException("maximumAbsoluteAdjustment must be in (0,5]");
        var copy=new TreeMap<String,Double>();Objects.requireNonNull(candidateAdjustments,"candidateAdjustments").forEach((id,value)->{
            String candidateId=required(id,"candidateId");
            if(value==null||!Double.isFinite(value)||Math.abs(value)>maximumAbsoluteAdjustment+1e-9)
                throw new IllegalArgumentException("candidate adjustment is invalid or exceeds the configured bound");copy.put(candidateId,value);
        });candidateAdjustments=Map.copyOf(copy);
        sourceOutcomeIds=List.copyOf(Objects.requireNonNull(sourceOutcomeIds,"sourceOutcomeIds"));
        if(sourceOutcomeIds.stream().anyMatch(id->id==null||id.isBlank())||sourceOutcomeIds.stream().distinct().count()!=sourceOutcomeIds.size())
            throw new IllegalArgumentException("source outcome IDs must be nonblank and unique");
        trainingCycleIds=Set.copyOf(Objects.requireNonNull(trainingCycleIds,"trainingCycleIds"));
        if(trainingCycleIds.stream().anyMatch(id->id==null||id.isBlank()))throw new IllegalArgumentException("training cycle IDs must be nonblank");
        sourceFingerprint=required(sourceFingerprint,"sourceFingerprint");
        if(!sourceFingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("sourceFingerprint must be SHA-256");
        trainedAt=Objects.requireNonNull(trainedAt,"trainedAt");
        disposition=Objects.requireNonNull(disposition,"disposition");
        if(disposition!=PolicyDisposition.SHADOW)throw new SecurityException("trained candidates must start in SHADOW; activation is a separate authorized operation");
    }
    /** Shadow-only candidate lookup; scope is mandatory even before the policy is activated. */
    public double adjustmentFor(MemoryScopeId requestedScope,String candidateId) {
        Objects.requireNonNull(requestedScope,"requestedScope");
        return scopeId.equals(requestedScope)?candidateAdjustments.getOrDefault(candidateId,0.0):0.0;
    }
    private static String required(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
