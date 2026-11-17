package dev.supplychain.agent.mechanisms.b3;

import java.util.List;
import java.util.Optional;

public record WorkspaceAdmissionOutcome(WorkspaceAdmissionStatus status,WorkspaceSnapshot snapshot,
                                        List<String> missingCoverage,String explanation){
    public WorkspaceAdmissionOutcome{missingCoverage=List.copyOf(missingCoverage);if(explanation==null||explanation.isBlank())throw new IllegalArgumentException("explanation cannot be blank");
        if(status==WorkspaceAdmissionStatus.INSUFFICIENT_MANDATORY_CAPACITY&&snapshot!=null)throw new IllegalArgumentException("failed mandatory admission cannot return a partial snapshot");
        if(status!=WorkspaceAdmissionStatus.INSUFFICIENT_MANDATORY_CAPACITY&&snapshot==null)throw new IllegalArgumentException("successful/partial admission requires snapshot");}
    public Optional<WorkspaceSnapshot> snapshotOptional(){return Optional.ofNullable(snapshot);}
}
