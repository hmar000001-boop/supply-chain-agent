package dev.supplychain.agent.evaluation.experiment.research;

/** Trusted host adapter that verifies CI provenance, exact commit/tree identity and clean Git state. */
public interface M8FreezePrerequisiteVerifier {
    String verifierId();
    void verify(M8TrainingArtifactFreezeService.FreezeContext context);
}
