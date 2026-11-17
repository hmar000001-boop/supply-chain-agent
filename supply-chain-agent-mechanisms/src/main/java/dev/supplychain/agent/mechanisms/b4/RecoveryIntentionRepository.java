package dev.supplychain.agent.mechanisms.b4;

import java.util.Optional;

public interface RecoveryIntentionRepository {void saveNew(RecoveryIntention intention);Optional<RecoveryIntention> findById(RecoveryIntentionId id);boolean compareAndSet(RecoveryIntentionId id,long expectedVersion,RecoveryIntention replacement);}
