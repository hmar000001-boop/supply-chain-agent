package dev.supplychain.agent.simulator.effect;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Auditable description of a modeled transition; it does not imply an external side effect. */
public record SimulationEffect(SimulationEffectType type, String description,
                               Set<String> affectedEntityReferences, Map<String, String> attributes) {
    public SimulationEffect {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
        affectedEntityReferences = Set.copyOf(Objects.requireNonNull(affectedEntityReferences, "affectedEntityReferences"));
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }
    public static SimulationEffect of(SimulationEffectType type, String description, Set<String> affected, Map<String, String> attributes) {
        return new SimulationEffect(type, description, affected, attributes);
    }
}
