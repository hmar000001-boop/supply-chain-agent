package dev.supplychain.agent.memory.common;

/** Provenance classes are intentionally explicit; model-derived content is not authoritative fact. */
public enum ProvenanceSource {
    AUTHORITATIVE_SYSTEM,
    HUMAN_VERIFIED,
    VERIFIED_DOCUMENT,
    VERIFIED_EPISODE,
    SIMULATOR,
    UNVERIFIED_DOCUMENT,
    MODEL_DERIVED
}
