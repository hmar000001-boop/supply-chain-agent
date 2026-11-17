package dev.supplychain.agent.memory.semantic;

import dev.supplychain.agent.memory.common.Provenance;

/** Default policy never lets model-generated or unverified documents become active semantic facts. */
@FunctionalInterface
public interface SemanticAdmissionPolicy {
    boolean mayActivateWithoutHumanReview(Provenance provenance);

    static SemanticAdmissionPolicy conservativeDefault() {
        return provenance -> switch (provenance.source()) {
            case AUTHORITATIVE_SYSTEM, HUMAN_VERIFIED -> true;
            case VERIFIED_DOCUMENT, VERIFIED_EPISODE, SIMULATOR, UNVERIFIED_DOCUMENT, MODEL_DERIVED -> false;
        };
    }
}
