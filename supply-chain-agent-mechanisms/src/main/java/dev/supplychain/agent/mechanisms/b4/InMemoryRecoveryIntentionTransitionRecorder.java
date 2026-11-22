package dev.supplychain.agent.mechanisms.b4;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Append-only, per-session recorder for committed B4 intention transitions. */
public final class InMemoryRecoveryIntentionTransitionRecorder implements RecoveryIntentionTransitionRecorder {
    public record Entry(long sequence, RecoveryIntention before, RecoveryIntention after, TransitionKind kind) {
        public Entry {
            if (sequence < 1 || before == null || after == null || kind == null)
                throw new IllegalArgumentException("invalid B4 transition audit entry");
        }
    }

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override public void record(RecoveryIntention before, RecoveryIntention after, TransitionKind kind) {
        entries.add(new Entry(sequence.incrementAndGet(), before, after, kind));
    }

    public List<Entry> entries() { return List.copyOf(entries); }
}
