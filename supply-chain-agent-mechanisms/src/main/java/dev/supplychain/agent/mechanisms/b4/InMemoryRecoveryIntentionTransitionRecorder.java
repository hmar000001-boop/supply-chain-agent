package dev.supplychain.agent.mechanisms.b4;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Append-only, per-session recorder for committed B4 intention transitions. */
public final class InMemoryRecoveryIntentionTransitionRecorder implements RecoveryIntentionTransitionRecorder {
    public record Entry(long sequence, RecoveryIntention before, RecoveryIntention after,
            TransitionKind kind, String actor) {
        public Entry {
            if (sequence < 1 || before == null || after == null || kind == null)
                throw new IllegalArgumentException("invalid B4 transition audit entry");
            actor = required(actor, "actor");
            if (!before.id().equals(after.id()) || after.version() != before.version() + 1)
                throw new IllegalArgumentException("B4 transition audit entry is not a contiguous intention revision");
            if (!before.objectiveFingerprint().equals(after.objectiveFingerprint()))
                throw new SecurityException("B4 transition audit cannot record a changed committed objective");
        }
    }

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override public synchronized void record(RecoveryIntention before, RecoveryIntention after, TransitionKind kind, String actor) {
        entries.add(new Entry(sequence.incrementAndGet(), before, after, kind, actor));
    }

    public List<Entry> entries() { return List.copyOf(entries); }

    private static String required(String value, String name) {
        String result = java.util.Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
