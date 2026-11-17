package dev.supplychain.agent.mechanisms.b3;

import java.time.Instant;
import java.util.List;

public record WorkspaceConsumerReceipt(String consumerId,String workspaceId,ConsumerReceiptStatus status,
                                       List<String> consumedCandidateIds,Instant receivedAt,String detail){
    public WorkspaceConsumerReceipt{if(consumerId==null||consumerId.isBlank()||workspaceId==null||workspaceId.isBlank())throw new IllegalArgumentException("consumer/workspace IDs cannot be blank");consumedCandidateIds=List.copyOf(consumedCandidateIds);if(status==null||receivedAt==null||detail==null||detail.isBlank())throw new IllegalArgumentException("receipt fields are required");}
}
