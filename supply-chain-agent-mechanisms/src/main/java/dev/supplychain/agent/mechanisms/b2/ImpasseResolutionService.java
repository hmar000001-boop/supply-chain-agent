package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ImpasseResolutionService {
    private final ImpasseDetector detector;
    private final ImpasseCaseRepository repository;
    public ImpasseResolutionService(ImpasseDetector detector,ImpasseCaseRepository repository){this.detector=Objects.requireNonNull(detector,"detector");this.repository=Objects.requireNonNull(repository,"repository");}
    public List<ImpasseSignal> detect(List<ImpasseSignal> signals){return detector.consolidate(signals);}
    public ImpasseResolutionCase open(String caseId,MemoryScopeId scope,String executionId,ParentContextSnapshot parent,
                                      ImpasseSignal signal,Set<Capability> parentCapabilities,Set<Capability> childCapabilities,
                                      Instant now,Instant deadline,int maxActions){
        var value=ImpasseResolutionCase.open(caseId,scope,executionId,parent,signal,parentCapabilities,childCapabilities,now,deadline,maxActions);
        repository.saveNew(value);return value;
    }
    public ImpasseResolutionCase start(String id,long expectedVersion,Instant now){return update(id,expectedVersion,c->c.start(now));}
    public ImpasseResolutionCase recordChildAction(String id,long expectedVersion,Capability capability,Instant now){return update(id,expectedVersion,c->c.recordAction(capability,now));}
    public ImpasseResolutionCase resolve(String id,long expectedVersion,ChildResolutionResult result,ParentContextSnapshot currentParent,Instant now){
        return update(id,expectedVersion,c->c.resolve(result,currentParent,now));
    }
    public ImpasseResolutionCase reject(String id,long expectedVersion,String reason,Instant now){return update(id,expectedVersion,c->c.reject(reason,now));}
    public ImpasseResolutionCase escalate(String id,long expectedVersion,String reason,Instant now){return update(id,expectedVersion,c->c.escalate(reason,now));}
    public ImpasseResolutionCase expire(String id,long expectedVersion,Instant now){return update(id,expectedVersion,c->c.expire(now));}
    public ImpasseResolutionCase get(String id){return repository.findById(id).orElseThrow(()->new IllegalArgumentException("unknown impasse case: "+id));}
    private ImpasseResolutionCase update(String id,long expected,java.util.function.UnaryOperator<ImpasseResolutionCase> operation){
        var current=get(id);if(current.version()!=expected)throw new IllegalStateException("impasse case version conflict");
        var next=operation.apply(current);if(next==current)return current;
        if(!repository.compareAndSet(id,expected,next))throw new IllegalStateException("impasse case changed concurrently");return next;
    }
}
