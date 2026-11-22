package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M8VerifiedEndpointEvidenceTest {
    private static final String UNIT = "1".repeat(64);
    private static final String SOURCE = "2".repeat(64);

    @Test
    void endpointFingerprintAndDirectionAreReproducible() {
        var evidence = M8VerifiedEndpointEvidence.create(
                M8VerifiedEndpointEvidence.EndpointId.H1_VERIFIED_USEFULNESS_PER_RETRIEVAL_BUDGET,
                ExperimentSplit.HOLDOUT, "HOLDOUT-001", UNIT, "profile-a", "family-a",
                0.50, 0.60, "independent-verifier-v1", SOURCE);
        assertEquals(0.10, evidence.orientedImprovement(), 1e-12);
        assertEquals(64, evidence.evidenceFingerprint().length());
    }

    @Test
    void rejectsTrainEvidenceAndRatesOutsideTheUnitInterval() {
        assertThrows(SecurityException.class, () -> M8VerifiedEndpointEvidence.create(
                M8VerifiedEndpointEvidence.EndpointId.H1_VERIFIED_USEFULNESS_PER_RETRIEVAL_BUDGET,
                ExperimentSplit.TRAIN, "TRAIN-001", UNIT, "profile-a", "family-a",
                0.50, 0.60, "independent-verifier-v1", SOURCE));
        assertThrows(IllegalArgumentException.class, () -> M8VerifiedEndpointEvidence.create(
                M8VerifiedEndpointEvidence.EndpointId.H1_VERIFIED_USEFULNESS_PER_RETRIEVAL_BUDGET,
                ExperimentSplit.HOLDOUT, "HOLDOUT-001", UNIT, "profile-a", "family-a",
                0.50, 1.01, "independent-verifier-v1", SOURCE));
    }
}
