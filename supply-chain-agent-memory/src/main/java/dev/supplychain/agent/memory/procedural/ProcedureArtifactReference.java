package dev.supplychain.agent.memory.procedural;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/** Versioned reference to an immutable artifact; prompts/secrets/source code are not stored in memory records. */
public record ProcedureArtifactReference(URI location, String sha256) {
    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");

    public ProcedureArtifactReference {
        location = Objects.requireNonNull(location, "location");
        sha256 = Objects.requireNonNull(sha256, "sha256").trim().toLowerCase(java.util.Locale.ROOT);
        if (!SHA256.matcher(sha256).matches()) throw new IllegalArgumentException("sha256 must contain 64 hexadecimal characters");
        if (!location.isAbsolute() && !location.getPath().startsWith("/")) {
            throw new IllegalArgumentException("artifact location must be absolute or repository-rooted");
        }
    }
}
