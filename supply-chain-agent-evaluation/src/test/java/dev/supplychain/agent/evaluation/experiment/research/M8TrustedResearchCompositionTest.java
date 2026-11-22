package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.research.M8VerifiedEndpointEvidence.EndpointId;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class M8TrustedResearchCompositionTest {
    @TempDir Path temporary;

    @Test
    void composesPinnedIndependentVerifiersAndChecksEndpointAndSafetySignatures() throws Exception {
        Path keys = Files.createDirectory(temporary.resolve("keys"));
        Path attestations = Files.createDirectory(temporary.resolve("attestations"));
        String[] issuers = {"endpoint-v1", "safety-v1", "ci-v1", "b1-v1", "b2-v1", "b3-v1", "b6-v1"};
        var keyPairs = new java.util.HashMap<String, KeyPair>();
        for (String issuer : issuers) {
            KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            keyPairs.put(issuer, keyPair);
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            Files.writeString(keys.resolve(issuer + ".pub"), pem, StandardCharsets.US_ASCII);
        }

        var composition = M8TrustedResearchComposition.open(new M8TrustedResearchComposition.Configuration(
                temporary, keys, attestations, temporary.resolve("archive"),
                "endpoint-v1", "safety-v1", "ci-v1", "b1-v1", "b2-v1", "b3-v1", "b6-v1",
                "freeze-verifier-v1", "bundle-verifier-v1", Clock.systemUTC(), Duration.ofDays(30)));

        assertEquals("freeze-verifier-v1", composition.freezePrerequisiteVerifier().verifierId());
        assertEquals("bundle-verifier-v1", composition.trainingArtifactVerifier().verifierId());
        assertEquals("m8-record-graph-v1", composition.bundleCodec().codecId());
        assertNotNull(composition.trainingArtifactArchive());

        var endpoint = M8VerifiedEndpointEvidence.create(EndpointId.H1_VERIFIED_USEFULNESS_PER_RETRIEVAL_BUDGET,
                ExperimentSplit.HOLDOUT, "unit-1", "1".repeat(64), "profile-a", "family-a",
                0.2, 0.4, "endpoint-v1", "2".repeat(64));
        writeSigned(attestations, keyPairs.get("endpoint-v1"), "M8_ENDPOINT_HOLDOUT", endpoint.evidenceFingerprint(), "endpoint-v1");
        composition.endpointVerifier().verify(endpoint);

        var zero = new M8ExperimentSafetyEvidence.ArmCounters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var counters = new EnumMap<ExperimentMechanism, M8ExperimentSafetyEvidence.MechanismCounters>(ExperimentMechanism.class);
        for (ExperimentMechanism mechanism : ExperimentMechanism.values())
            counters.put(mechanism, new M8ExperimentSafetyEvidence.MechanismCounters(zero, zero));
        var safety = M8ExperimentSafetyEvidence.create(ExperimentSplit.TRAIN, 280, counters, "safety-v1", "3".repeat(64));
        writeSigned(attestations, keyPairs.get("safety-v1"), "M8_SAFETY_TRAIN", safety.evidenceFingerprint(), "safety-v1");
        composition.safetyVerifier().verify(safety);

        var tampered = M8VerifiedEndpointEvidence.create(EndpointId.H1_VERIFIED_USEFULNESS_PER_RETRIEVAL_BUDGET,
                ExperimentSplit.HOLDOUT, "unit-1", "1".repeat(64), "profile-a", "family-a",
                0.2, 0.5, "endpoint-v1", "2".repeat(64));
        assertThrows(SecurityException.class, () -> composition.endpointVerifier().verify(tampered));
    }

    @Test
    void rejectsDifferentIssuerIdsThatReuseTheSamePublicKeyMaterial() throws Exception {
        Path keys = Files.createDirectory(temporary.resolve("keys-reused"));
        Path attestations = Files.createDirectory(temporary.resolve("attestations-reused"));
        String[] issuers = {"endpoint-v1", "safety-v1", "ci-v1", "b1-v1", "b2-v1", "b3-v1", "b6-v1"};
        KeyPair shared = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(shared.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        for (String issuer : issuers) {
            Files.writeString(keys.resolve(issuer + ".pub"), pem, StandardCharsets.US_ASCII);
        }
        assertThrows(SecurityException.class, () -> M8TrustedResearchComposition.open(
                new M8TrustedResearchComposition.Configuration(temporary, keys, attestations,
                        temporary.resolve("archive-reused"), "endpoint-v1", "safety-v1", "ci-v1",
                        "b1-v1", "b2-v1", "b3-v1", "b6-v1", "freeze-v1", "bundle-v1",
                        Clock.systemUTC(), Duration.ofDays(30))));
    }

    @Test
    void rejectsMissingOrUnexpectedPinnedPublicKeys() throws Exception {
        Path keys = Files.createDirectory(temporary.resolve("keys-empty"));
        assertThrows(SecurityException.class, () -> M8PinnedEd25519PublicKeyLoader.load(keys, Set.of("missing-v1")));
    }

    private static void writeSigned(Path root, KeyPair keyPair, String type, String subject, String issuer) throws Exception {
        String placeholder = Base64.getEncoder().encodeToString(new byte[64]);
        var unsigned = new M8SignedAttestation(type, subject, issuer, java.time.Instant.now(), placeholder);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(unsigned.signedPayload());
        var signed = new M8SignedAttestation(type, subject, issuer, unsigned.issuedAt(),
                Base64.getEncoder().encodeToString(signer.sign()));
        String fileName = type + "--" + subject + ".attestation";
        Files.write(root.resolve(fileName), FileSystemM8SignedAttestationRepository.encode(signed));
    }
}
