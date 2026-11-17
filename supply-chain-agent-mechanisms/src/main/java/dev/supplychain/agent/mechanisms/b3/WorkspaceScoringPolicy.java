package dev.supplychain.agent.mechanisms.b3;

public record WorkspaceScoringPolicy(double riskPenaltyPoints,double coverageBonusPerUnit) {
    public WorkspaceScoringPolicy {if(!Double.isFinite(riskPenaltyPoints)||riskPenaltyPoints<0||!Double.isFinite(coverageBonusPerUnit)||coverageBonusPerUnit<0)throw new IllegalArgumentException("workspace scoring values must be finite and nonnegative");}
    public double baseScore(WorkspaceCandidate c){return c.utilityPoints()*c.reliability()-riskPenaltyPoints*c.risk();}
}
