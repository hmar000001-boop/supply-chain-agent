package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.Objects;

public record B1MemoryCandidateReference(B1MemoryCandidateKind kind,MemoryScopeId scopeId,String itemId,long itemVersion){
    public B1MemoryCandidateReference{kind=Objects.requireNonNull(kind,"kind");scopeId=Objects.requireNonNull(scopeId,"scopeId");itemId=Objects.requireNonNull(itemId,"itemId").trim();if(itemId.isEmpty()||itemVersion<0)throw new IllegalArgumentException("invalid memory candidate reference");}
    public String candidateId(){return kind.name().toLowerCase(java.util.Locale.ROOT)+":"+itemId+":"+itemVersion;}
    public String memoryReference(){return kind.name().toLowerCase(java.util.Locale.ROOT)+":"+itemId+"@"+itemVersion;}
}
