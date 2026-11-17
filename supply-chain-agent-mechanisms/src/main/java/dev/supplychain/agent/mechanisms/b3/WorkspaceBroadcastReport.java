package dev.supplychain.agent.mechanisms.b3;

import java.util.List;

public record WorkspaceBroadcastReport(String workspaceId,List<WorkspaceConsumerReceipt> receipts){
    public WorkspaceBroadcastReport{if(workspaceId==null||workspaceId.isBlank())throw new IllegalArgumentException("workspaceId cannot be blank");receipts=List.copyOf(receipts);}
    public boolean allAccepted(){return !receipts.isEmpty()&&receipts.stream().allMatch(r->r.status()==ConsumerReceiptStatus.ACCEPTED);}
}
