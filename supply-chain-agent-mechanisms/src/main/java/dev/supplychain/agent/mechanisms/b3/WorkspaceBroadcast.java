package dev.supplychain.agent.mechanisms.b3;

import java.time.Instant;
import java.util.Objects;

public record WorkspaceBroadcast(WorkspaceSnapshot snapshot,String consumerId,Instant broadcastAt){
    public WorkspaceBroadcast{snapshot=Objects.requireNonNull(snapshot,"snapshot");consumerId=require(consumerId,"consumerId");broadcastAt=Objects.requireNonNull(broadcastAt,"broadcastAt");if(snapshot.status()!=WorkspaceStatus.ADMITTED)throw new IllegalStateException("only admitted workspace can be broadcast");}
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
