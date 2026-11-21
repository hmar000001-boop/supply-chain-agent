package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Canonical descriptor fingerprinting shared by draft creation, promotion and later retrieval. */
public final class RecoveryProcedureActionReferences {
    private RecoveryProcedureActionReferences() { }

    public static RegisteredRecoveryActionReference current(ActionRegistry registry, ActionId id) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(id, "id");
        return current(registry.descriptor(id));
    }

    public static RegisteredRecoveryActionReference current(ActionDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String canonical = descriptor.id().value() + "|" + descriptor.name() + "|" + descriptor.description()
                + "|" + descriptor.category() + "|" + descriptor.effect() + "|"
                + descriptor.requiredCapability() + "|" + descriptor.idempotencyRequired();
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return new RegisteredRecoveryActionReference(descriptor.id().value(), descriptor.requiredCapability(),
                    descriptor.effect(), fingerprint);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
