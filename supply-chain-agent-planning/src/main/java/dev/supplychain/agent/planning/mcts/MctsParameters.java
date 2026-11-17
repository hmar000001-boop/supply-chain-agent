package dev.supplychain.agent.planning.mcts;

public record MctsParameters(double explorationCoefficient, int rolloutDepth,
                             int checkpointEveryIterations, long deterministicSeed) {
    public MctsParameters {
        if (!Double.isFinite(explorationCoefficient) || explorationCoefficient <= 0) throw new IllegalArgumentException("explorationCoefficient must be positive");
        if (rolloutDepth < 1 || checkpointEveryIterations < 1) throw new IllegalArgumentException("MCTS depth and checkpoint interval must be positive");
    }
}
