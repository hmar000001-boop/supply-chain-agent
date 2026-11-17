package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record ImpasseResolutionCase(
        String id, MemoryScopeId scopeId, String executionId, String parentRequestId,
        long parentWorkingMemoryVersion, String objectiveFingerprint, ImpasseSignal impasse,
        Set<Capability> childCapabilities, Instant openedAt, Instant deadline,
        int maximumChildActions, int childActionsUsed, ImpasseCaseStatus status,
        long version, ChildResolutionResult result, String terminalReason) {
    private static final Set<Capability> CHILD_ALLOW_LIST=Set.of(Capability.MEMORY_RETRIEVE, Capability.REASONING_INVOKE,
            Capability.SUPPLIER_READ, Capability.INVENTORY_READ, Capability.ORDER_READ, Capability.SHIPMENT_READ, Capability.QUALITY_READ);
    public ImpasseResolutionCase {
        id=require(id,"id"); scopeId=Objects.requireNonNull(scopeId,"scopeId"); executionId=require(executionId,"executionId");
        parentRequestId=require(parentRequestId,"parentRequestId");
        if(parentWorkingMemoryVersion<0||version<0||childActionsUsed<0) throw new IllegalArgumentException("versions/action count cannot be negative");
        objectiveFingerprint=require(objectiveFingerprint,"objectiveFingerprint"); impasse=Objects.requireNonNull(impasse,"impasse");
        childCapabilities=Set.copyOf(Objects.requireNonNull(childCapabilities,"childCapabilities"));
        if(childCapabilities.isEmpty()||!CHILD_ALLOW_LIST.containsAll(childCapabilities)) throw new SecurityException("child resolution capability set contains forbidden capability");
        openedAt=Objects.requireNonNull(openedAt,"openedAt"); deadline=Objects.requireNonNull(deadline,"deadline");
        if(!deadline.isAfter(openedAt)) throw new IllegalArgumentException("deadline must follow open time");
        if(maximumChildActions<1||childActionsUsed>maximumChildActions) throw new IllegalArgumentException("invalid child action budget");
        status=Objects.requireNonNull(status,"status");
        if(status==ImpasseCaseStatus.RESOLVED && (result==null || result.disposition()==ResolutionDisposition.UNRESOLVED)) throw new IllegalArgumentException("resolved case requires a positive child result");
        if(result!=null && status!=ImpasseCaseStatus.RESOLVED && !(status==ImpasseCaseStatus.ESCALATED && result.disposition()==ResolutionDisposition.UNRESOLVED)) throw new IllegalArgumentException("only resolved or unresolved-escalated cases may retain a child result");
        if(isTerminal(status) && (terminalReason==null||terminalReason.isBlank()) && status!=ImpasseCaseStatus.RESOLVED) throw new IllegalArgumentException("terminal case requires reason");
        terminalReason=terminalReason==null?null:terminalReason.trim();
    }
    public static ImpasseResolutionCase open(String id, MemoryScopeId scope, String execution, ParentContextSnapshot parent,
                                              ImpasseSignal signal, Set<Capability> parentCapabilities, Set<Capability> childCapabilities,
                                              Instant now, Instant deadline, int maxActions) {
        Objects.requireNonNull(parentCapabilities,"parentCapabilities");
        if(!scope.equals(parent.scopeId())) throw new SecurityException("impasse child scope must match parent scope");
        if(!parentCapabilities.containsAll(childCapabilities)) throw new SecurityException("child capabilities exceed parent grant");
        return new ImpasseResolutionCase(id,scope,execution,parent.requestId(),parent.workingMemoryVersion(),parent.objectiveFingerprint(),
                signal,childCapabilities,now,deadline,maxActions,0,ImpasseCaseStatus.OPEN,0,null,null);
    }
    public ImpasseResolutionCase start(Instant now) {
        requireLive(now); if(status!=ImpasseCaseStatus.OPEN) throw new IllegalStateException("only open case can start");
        return revise(ImpasseCaseStatus.INVESTIGATING,childActionsUsed,null,null);
    }
    public ImpasseResolutionCase recordAction(Capability capability, Instant now) {
        requireLive(now); if(status!=ImpasseCaseStatus.INVESTIGATING) throw new IllegalStateException("case is not investigating");
        if(!childCapabilities.contains(Objects.requireNonNull(capability,"capability"))) throw new SecurityException("child action exceeds delegated capability");
        if(childActionsUsed>=maximumChildActions) throw new IllegalStateException("child action budget exhausted");
        return revise(status,childActionsUsed+1,null,null);
    }
    public ImpasseResolutionCase resolve(ChildResolutionResult childResult, ParentContextSnapshot currentParent, Instant now) {
        requireLive(now); if(status!=ImpasseCaseStatus.INVESTIGATING) throw new IllegalStateException("case is not investigating");
        Objects.requireNonNull(childResult,"childResult"); Objects.requireNonNull(currentParent,"currentParent");
        if(!scopeId.equals(currentParent.scopeId()) || !parentRequestId.equals(currentParent.requestId())
                || parentWorkingMemoryVersion!=currentParent.workingMemoryVersion()
                || !objectiveFingerprint.equals(currentParent.objectiveFingerprint())) {
            throw new IllegalStateException("parent changed while child resolution was running; result must not be applied");
        }
        if(!id.equals(childResult.caseId()) || !parentRequestId.equals(childResult.parentRequestId())
                || parentWorkingMemoryVersion!=childResult.parentWorkingMemoryVersion()
                || !objectiveFingerprint.equals(childResult.objectiveFingerprint())) throw new SecurityException("child result does not match its parent case snapshot");
        if(childResult.producedAt().isAfter(now) || childResult.producedAt().isBefore(openedAt)) throw new IllegalArgumentException("child result timestamp is outside case interval");
        if(childResult.disposition()==ResolutionDisposition.UNRESOLVED) return revise(ImpasseCaseStatus.ESCALATED,childActionsUsed,childResult,"child could not resolve impasse");
        return revise(ImpasseCaseStatus.RESOLVED,childActionsUsed,childResult,null);
    }
    public ImpasseResolutionCase reject(String reason, Instant now) { requireLive(now); return terminal(ImpasseCaseStatus.REJECTED,reason); }
    public ImpasseResolutionCase escalate(String reason, Instant now) { requireLive(now); return terminal(ImpasseCaseStatus.ESCALATED,reason); }
    public ImpasseResolutionCase expire(Instant now) {
        if(now.isBefore(deadline)) throw new IllegalStateException("case has not expired");
        if(isTerminal(status)) return this;
        return terminal(ImpasseCaseStatus.EXPIRED,"resolution deadline exceeded");
    }
    private void requireLive(Instant now) { Objects.requireNonNull(now,"now"); if(now.isBefore(openedAt)) throw new IllegalArgumentException("case transition cannot predate opening"); if(!now.isBefore(deadline)) throw new IllegalStateException("resolution deadline exceeded"); }
    private ImpasseResolutionCase terminal(ImpasseCaseStatus next,String reason) { return revise(next,childActionsUsed,null,require(reason,"reason")); }
    private ImpasseResolutionCase revise(ImpasseCaseStatus next,int actions,ChildResolutionResult child,String reason) {
        return new ImpasseResolutionCase(id,scopeId,executionId,parentRequestId,parentWorkingMemoryVersion,objectiveFingerprint,impasse,
                childCapabilities,openedAt,deadline,maximumChildActions,actions,next,version+1,child,reason);
    }
    private static boolean isTerminal(ImpasseCaseStatus s){return s==ImpasseCaseStatus.RESOLVED||s==ImpasseCaseStatus.REJECTED||s==ImpasseCaseStatus.ESCALATED||s==ImpasseCaseStatus.EXPIRED;}
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
