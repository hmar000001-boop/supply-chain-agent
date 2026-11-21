package dev.supplychain.agent.mechanisms.b3;

import java.util.Objects;

/** Capability minted only by B3 after exact receipt and verifier revalidation. */
public final class VerifiedWorkspaceCoalitionOutcomeEvidence {
    private final WorkspaceCoalitionOutcomeEvidence evidence;
    VerifiedWorkspaceCoalitionOutcomeEvidence(WorkspaceCoalitionOutcomeEvidence evidence,String experimentUnitFingerprint){
        this.evidence=Objects.requireNonNull(evidence,"evidence");
        if(!Objects.requireNonNull(experimentUnitFingerprint,"experimentUnitFingerprint").equals(evidence.experimentUnitFingerprint()))
            throw new SecurityException("verified coalition outcome unit differs from receipt-bound unit");
    }
    public WorkspaceCoalitionOutcomeEvidence evidence(){return evidence;}
    public String experimentUnitFingerprint(){return evidence.experimentUnitFingerprint();}
}
