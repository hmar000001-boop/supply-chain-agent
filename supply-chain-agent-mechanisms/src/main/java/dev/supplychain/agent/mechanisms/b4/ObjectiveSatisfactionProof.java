package dev.supplychain.agent.mechanisms.b4;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ObjectiveSatisfactionProof(String objectiveFingerprint,List<EvidenceReference> evidence,
                                         String verifier, String verificationPolicy,Instant verifiedAt){
    public ObjectiveSatisfactionProof{if(objectiveFingerprint==null||objectiveFingerprint.isBlank())throw new IllegalArgumentException("objectiveFingerprint cannot be blank");evidence=List.copyOf(Objects.requireNonNull(evidence,"evidence"));if(evidence.isEmpty())throw new IllegalArgumentException("completion requires evidence");verifier=require(verifier,"verifier");verificationPolicy=require(verificationPolicy,"verificationPolicy");verifiedAt=Objects.requireNonNull(verifiedAt,"verifiedAt");}
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
