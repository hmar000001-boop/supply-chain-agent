package dev.supplychain.agent.mechanisms.b4;

import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;

public final class RecoveryIntentionService{
    private final RecoveryIntentionRepository repository;
    public RecoveryIntentionService(RecoveryIntentionRepository repository){this.repository=Objects.requireNonNull(repository,"repository");}
    public RecoveryIntention create(RecoveryObjective objective,MemoryScopeId scope,String owner,RecoveryMethodReference method,Instant at){return create(RecoveryIntentionId.random(),objective,scope,owner,method,at);}
    public RecoveryIntention create(RecoveryIntentionId id,RecoveryObjective objective,MemoryScopeId scope,String owner,RecoveryMethodReference method,Instant at){var value=RecoveryIntention.create(id,objective,scope,owner,method,at);repository.saveNew(value);return value;}
    public java.util.Optional<RecoveryIntention> find(RecoveryIntentionId id){return repository.findById(id);}
    public RecoveryIntention get(RecoveryIntentionId id){return repository.findById(id).orElseThrow(()->new IllegalArgumentException("unknown intention: "+id));}
    public RecoveryIntention requestReconsideration(RecoveryIntentionId id,long version,String reason,Instant at){return update(id,version,v->v.requestReconsideration(reason,at));}
    public RecoveryIntention changeMethod(RecoveryIntentionId id,long version,RecoveryMethodReference method,String objectiveFingerprint,Instant at){return update(id,version,v->v.changeMethod(method,objectiveFingerprint,at));}
    public RecoveryIntention suspend(RecoveryIntentionId id,long version,String reason,Instant at){return update(id,version,v->v.suspend(reason,at));}
    public RecoveryIntention resume(RecoveryIntentionId id,long version,Instant at){return update(id,version,v->v.resume(at));}
    public RecoveryIntention delegate(RecoveryIntentionId id,long version,String caseId,Instant at){return update(id,version,v->v.delegate(caseId,at));}
    public RecoveryIntention returnFromDelegation(RecoveryIntentionId id,long version,String caseId,DelegationReturn result,Instant at){return update(id,version,v->v.returnFromDelegation(caseId,result,at));}
    public RecoveryIntention complete(RecoveryIntentionId id,long version,ObjectiveSatisfactionProof proof,Instant at){return update(id,version,v->v.complete(proof,at));}
    public RecoveryIntention terminate(RecoveryIntentionId id,long version,String actor,String reason,Instant at){return update(id,version,v->v.terminate(actor,reason,at));}
    private RecoveryIntention update(RecoveryIntentionId id,long expected,java.util.function.UnaryOperator<RecoveryIntention> op){var current=get(id);if(current.version()!=expected)throw new IllegalStateException("intention version conflict");var next=op.apply(current);if(!repository.compareAndSet(id,expected,next))throw new IllegalStateException("intention changed concurrently");return next;}
}
