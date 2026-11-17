package dev.supplychain.agent.actions.api;

import java.util.Set;

public final class CapabilityAuthorizer {
    public boolean isAuthorized(ActionPrincipal principal, Capability capability, Set<ResourceReference> resources) {
        if (principal == null || capability == null || resources == null) return false;
        return principal.grants().stream().filter(grant -> grant.capability() == capability)
                .anyMatch(grant -> grant.covers(resources));
    }
}
