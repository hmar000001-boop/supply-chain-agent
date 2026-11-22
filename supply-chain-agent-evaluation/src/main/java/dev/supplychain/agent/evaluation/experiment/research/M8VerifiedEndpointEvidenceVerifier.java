package dev.supplychain.agent.evaluation.experiment.research;

/** Trusted verifier for endpoint values derived from the authoritative evidence ledger. */
public interface M8VerifiedEndpointEvidenceVerifier {
    String verifierId();
    void verify(M8VerifiedEndpointEvidence evidence);
}
