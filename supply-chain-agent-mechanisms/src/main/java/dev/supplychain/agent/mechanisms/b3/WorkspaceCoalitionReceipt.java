package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact selected coalition proposal and member fingerprints captured with an accepted consumer receipt. */
public record WorkspaceCoalitionReceipt(String coalitionId, MemoryScopeId scopeId, String workspaceId,
        int round, String proposalFingerprint, String preferenceKey, Set<String> candidateIds,
        Map<String,String> candidateFingerprints, Set<String> jointCoverageTags,
        int contextUnits, String experimentUnitFingerprint) {
    public WorkspaceCoalitionReceipt {
        coalitionId=required(coalitionId,"coalitionId");scopeId=Objects.requireNonNull(scopeId,"scopeId");workspaceId=required(workspaceId,"workspaceId");
        if(round<0||contextUnits<2)throw new IllegalArgumentException("invalid coalition receipt accounting");
        proposalFingerprint=sha(proposalFingerprint,"proposalFingerprint");preferenceKey=sha(preferenceKey,"preferenceKey");
        candidateIds=Set.copyOf(Objects.requireNonNull(candidateIds,"candidateIds"));
        if(candidateIds.size()<2||candidateIds.stream().anyMatch(v->v==null||v.isBlank()))throw new IllegalArgumentException("coalition receipt requires at least two candidate IDs");
        var fingerprints=new java.util.TreeMap<String,String>();
        Objects.requireNonNull(candidateFingerprints,"candidateFingerprints").forEach((id,fp)->fingerprints.put(required(id,"candidate ID"),sha(fp,"candidate fingerprint")));
        if(!fingerprints.keySet().equals(candidateIds))throw new SecurityException("coalition receipt member fingerprints do not match its exact candidate set");
        candidateFingerprints=Map.copyOf(fingerprints);
        jointCoverageTags=Set.copyOf(Objects.requireNonNull(jointCoverageTags,"jointCoverageTags"));
        if(jointCoverageTags.size()<2||jointCoverageTags.stream().anyMatch(v->v==null||v.isBlank()))throw new IllegalArgumentException("coalition receipt requires joint coverage");
        experimentUnitFingerprint=experimentUnitFingerprint==null?"":experimentUnitFingerprint.trim();
        if(!experimentUnitFingerprint.isEmpty()&&!experimentUnitFingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("experiment unit fingerprint must be empty or SHA-256");
    }
    /** Compatibility constructor for stored/test callers that only knew exact proposal identity. */
    public WorkspaceCoalitionReceipt(String coalitionId,MemoryScopeId scopeId,String workspaceId,int round,
            String proposalFingerprint,Set<String> candidateIds,Map<String,String> candidateFingerprints,
            Set<String> jointCoverageTags,int contextUnits,String experimentUnitFingerprint) {
        this(coalitionId,scopeId,workspaceId,round,proposalFingerprint,proposalFingerprint,candidateIds,
                candidateFingerprints,jointCoverageTags,contextUnits,experimentUnitFingerprint);
    }
    private static String sha(String v,String n){String s=required(v,n);if(!s.matches("[0-9a-f]{64}"))throw new IllegalArgumentException(n+" must be SHA-256");return s;}
    private static String required(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
