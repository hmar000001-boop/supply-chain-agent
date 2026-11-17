package dev.supplychain.agent.mechanisms.b1;

import java.time.Duration;

@FunctionalInterface
public interface CognitiveRetrievalLatencyModel { Duration predict(ActivationEstimate activation); }
