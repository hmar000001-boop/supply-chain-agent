package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ImpasseSignal(ImpasseType type, ImpasseSeverity severity, String subjectReference,
                            String summary, List<EvidenceReference> evidence, Instant detectedAt) {
    public ImpasseSignal {
        type=Objects.requireNonNull(type,"type"); severity=Objects.requireNonNull(severity,"severity");
        subjectReference=require(subjectReference,"subjectReference"); summary=require(summary,"summary");
        evidence=List.copyOf(Objects.requireNonNull(evidence,"evidence")); detectedAt=Objects.requireNonNull(detectedAt,"detectedAt");
    }
    public String stableKey() { return type.name()+":"+subjectReference; }
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
