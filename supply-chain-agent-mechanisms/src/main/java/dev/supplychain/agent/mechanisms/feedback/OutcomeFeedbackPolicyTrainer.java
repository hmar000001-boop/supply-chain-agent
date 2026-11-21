package dev.supplychain.agent.mechanisms.feedback;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeSignal;
import dev.supplychain.agent.mechanisms.b3.VerifiedDownstreamOutcomeEvidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Deterministic bounded outcome-to-score trainer. It cannot directly activate a policy. */
public final class OutcomeFeedbackPolicyTrainer {
    /**
     * Creates a deterministic, non-trained SHADOW placeholder when no verified outcome corpus
     * exists. This is deliberately separate from {@link #train}: it carries no source outcomes,
     * training cycles, experimental units or score adjustments and cannot pass activation.
     */
    public OutcomeFeedbackPolicyCandidate createNoEvidenceShadowCandidate(String policyId, long nextVersion,
            MemoryScopeId scopeId, String baselinePolicyVersion, int minimumObservationsPerCandidate,
            double maximumAbsoluteAdjustment, Instant createdAt) {
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (minimumObservationsPerCandidate < 1) {
            throw new IllegalArgumentException("minimumObservationsPerCandidate must be positive");
        }
        if (!Double.isFinite(maximumAbsoluteAdjustment) || maximumAbsoluteAdjustment <= 0
                || maximumAbsoluteAdjustment > 5.0) {
            throw new IllegalArgumentException("maximumAbsoluteAdjustment must be in (0,5]");
        }
        String canonical = "NO_VERIFIED_OUTCOME_CORPUS|" + scopeId.value() + "|" + policyId + "|"
                + nextVersion + "|" + baselinePolicyVersion + "|" + minimumObservationsPerCandidate + "|"
                + Double.toHexString(maximumAbsoluteAdjustment) + "|" + createdAt;
        return new OutcomeFeedbackPolicyCandidate(policyId, nextVersion, scopeId, baselinePolicyVersion,
                Map.of(), List.of(), java.util.Set.of(), java.util.Set.of(), minimumObservationsPerCandidate,
                maximumAbsoluteAdjustment, sha256(canonical), createdAt, PolicyDisposition.SHADOW);
    }

    public OutcomeFeedbackPolicyCandidate train(String policyId,long nextVersion,MemoryScopeId scopeId,
            String baselinePolicyVersion,List<VerifiedDownstreamOutcomeEvidence> verifiedOutcomes,
            int minimumObservationsPerCandidate,double maximumAbsoluteAdjustment,Instant trainedAt) {
        Objects.requireNonNull(scopeId,"scopeId");Objects.requireNonNull(verifiedOutcomes,"verifiedOutcomes");
        Objects.requireNonNull(trainedAt,"trainedAt");
        if(minimumObservationsPerCandidate<1)throw new IllegalArgumentException("minimumObservationsPerCandidate must be positive");
        if(!Double.isFinite(maximumAbsoluteAdjustment)||maximumAbsoluteAdjustment<=0||maximumAbsoluteAdjustment>5.0)
            throw new IllegalArgumentException("maximumAbsoluteAdjustment must be in (0,5]");
        var ordered=verifiedOutcomes.stream().map(v->Objects.requireNonNull(v,"verifiedOutcome").evidence())
                .sorted(Comparator.comparing(DownstreamOutcomeEvidence::idempotencyKey)).toList();
        if (ordered.stream().anyMatch(outcome -> !scopeId.equals(outcome.scopeId())))
            throw new SecurityException("outcome feedback training cannot mix memory scopes");
        if (ordered.stream().anyMatch(outcome -> outcome.verifiedAt().isAfter(trainedAt)))
            throw new IllegalArgumentException("training timestamp predates one or more verified outcomes");
        if (ordered.stream().map(DownstreamOutcomeEvidence::idempotencyKey).distinct().count()!=ordered.size())
            throw new IllegalArgumentException("verified outcome IDs must be unique");
        var grouped=ordered.stream().collect(Collectors.groupingBy(DownstreamOutcomeEvidence::candidateId,TreeMap::new,Collectors.toList()));
        var adjustments=new TreeMap<String,Double>();
        grouped.forEach((candidate,events)->{
            long useful=events.stream().filter(e->e.signal()==DownstreamOutcomeSignal.USEFUL).count();
            long harmful=events.stream().filter(e->e.signal()==DownstreamOutcomeSignal.HARMFUL).count();
            long total=events.size();
            if(total>=minimumObservationsPerCandidate) {
                double normalized=(useful-harmful)/(double)total;
                adjustments.put(candidate,Math.max(-maximumAbsoluteAdjustment,Math.min(maximumAbsoluteAdjustment,normalized*maximumAbsoluteAdjustment)));
            }
        });
        List<String> ids=ordered.stream().map(DownstreamOutcomeEvidence::idempotencyKey).toList();
        var cycles=ordered.stream().map(DownstreamOutcomeEvidence::cycleId).collect(Collectors.toUnmodifiableSet());
        var verifiedUnits=verifiedOutcomes.stream().map(VerifiedDownstreamOutcomeEvidence::experimentUnitFingerprint)
                .collect(Collectors.toUnmodifiableSet());
        if(verifiedUnits.size()<2) throw new IllegalArgumentException("adaptive training requires at least two distinct verified experimental units");
        String canonical=scopeId.value()+"|"+policyId+"|"+nextVersion+"|"+baselinePolicyVersion+"|"
                +ordered+"|"+cycles.stream().sorted().toList()+"|"+verifiedUnits.stream().sorted().toList()+"|"+new TreeMap<>(adjustments)+"|"
                +minimumObservationsPerCandidate+"|"+Double.toHexString(maximumAbsoluteAdjustment)+"|"+trainedAt;
        return new OutcomeFeedbackPolicyCandidate(policyId,nextVersion,scopeId,baselinePolicyVersion,adjustments,ids,cycles,verifiedUnits,
                minimumObservationsPerCandidate,maximumAbsoluteAdjustment,sha256(canonical),trainedAt,PolicyDisposition.SHADOW);
    }
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}}
}
