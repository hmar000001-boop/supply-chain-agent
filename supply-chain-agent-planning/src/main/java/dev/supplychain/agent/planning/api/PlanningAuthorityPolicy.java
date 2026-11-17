package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.actions.api.Capability;
import java.util.Objects;
import java.util.Set;

/** Trusted planning-scope and approval-policy snapshot; it is not an execution authorization grant. */
public record PlanningAuthorityPolicy(
        String policyVersion,
        Set<Capability> proposalScope,
        Set<Capability> approvalRequiredCapabilities) {
    public PlanningAuthorityPolicy {
        policyVersion = require(policyVersion);
        proposalScope = Set.copyOf(Objects.requireNonNull(proposalScope, "proposalScope"));
        approvalRequiredCapabilities = Set.copyOf(Objects.requireNonNull(approvalRequiredCapabilities, "approvalRequiredCapabilities"));
    }
    private static String require(String value) {
        String result = Objects.requireNonNull(value, "policyVersion").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("policyVersion cannot be blank");
        return result;
    }
}
