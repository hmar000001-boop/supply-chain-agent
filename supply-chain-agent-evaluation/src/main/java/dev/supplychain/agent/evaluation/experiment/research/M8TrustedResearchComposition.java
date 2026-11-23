package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.mechanism.Ed25519MechanismTrainingArtifactVerifierServices;
import dev.supplychain.agent.evaluation.experiment.mechanism.MechanismTrainingArtifactVerifier;
import dev.supplychain.agent.evaluation.experiment.mechanism.SimulatorUncertaintyLabelVerifier;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Production composition root for the M8 trust boundary. All evidence verifiers share one
 * read-only attestation repository and one Ed25519 trust engine backed by externally provisioned,
 * pinned issuer public keys. This class does not generate attestations or create private keys.
 */
public final class M8TrustedResearchComposition {
    private final M8Ed25519AttestationTrust trust;
    private final Ed25519M8VerifiedEndpointEvidenceVerifier endpointVerifier;
    private final Ed25519M8ExperimentSafetyEvidenceVerifier safetyVerifier;
    private final GitCiM8FreezePrerequisiteVerifier freezePrerequisiteVerifier;
    private final MechanismTrainingArtifactVerifier trainingArtifactVerifier;
    private final SimulatorUncertaintyLabelVerifier simulatorLabelVerifier;
    private final M8TrainingArtifactBundleCodec bundleCodec;
    private final M8TrainingArtifactArchive trainingArtifactArchive;
    private final Map<String, String> pinnedIssuerKeyFingerprints;

    private M8TrustedResearchComposition(M8Ed25519AttestationTrust trust,
            Ed25519M8VerifiedEndpointEvidenceVerifier endpointVerifier,
            Ed25519M8ExperimentSafetyEvidenceVerifier safetyVerifier,
            GitCiM8FreezePrerequisiteVerifier freezePrerequisiteVerifier,
            MechanismTrainingArtifactVerifier trainingArtifactVerifier,
            SimulatorUncertaintyLabelVerifier simulatorLabelVerifier,
            M8TrainingArtifactBundleCodec bundleCodec,
            M8TrainingArtifactArchive trainingArtifactArchive,
            Map<String, String> pinnedIssuerKeyFingerprints) {
        this.trust = trust;
        this.endpointVerifier = endpointVerifier;
        this.safetyVerifier = safetyVerifier;
        this.freezePrerequisiteVerifier = freezePrerequisiteVerifier;
        this.trainingArtifactVerifier = trainingArtifactVerifier;
        this.simulatorLabelVerifier = simulatorLabelVerifier;
        this.bundleCodec = bundleCodec;
        this.trainingArtifactArchive = trainingArtifactArchive;
        this.pinnedIssuerKeyFingerprints = Map.copyOf(Objects.requireNonNull(
                pinnedIssuerKeyFingerprints, "pinnedIssuerKeyFingerprints"));
    }

    public static M8TrustedResearchComposition open(Configuration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        Set<String> issuerIds = Set.of(configuration.endpointIssuerId(), configuration.safetyIssuerId(),
                configuration.ciIssuerId(), configuration.b1OutcomeIssuerId(), configuration.b2ProcedureIssuerId(),
                configuration.b3OutcomeIssuerId(), configuration.b6SimulatorIssuerId());
        if (issuerIds.size() != 7)
            throw new IllegalArgumentException("endpoint, safety, CI, B1, B2, B3 and B6 issuers must be distinct identities");
        Map<String, PublicKey> keys = M8PinnedEd25519PublicKeyLoader.load(configuration.pinnedPublicKeyDirectory(), issuerIds);
        var uniqueKeyFingerprints = new java.util.HashSet<String>();
        var keyFingerprints = new java.util.TreeMap<String, String>();
        for (Map.Entry<String, PublicKey> entry : keys.entrySet()) {
            String fingerprint = M8CandidateCodeFingerprint.sha256(entry.getValue().getEncoded());
            if (!uniqueKeyFingerprints.add(fingerprint))
                throw new SecurityException("independent M8 issuer identities must not share the same Ed25519 public key: " + entry.getKey());
            keyFingerprints.put(entry.getKey(), fingerprint);
        }
        var repository = new FileSystemM8SignedAttestationRepository(configuration.attestationDirectory());
        var trust = new M8Ed25519AttestationTrust(repository, keys, configuration.clock(), configuration.maximumAttestationAge());
        var endpoint = new Ed25519M8VerifiedEndpointEvidenceVerifier(trust, configuration.endpointIssuerId());
        var safety = new Ed25519M8ExperimentSafetyEvidenceVerifier(trust, configuration.safetyIssuerId());
        var freeze = new GitCiM8FreezePrerequisiteVerifier(configuration.repositoryRoot(), trust,
                configuration.ciIssuerId(), configuration.freezePrerequisiteVerifierId());
        var services = new Ed25519MechanismTrainingArtifactVerifierServices(trust,
                configuration.b1OutcomeIssuerId(), configuration.b2ProcedureIssuerId(),
                configuration.b3OutcomeIssuerId(), configuration.b6SimulatorIssuerId()).create();
        var bundleVerifier = new MechanismTrainingArtifactVerifier(services, configuration.bundleVerifierId());
        var codec = new ReflectiveM8TrainingArtifactBundleCodec();
        var archive = new FileSystemM8TrainingArtifactArchive(configuration.trainingArchiveDirectory(), codec);
        return new M8TrustedResearchComposition(trust, endpoint, safety, freeze, bundleVerifier,
                services.b6LabelVerifier(), codec, archive, keyFingerprints);
    }

