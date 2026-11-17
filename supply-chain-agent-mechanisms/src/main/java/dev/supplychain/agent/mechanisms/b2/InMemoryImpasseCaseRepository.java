package dev.supplychain.agent.mechanisms.b2;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference adapter; M7 supplies durable storage. */
public final class InMemoryImpasseCaseRepository implements ImpasseCaseRepository {
    private final ConcurrentHashMap<String,ImpasseResolutionCase> values=new ConcurrentHashMap<>();
    @Override public void saveNew(ImpasseResolutionCase value){if(values.putIfAbsent(value.id(),value)!=null)throw new IllegalStateException("impasse case already exists: "+value.id());}
    @Override public Optional<ImpasseResolutionCase> findById(String id){return Optional.ofNullable(values.get(id));}
    @Override public boolean compareAndSet(String id,long expected,ImpasseResolutionCase replacement){
        final boolean[] changed={false}; values.compute(id,(key,current)->{if(current==null)throw new IllegalArgumentException("unknown impasse case: "+id);
            if(current.version()!=expected)return current;if(replacement.version()!=expected+1||!replacement.id().equals(id))throw new IllegalArgumentException("invalid replacement version or identity");changed[0]=true;return replacement;});return changed[0];
    }
}
