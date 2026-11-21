package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Outcome-trained coalition preferences always begin in SHADOW and cannot be used for admission directly. */
public record B3CoalitionFeedbackCandidate(String policyId,long version,MemoryScopeId scopeId,
        Map<String,Double> preferenceAdjustments,List<String> sourceOutcomeIds,Set<String> trainingUnitFingerprints,
        int minimumObservationsPerCoalition,double maximumAbsoluteAdjustment,String sourceFingerprint,
        Instant trainedAt,Disposition disposition) {
    public enum Disposition { SHADOW }
    public B3CoalitionFeedbackCandidate {
        policyId=req(policyId,"policyId");if(version<1)throw new IllegalArgumentException("version must be positive");
        scopeId=Objects.requireNonNull(scopeId,"scopeId");var copy=new TreeMap<String,Double>();
        Objects.requireNonNull(preferenceAdjustments,"preferenceAdjustments").forEach((key,value)->{
            if(!sha(key)||value==null||!Double.isFinite(value)||Math.abs(value)>maximumAbsoluteAdjustment+1e-9)
                throw new IllegalArgumentException("invalid coalition preference adjustment");copy.put(key,value);
        });preferenceAdjustments=Map.copyOf(copy);
        sourceOutcomeIds=List.copyOf(Objects.requireNonNull(sourceOutcomeIds,"sourceOutcomeIds"));
        if(sourceOutcomeIds.stream().anyMatch(v->v==null||v.isBlank())||sourceOutcomeIds.stream().distinct().count()!=sourceOutcomeIds.size())
            throw new IllegalArgumentException("source outcomes must be unique");
        trainingUnitFingerprints=Set.copyOf(Objects.requireNonNull(trainingUnitFingerprints,"trainingUnitFingerprints"));
        if(trainingUnitFingerprints.size()<2||trainingUnitFingerprints.stream().anyMatch(v->!sha(v)))
            throw new IllegalArgumentException("coalition training requires at least two distinct SHA-256 experiment units");
        if(minimumObservationsPerCoalition<1||!Double.isFinite(maximumAbsoluteAdjustment)||maximumAbsoluteAdjustment<=0||maximumAbsoluteAdjustment>5)
            throw new IllegalArgumentException("invalid coalition training limits");
        sourceFingerprint=req(sourceFingerprint,"sourceFingerprint");if(!sha(sourceFingerprint))throw new IllegalArgumentException("source fingerprint must be SHA-256");
        trainedAt=Objects.requireNonNull(trainedAt,"trainedAt");disposition=Objects.requireNonNull(disposition,"disposition");
        if(disposition!=Disposition.SHADOW)throw new SecurityException("trained coalition candidates must start in SHADOW");
    }
    private static boolean sha(String v){return v!=null&&v.matches("[0-9a-f]{64}");}
    private static String req(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
