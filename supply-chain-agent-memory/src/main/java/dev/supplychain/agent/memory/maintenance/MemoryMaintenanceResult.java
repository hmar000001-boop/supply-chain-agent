package dev.supplychain.agent.memory.maintenance;

public record MemoryMaintenanceResult(int workingMemoryDeleted, int unverifiedEpisodesDeleted,
                                      int supersededFactsDeleted) {
    public MemoryMaintenanceResult {
        if (workingMemoryDeleted < 0 || unverifiedEpisodesDeleted < 0 || supersededFactsDeleted < 0) {
            throw new IllegalArgumentException("deleted counts cannot be negative");
        }
    }
    public int totalDeleted() { return workingMemoryDeleted + unverifiedEpisodesDeleted + supersededFactsDeleted; }
}
