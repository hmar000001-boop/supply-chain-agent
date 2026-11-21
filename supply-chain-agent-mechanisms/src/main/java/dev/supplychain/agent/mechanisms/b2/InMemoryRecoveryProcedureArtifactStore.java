package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.procedural.ProcedureArtifactReference;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test/evaluation adapter; production hosts should use the immutable PostgreSQL artifact adapter. */
public final class InMemoryRecoveryProcedureArtifactStore implements RecoveryProcedureArtifactStore {
    private final RecoveryProcedureArtifactCodec codec = new RecoveryProcedureArtifactCodec();
    private final ConcurrentHashMap<String, byte[]> artifacts = new ConcurrentHashMap<>();

    @Override public ProcedureArtifactReference publish(RecoveryProcedureArtifact artifact) {
        byte[] bytes = codec.encode(artifact);
        String digest = sha256(bytes);
        byte[] existing = artifacts.putIfAbsent(digest, bytes.clone());
        if (existing != null && !java.util.Arrays.equals(existing, bytes)) {
            throw new SecurityException("content-addressed recovery artifact digest collision");
        }
        return new ProcedureArtifactReference(URI.create("urn:sha256:" + digest), digest);
    }

    @Override public Optional<RecoveryProcedureArtifact> retrieve(ProcedureArtifactReference reference, MemoryScopeId expectedScope) {
        java.util.Objects.requireNonNull(reference, "reference");
        java.util.Objects.requireNonNull(expectedScope, "expectedScope");
        if (!reference.location().toString().equals("urn:sha256:" + reference.sha256()))
            throw new SecurityException("recovery artifact URI and digest do not match");
        byte[] stored = artifacts.get(reference.sha256());
        if (stored == null) return Optional.empty();
        byte[] bytes = stored.clone();
        if (!sha256(bytes).equals(reference.sha256())) throw new SecurityException("recovery artifact digest verification failed");
        RecoveryProcedureArtifact artifact = codec.decode(bytes);
        if (!artifact.scopeId().equals(expectedScope)) throw new SecurityException("recovery artifact belongs to another memory scope");
        return Optional.of(artifact);
    }

    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
