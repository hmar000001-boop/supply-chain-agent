package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.mechanism.MechanismTrainingArtifactBundle;

/** Durable full-content storage contract used by the TRAIN freeze service. */
public interface M8TrainingArtifactArchive {
    StoredArtifact store(MechanismTrainingArtifactBundle bundle) throws Exception;
    MechanismTrainingArtifactBundle load(StoredArtifact stored) throws Exception;

    record StoredArtifact(String archiveName, String contentSha256, String bundleFingerprint,
            long byteCount, String codecId) {
        public StoredArtifact {
            archiveName = java.util.Objects.requireNonNull(archiveName, "archiveName");
            contentSha256 = requireSha(contentSha256, "contentSha256");
            bundleFingerprint = requireSha(bundleFingerprint, "bundleFingerprint");
            if (byteCount < 1) throw new IllegalArgumentException("byteCount must be positive");
            codecId = java.util.Objects.requireNonNull(codecId, "codecId");
        }
        private static String requireSha(String value, String name) {
            String result = java.util.Objects.requireNonNull(value, name).trim();
            if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256");
            return result;
        }
    }
}
