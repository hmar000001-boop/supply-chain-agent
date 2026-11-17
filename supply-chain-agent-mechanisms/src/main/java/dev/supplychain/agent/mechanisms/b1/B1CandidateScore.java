package dev.supplychain.agent.mechanisms.b1;

import java.time.Duration;

public record B1CandidateScore(String candidateId, double activation, double accessibilityProbability,
                               Duration predictedCognitiveLatency, Duration measuredInfrastructureLatency,
                               double expectedDownstreamUtility, double estimatedMaintenanceCost,
                               double netUtility, int contextUnits, boolean selected) { }
