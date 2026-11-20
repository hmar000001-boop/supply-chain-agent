package dev.supplychain.agent.mechanisms.b3;

/** Only a separately verified downstream outcome is eligible for feedback; a receipt is not an outcome. */
public enum DownstreamOutcomeSignal {
    USEFUL,
    HARMFUL,
    NEUTRAL
}
