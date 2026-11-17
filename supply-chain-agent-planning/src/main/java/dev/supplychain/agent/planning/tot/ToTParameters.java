package dev.supplychain.agent.planning.tot;

public record ToTParameters(int beamWidth, int branchingFactor, int checkpointEveryDepth) {
    public ToTParameters {
        if (beamWidth < 1 || branchingFactor < 1 || checkpointEveryDepth < 1) throw new IllegalArgumentException("ToT parameters must be positive");
    }
}
