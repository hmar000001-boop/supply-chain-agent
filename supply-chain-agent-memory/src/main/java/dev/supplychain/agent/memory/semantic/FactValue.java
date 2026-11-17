package dev.supplychain.agent.memory.semantic;

import dev.supplychain.agent.memory.common.EntityReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Typed semantic value avoids unvalidated stringly-typed facts. */
public sealed interface FactValue permits FactValue.Text, FactValue.Decimal, FactValue.Flag,
        FactValue.Timestamp, FactValue.EntityLink {
    record Text(String value) implements FactValue {
        public Text { value = Objects.requireNonNull(value, "value"); }
    }
    record Decimal(BigDecimal value) implements FactValue {
        public Decimal { value = Objects.requireNonNull(value, "value"); }
    }
    record Flag(boolean value) implements FactValue { }
    record Timestamp(Instant value) implements FactValue {
        public Timestamp { value = Objects.requireNonNull(value, "value"); }
    }
    record EntityLink(EntityReference value) implements FactValue {
        public EntityLink { value = Objects.requireNonNull(value, "value"); }
    }
}
