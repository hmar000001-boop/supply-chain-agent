package dev.supplychain.agent.actions.api;

import java.util.Objects;
import java.util.Set;

/** Authenticated caller identity plus trusted, already-issued capabilities. */
public record ActionPrincipal(String principalId, Set<CapabilityGrant> grants) {
    public ActionPrincipal {
        principalId = Objects.requireNonNull(principalId, "principalId").trim();
        if (principalId.isEmpty()) throw new IllegalArgumentException("principalId cannot be blank");
        grants = Set.copyOf(Objects.requireNonNull(grants, "grants"));
    }
}
