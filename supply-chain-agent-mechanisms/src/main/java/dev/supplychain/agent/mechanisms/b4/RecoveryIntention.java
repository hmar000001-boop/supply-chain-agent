package dev.supplychain.agent.mechanisms.b4;

import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Persistent objective with mutable method reference; objective identity and constraints are immutable. */
public record RecoveryIntention(RecoveryIntentionId id,MemoryScopeId scopeId,RecoveryObjectiveId objectiveId,
                                String objectiveFingerprint,String objectiveOwner,Set<RecoveryConstraint> protectedConstraints,
                                RecoveryMethodReference currentMethod,IntentionStatus status,long version,
                                Instant createdAt,Instant updatedAt,String reason,IntentionStatus resumeStatus,
                                String delegationId,ObjectiveSatisfactionProof completionProof){
    public RecoveryIntention{
        id=Objects.requireNonNull(id,"id");scopeId=Objects.requireNonNull(scopeId,"scopeId");objectiveId=Objects.requireNonNull(objectiveId,"objectiveId");
        if(objectiveFingerprint==null||!objectiveFingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid objective fingerprint");
        objectiveOwner=require(objectiveOwner,"objectiveOwner");protectedConstraints=Set.copyOf(protectedConstraints);
        currentMethod=Objects.requireNonNull(currentMethod,"currentMethod");status=Objects.requireNonNull(status,"status");
        if(version<0)throw new IllegalArgumentException("version cannot be negative");createdAt=Objects.requireNonNull(createdAt,"createdAt");updatedAt=Objects.requireNonNull(updatedAt,"updatedAt");
        if(updatedAt.isBefore(createdAt))throw new IllegalArgumentException("updatedAt precedes createdAt");
        reason=reason==null?null:reason.trim();
        if((status==IntentionStatus.SUSPENDED)!=(resumeStatus!=null))throw new IllegalArgumentException("suspended intention must preserve its previous status");
        if((status==IntentionStatus.DELEGATED)!=(delegationId!=null))throw new IllegalArgumentException("delegation ID must exist only while delegated");
        if((status==IntentionStatus.COMPLETED)!=(completionProof!=null))throw new IllegalArgumentException("completion proof must exist exactly for completed intention");
        if(completionProof!=null&&!objectiveFingerprint.equals(completionProof.objectiveFingerprint()))throw new IllegalArgumentException("completion proof targets a different objective");
    }
    public static RecoveryIntention create(RecoveryObjective objective,MemoryScopeId scope,String owner,RecoveryMethodReference method,Instant at){
        return create(RecoveryIntentionId.random(), objective, scope, owner, method, at);
    }
    public static RecoveryIntention create(RecoveryIntentionId id, RecoveryObjective objective, MemoryScopeId scope,
                                           String owner, RecoveryMethodReference method, Instant at){
        Objects.requireNonNull(objective,"objective");
        if(objective.status()!=dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus.ACTIVE)throw new IllegalStateException("only an active recovery objective can create an intention");
        return new RecoveryIntention(Objects.requireNonNull(id,"id"),scope,objective.id(),ObjectiveFingerprint.from(objective).value(),owner,
                objective.hardConstraints(),method,IntentionStatus.ACTIVE,0,at,at,null,null,null,null);
    }
    public RecoveryIntention requestReconsideration(String why,Instant at){requireActive();return revise(IntentionStatus.RECONSIDERATION_PENDING,at,why,null,null,null);}
    public RecoveryIntention changeMethod(RecoveryMethodReference method,String assertedObjectiveFingerprint,Instant at){
        if(status!=IntentionStatus.RECONSIDERATION_PENDING)throw new IllegalStateException("method can change only after reconsideration is requested");
        if(!objectiveFingerprint.equals(assertedObjectiveFingerprint))throw new SecurityException("method change cannot alter the protected objective");
        return revise(IntentionStatus.ACTIVE,at,"method changed",null,null,Objects.requireNonNull(method,"method"));
    }
    public RecoveryIntention suspend(String why,Instant at){if(status!=IntentionStatus.ACTIVE&&status!=IntentionStatus.RECONSIDERATION_PENDING)throw new IllegalStateException("intention cannot be suspended from "+status);return revise(IntentionStatus.SUSPENDED,at,why,status,null,null);}
    public RecoveryIntention resume(Instant at){if(status!=IntentionStatus.SUSPENDED)throw new IllegalStateException("intention is not suspended");return revise(resumeStatus,at,"resumed",null,null,null);}
    public RecoveryIntention delegate(String childCaseId,Instant at){if(status!=IntentionStatus.ACTIVE)throw new IllegalStateException("only active intention can delegate a bounded investigation");return revise(IntentionStatus.DELEGATED,at,"bounded investigation delegated",null,require(childCaseId,"childCaseId"),null);}
    public RecoveryIntention returnFromDelegation(String childCaseId,DelegationReturn result,Instant at){
        Objects.requireNonNull(result,"result");
        if(status!=IntentionStatus.DELEGATED||!delegationId.equals(childCaseId))throw new IllegalStateException("delegation return does not match active child");
        return revise(result==DelegationReturn.NO_METHOD_CHANGE?IntentionStatus.ACTIVE:IntentionStatus.RECONSIDERATION_PENDING,at,"delegation returned: "+result,null,null,null);
    }
    public RecoveryIntention complete(ObjectiveSatisfactionProof proof,Instant at){
        Objects.requireNonNull(proof,"proof");
        Objects.requireNonNull(at,"at");
        if(status!=IntentionStatus.ACTIVE)throw new IllegalStateException("only active intention can complete");
        if(!objectiveFingerprint.equals(proof.objectiveFingerprint()))throw new SecurityException("completion proof does not match protected objective");
        if(proof.verifiedAt().isBefore(updatedAt)||proof.verifiedAt().isAfter(at)||at.isBefore(updatedAt))throw new IllegalArgumentException("completion proof time is outside intention lifecycle");
        return new RecoveryIntention(id,scopeId,objectiveId,objectiveFingerprint,objectiveOwner,protectedConstraints,currentMethod,IntentionStatus.COMPLETED,version+1,createdAt,at,"objective verified",null,null,proof);
    }
    public RecoveryIntention terminate(String actor,String why,Instant at){
        Objects.requireNonNull(at,"at");
        if(status==IntentionStatus.COMPLETED||status==IntentionStatus.TERMINATED)throw new IllegalStateException("intention is already terminal");
        if(at.isBefore(updatedAt))throw new IllegalArgumentException("intention time cannot move backwards");
        return new RecoveryIntention(id,scopeId,objectiveId,objectiveFingerprint,objectiveOwner,protectedConstraints,currentMethod,IntentionStatus.TERMINATED,version+1,createdAt,at,require(actor,"actor")+": "+require(why,"reason"),null,null,null);
    }
    private void requireActive(){if(status!=IntentionStatus.ACTIVE)throw new IllegalStateException("intention is not active");}
    private RecoveryIntention revise(IntentionStatus next,Instant at,String why,IntentionStatus resume,String delegation,RecoveryMethodReference method){
        why=require(why,"reason");
        if(at.isBefore(updatedAt))throw new IllegalArgumentException("intention time cannot move backwards");
        return new RecoveryIntention(id,scopeId,objectiveId,objectiveFingerprint,objectiveOwner,protectedConstraints,
                method==null?currentMethod:method,next,version+1,createdAt,at,why,resume,delegation,null);
    }
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
