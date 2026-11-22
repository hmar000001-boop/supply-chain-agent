package dev.supplychain.agent.evaluation.experiment.research;

import java.io.IOException;
import java.util.Optional;

/** Read-only lookup for externally created and signed research attestations. */
public interface M8SignedAttestationRepository {
    Optional<M8SignedAttestation> find(String evidenceType, String subjectFingerprint) throws IOException;
}