    public M8Ed25519AttestationTrust trust() { return trust; }
    public M8VerifiedEndpointEvidenceVerifier endpointVerifier() { return endpointVerifier; }
    public M8ExperimentSafetyEvidenceVerifier safetyVerifier() { return safetyVerifier; }
    public M8FreezePrerequisiteVerifier freezePrerequisiteVerifier() { return freezePrerequisiteVerifier; }
    public MechanismTrainingArtifactVerifier trainingArtifactVerifier() { return trainingArtifactVerifier; }
    public SimulatorUncertaintyLabelVerifier simulatorLabelVerifier() { return simulatorLabelVerifier; }
    public M8TrainingArtifactBundleCodec bundleCodec() { return bundleCodec; }
    public M8TrainingArtifactArchive trainingArtifactArchive() { return trainingArtifactArchive; }
    /** SHA-256 fingerprints of the exact Ed25519 SubjectPublicKeyInfo pins loaded by this composition. */
    public Map<String, String> pinnedIssuerKeyFingerprints() { return pinnedIssuerKeyFingerprints; }

    public M8TrainingArtifactFreezeService freezeService(M8ExperimentArtifactIntegrityAuditor auditor) {
        return new M8TrainingArtifactFreezeService(Objects.requireNonNull(auditor, "auditor"),
                trainingArtifactVerifier, trainingArtifactArchive, freezePrerequisiteVerifier, safetyVerifier);
    }

    public record Configuration(Path repositoryRoot, Path pinnedPublicKeyDirectory,
            Path attestationDirectory, Path trainingArchiveDirectory,
            String endpointIssuerId, String safetyIssuerId, String ciIssuerId,
            String b1OutcomeIssuerId, String b2ProcedureIssuerId, String b3OutcomeIssuerId,
            String b6SimulatorIssuerId, String freezePrerequisiteVerifierId, String bundleVerifierId,
            Clock clock, Duration maximumAttestationAge) {
        public Configuration {
            repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
            pinnedPublicKeyDirectory = Objects.requireNonNull(pinnedPublicKeyDirectory, "pinnedPublicKeyDirectory").toAbsolutePath().normalize();
            attestationDirectory = Objects.requireNonNull(attestationDirectory, "attestationDirectory").toAbsolutePath().normalize();
            trainingArchiveDirectory = Objects.requireNonNull(trainingArchiveDirectory, "trainingArchiveDirectory").toAbsolutePath().normalize();
            endpointIssuerId = required(endpointIssuerId, "endpointIssuerId");
            safetyIssuerId = required(safetyIssuerId, "safetyIssuerId");
            ciIssuerId = required(ciIssuerId, "ciIssuerId");
            b1OutcomeIssuerId = required(b1OutcomeIssuerId, "b1OutcomeIssuerId");
            b2ProcedureIssuerId = required(b2ProcedureIssuerId, "b2ProcedureIssuerId");
            b3OutcomeIssuerId = required(b3OutcomeIssuerId, "b3OutcomeIssuerId");
            b6SimulatorIssuerId = required(b6SimulatorIssuerId, "b6SimulatorIssuerId");
            freezePrerequisiteVerifierId = required(freezePrerequisiteVerifierId, "freezePrerequisiteVerifierId");
            bundleVerifierId = required(bundleVerifierId, "bundleVerifierId");
            clock = Objects.requireNonNull(clock, "clock");
            maximumAttestationAge = Objects.requireNonNull(maximumAttestationAge, "maximumAttestationAge");
            if (maximumAttestationAge.isNegative() || maximumAttestationAge.isZero())
                throw new IllegalArgumentException("maximumAttestationAge must be positive");
        }
        private static String required(String value, String name) {
            String result = Objects.requireNonNull(value, name).trim();
            if (!result.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
                throw new IllegalArgumentException(name + " must be a safe stable identity token");
            return result;
        }
    }
}
