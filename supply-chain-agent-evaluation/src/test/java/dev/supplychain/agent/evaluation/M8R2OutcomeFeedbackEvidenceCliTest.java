package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.evaluation.app.M8R2OutcomeFeedbackEvidenceCli;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M8R2OutcomeFeedbackEvidenceCliTest {
    @Test void emptyEvidenceProducesStableShadowOnlyArtifactAndPreservesBaseline() throws Exception {
        Path first = Files.createTempDirectory("m8-r2-evidence-a-");
        Path second = Files.createTempDirectory("m8-r2-evidence-b-");
        try {
            var firstResult = M8R2OutcomeFeedbackEvidenceCli.generate(first);
            var secondResult = M8R2OutcomeFeedbackEvidenceCli.generate(second);
            assertEquals(0, firstResult.verifiedOutcomeCount());
            assertEquals(0, firstResult.holdoutScenarioCount());
            assertEquals("SHADOW", firstResult.disposition());
            assertEquals("DISABLED", firstResult.activationStatus());
            assertEquals(firstResult.evaluationFingerprint(), secondResult.evaluationFingerprint());
            String json = Files.readString(first.resolve("m8-r2-outcome-feedback-evidence.json"));
            assertTrue(json.contains("NO_VERIFIED_OUTCOME_CORPUS"));
            assertTrue(json.contains("\"baselinePreserved\": true"));
            assertTrue(json.contains("\"empiricalAdvantageEstablished\": false"));
            assertFalse(json.contains("ELIGIBLE_FOR_AUTHORIZED_ACTIVATION"));
            assertEquals(json, Files.readString(second.resolve("m8-r2-outcome-feedback-evidence.json")));
            assertTrue(Files.size(first.resolve("SHA256SUMS.txt")) > 0);
        } finally {
            deleteTree(first);
            deleteTree(second);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
