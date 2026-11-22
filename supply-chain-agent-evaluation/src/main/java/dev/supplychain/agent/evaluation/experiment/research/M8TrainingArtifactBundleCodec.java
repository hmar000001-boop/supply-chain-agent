package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.mechanism.MechanismTrainingArtifactBundle;

/** Host-supplied, versioned codec for the complete typed TRAIN bundle (not just its fingerprint). */
public interface M8TrainingArtifactBundleCodec {
    String codecId();
    byte[] encode(MechanismTrainingArtifactBundle bundle) throws Exception;
    MechanismTrainingArtifactBundle decode(byte[] archiveBytes) throws Exception;
}
