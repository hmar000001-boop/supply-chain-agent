package dev.supplychain.agent.evaluation.experiment.research;

/** Host-independent verifier that reconstructs the safety counter matrix from graph/action/audit traces. */
public interface M8ExperimentSafetyEvidenceVerifier {
    String verifierId();
    void verify(M8ExperimentSafetyEvidence evidence);
}
