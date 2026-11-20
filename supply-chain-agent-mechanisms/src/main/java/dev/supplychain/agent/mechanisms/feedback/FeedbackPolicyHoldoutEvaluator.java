package dev.supplychain.agent.mechanisms.feedback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Paired holdout gate; training cycles may not appear in the holdout set. */
public final class FeedbackPolicyHoldoutEvaluator {
    public FeedbackPolicyEvaluationReport evaluate(OutcomeFeedbackPolicyCandidate candidate,
            List<FeedbackEvaluationObservation> holdout,FeedbackEvaluationThreshold threshold,Instant evaluatedAt) {
        Objects.requireNonNull(candidate,"candidate");Objects.requireNonNull(holdout,"holdout");
        Objects.requireNonNull(threshold,"threshold");Objects.requireNonNull(evaluatedAt,"evaluatedAt");
        if (evaluatedAt.isBefore(candidate.trainedAt()))
            throw new IllegalArgumentException("evaluation cannot predate candidate training");
        var ordered=holdout.stream().map(v->Objects.requireNonNull(v,"observation"))
                .sorted(Comparator.comparing(FeedbackEvaluationObservation::scenarioId)).toList();
        Set<String> ids=ordered.stream().map(FeedbackEvaluationObservation::scenarioId).collect(java.util.stream.Collectors.toSet());
        if(ids.size()!=ordered.size())throw new IllegalArgumentException("holdout scenario IDs must be unique");
        if(!java.util.Collections.disjoint(candidate.trainingCycleIds(),ids))
            throw new SecurityException("training-cycle overlap detected in held-out evaluation");
        double mean=0.0,variance=0.0,resource=0.0;int safety=0;
        for(var observation:ordered){mean+=observation.utilityDelta();resource+=observation.resourceDelta();safety+=observation.safetyRegression();}
        int n=ordered.size();if(n>0){mean/=n;resource/=n;for(var observation:ordered){double d=observation.utilityDelta()-mean;variance+=d*d;}variance=n>1?variance/(n-1):0.0;}
        // A low-sample report is SHADOW-only; keep every serialized metric finite.
        // Zero is a neutral placeholder, never an eligibility signal, because the
        // minimum sample gate below prevents activation with fewer than 2 observations.
        double lower=n>1?mean-1.96*Math.sqrt(variance/n):0.0;
        PolicyDisposition decision;String reason;
        if(n<threshold.minimumHoldoutScenarios()){decision=PolicyDisposition.SHADOW;reason="insufficient independent held-out scenarios";}
        else if(safety>threshold.maximumSafetyRegressions()){decision=PolicyDisposition.REJECTED;reason="candidate exceeded preregistered safety-regression limit";}
        else if(resource>threshold.maximumMeanResourceIncrease()){decision=PolicyDisposition.REJECTED;reason="candidate exceeded preregistered resource-cost limit";}
        else if(lower<threshold.minimumMeanUtilityGain()){decision=PolicyDisposition.REJECTED;reason="candidate did not clear the preregistered lower-confidence-bound utility threshold";}
        else {decision=PolicyDisposition.ELIGIBLE_FOR_AUTHORIZED_ACTIVATION;reason="holdout criteria passed; separate trusted activation approval is still required";}
        // Bind the report to the complete candidate payload, scope and frozen baseline.
        // TreeMap is used because Map.copyOf does not promise iteration order.
        var canonicalAdjustments=new TreeMap<>(candidate.candidateAdjustments());
        String canonical=candidate.scopeId().value()+"|"+candidate.policyId()+"|"+candidate.version()+"|"
                +candidate.baselinePolicyVersion()+"|"+candidate.sourceFingerprint()+"|"+canonicalAdjustments+"|"
                +candidate.trainingCycleIds().stream().sorted().toList()+"|"+threshold+"|"+ordered;
        return new FeedbackPolicyEvaluationReport(candidate.policyId(),candidate.version(),threshold.protocolId(),n,mean,lower,safety,resource,
                decision,reason,sha256(canonical),evaluatedAt);
    }
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}}
}
