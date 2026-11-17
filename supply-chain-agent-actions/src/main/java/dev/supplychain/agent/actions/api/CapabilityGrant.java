package dev.supplychain.agent.actions.api;

import java.util.Objects;
import java.util.Set;

/** An explicit all-resource or allow-listed resource grant. Empty scoped grants are forbidden. */
public record CapabilityGrant(Capability capability, boolean allResources, Set<ResourceReference> resources) {
    public CapabilityGrant {
        capability = Objects.requireNonNull(capability, "capability");
        resources = Set.copyOf(Objects.requireNonNull(resources, "resources"));
        if (allResources && !resources.isEmpty()) throw new IllegalArgumentException("all-resource grant cannot also list resources");
        if (!allResources && resources.isEmpty()) throw new IllegalArgumentException("scoped grant requires at least one resource");
    }
    public static CapabilityGrant all(Capability capability) { return new CapabilityGrant(capability, true, Set.of()); }
    public static CapabilityGrant scoped(Capability capability, Set<ResourceReference> resources) {
        return new CapabilityGrant(capability, false, resources);
    }
    public boolean covers(Set<ResourceReference> requested) {
        Objects.requireNonNull(requested, "requested");
        return allResources || (!requested.isEmpty() && resources.containsAll(requested));
    }
}
