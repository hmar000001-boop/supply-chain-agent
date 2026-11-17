package dev.supplychain.agent.memory.working;

import dev.supplychain.agent.memory.common.EntityReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Small, explicit value algebra for serializable active context; arbitrary Object values are forbidden. */
public sealed interface MemoryValue permits MemoryValue.Text, MemoryValue.Decimal,
        MemoryValue.Flag, MemoryValue.Timestamp, MemoryValue.EntityLink, MemoryValue.TextList {

    record Text(String value) implements MemoryValue {
        public Text { value = Objects.requireNonNull(value, "value"); }
    }

    record Decimal(BigDecimal value) implements MemoryValue {
        public Decimal { value = Objects.requireNonNull(value, "value"); }
    }

    record Flag(boolean value) implements MemoryValue { }

    record Timestamp(Instant value) implements MemoryValue {
        public Timestamp { value = Objects.requireNonNull(value, "value"); }
    }

    record EntityLink(EntityReference value) implements MemoryValue {
        public EntityLink { value = Objects.requireNonNull(value, "value"); }
    }

    record TextList(List<String> values) implements MemoryValue {
        public TextList { values = List.copyOf(Objects.requireNonNull(values, "values")); }
    }
}
