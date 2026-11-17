package dev.supplychain.agent.mechanisms.b2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Consolidates typed signals emitted by domain validators; it does not infer business facts from free text. */
public final class DeterministicImpasseDetector implements ImpasseDetector {
    @Override public List<ImpasseSignal> consolidate(List<ImpasseSignal> signals) {
        Objects.requireNonNull(signals,"signals");
        var grouped=new LinkedHashMap<String,ImpasseSignal>();
        for(var signal:signals){
            Objects.requireNonNull(signal,"signal");
            var old=grouped.get(signal.stableKey());
            if(old==null) grouped.put(signal.stableKey(),signal);
            else {
                var evidence=new java.util.LinkedHashSet<>(old.evidence()); evidence.addAll(signal.evidence());
                var chosen=signal.severity().ordinal()>old.severity().ordinal()?signal:old;
                grouped.put(signal.stableKey(),new ImpasseSignal(chosen.type(),chosen.severity(),chosen.subjectReference(),
                        chosen.summary(),List.copyOf(evidence),old.detectedAt().isAfter(signal.detectedAt())?old.detectedAt():signal.detectedAt()));
            }
        }
        return grouped.values().stream().sorted(Comparator.comparing(ImpasseSignal::severity).reversed()
                .thenComparing(s->s.type().name()).thenComparing(ImpasseSignal::subjectReference)).toList();
    }
}
