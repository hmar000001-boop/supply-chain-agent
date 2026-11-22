package dev.supplychain.agent.evaluation.experiment.research;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Freeze gate that re-reads Git commit/tree state and verifies a signed hosted-CI attestation.
 * The boolean values in FreezeContext are treated as claims only; this verifier checks them
 * against the repository and a separately provisioned Ed25519 CI issuer.
 */
public final class GitCiM8FreezePrerequisiteVerifier implements M8FreezePrerequisiteVerifier {
    private final Path repositoryRoot;
    private final M8Ed25519AttestationTrust trust;
    private final String ciIssuerId;
    private final String verifierId;

    public GitCiM8FreezePrerequisiteVerifier(Path repositoryRoot, M8Ed25519AttestationTrust trust,
            String ciIssuerId, String verifierId) {
        this.repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
        this.trust = Objects.requireNonNull(trust, "trust");
        this.ciIssuerId = required(ciIssuerId, "ciIssuerId");
        this.verifierId = required(verifierId, "verifierId");
    }

    @Override public String verifierId() { return verifierId; }

    @Override public void verify(M8TrainingArtifactFreezeService.FreezeContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.hostedCiGreen() || !"SUCCESS".equals(context.ciConclusion()) || !context.gitWorkingTreeClean())
            throw new SecurityException("freeze context does not claim a successful CI run and clean Git tree");
        try {
            if (!Files.isDirectory(repositoryRoot)) throw new SecurityException("repository root is absent");
            String head = git("rev-parse", "HEAD").trim();
            if (!head.equals(context.gitCommit())) throw new SecurityException("freeze commit does not match the checked-out Git HEAD");
            String status = git("status", "--porcelain", "--untracked-files=all");
            if (!status.isBlank()) throw new SecurityException("Git working tree is not clean");
            String actualCodeFingerprint = M8CandidateCodeFingerprint.compute(repositoryRoot);
            if (!actualCodeFingerprint.equals(context.candidateCodeFingerprint()))
                throw new SecurityException("candidate source fingerprint does not match the checked-out source tree");
            String dependencyFingerprint = M8CandidateCodeFingerprint.dependencyManifestFingerprint(repositoryRoot);
            if (!dependencyFingerprint.equals(context.dependencyManifestFingerprint()))
                throw new SecurityException("dependency manifest fingerprint does not match the checked-out POM files");
            trust.verify("M8_CI_FREEZE", attestationSubject(context), ciIssuerId);
        } catch (IOException failure) {
            throw new SecurityException("cannot verify Git/CI freeze prerequisites", failure);
        }
    }

    public static String attestationSubject(M8TrainingArtifactFreezeService.FreezeContext context) {
        Objects.requireNonNull(context, "context");
        String canonical = context.gitCommit() + "|" + context.ciWorkflowUrl() + "|" + context.ciRunId()
                + "|" + context.ciConclusion() + "|" + context.candidateCodeFingerprint()
                + "|" + context.dependencyManifestFingerprint() + "|" + context.gitWorkingTreeClean();
        return M8CandidateCodeFingerprint.sha256(canonical);
    }

    private String git(String... arguments) throws IOException {
        var command = new java.util.ArrayList<String>();
        command.add("git"); command.add("-C"); command.add(repositoryRoot.toString());
        command.addAll(java.util.List.of(arguments));
        Path output = Files.createTempFile("m8-git-freeze-", ".out");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    throw new IOException("git command timed out: " + String.join(" ", arguments));
                }
            } catch (InterruptedException interrupted) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while verifying Git state", interrupted);
            }
            String text = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0)
                throw new IOException("git command failed: " + text);
            return text;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw new IllegalArgumentException(name + " is invalid");
        return result;
    }
}
