package dev.supplychain.agent.mechanisms.b4;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-process reference adapter. M7 provides PostgreSQL CAS persistence. */
public final class InMemoryRecoveryIntentionRepository implements RecoveryIntentionRepository{
    private final ConcurrentHashMap<RecoveryIntentionId,RecoveryIntention> values=new ConcurrentHashMap<>();
    public void saveNew(RecoveryIntention value){if(values.putIfAbsent(value.id(),value)!=null)throw new IllegalStateException("intention already exists");}
    public Optional<RecoveryIntention> findById(RecoveryIntentionId id){return Optional.ofNullable(values.get(id));}
    public boolean compareAndSet(RecoveryIntentionId id,long expected,RecoveryIntention replacement){final boolean[] changed={false};values.compute(id,(key,current)->{if(current==null)throw new IllegalArgumentException("unknown intention: "+id);if(current.version()!=expected)return current;if(replacement.version()!=expected+1||!replacement.id().equals(id))throw new IllegalArgumentException("invalid intention replacement");changed[0]=true;return replacement;});return changed[0];}
}
