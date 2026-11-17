package dev.supplychain.agent.planning.api;

import java.util.Objects;

public sealed interface WorldTransition permits WorldTransition.Applied, WorldTransition.Rejected {
    record Applied(PlanningState state) implements WorldTransition {
        public Applied { Objects.requireNonNull(state, "state"); }
    }
    record Rejected(String code, String reason) implements WorldTransition {
        public Rejected {
            code = require(code, "code");
            reason = require(reason, "reason");
        }
        private static String require(String v, String n) {
            String s = Objects.requireNonNull(v, n).trim();
            if (s.isEmpty()) throw new IllegalArgumentException(n + " cannot be blank");
            return s;
        }
    }
}
