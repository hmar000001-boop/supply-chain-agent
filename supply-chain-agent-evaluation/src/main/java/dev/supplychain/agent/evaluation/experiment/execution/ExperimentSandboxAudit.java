package dev.supplychain.agent.evaluation.experiment.execution;

/** Runtime counters instrumented by the disposable action handlers and external connector fence. */
@FunctionalInterface
public interface ExperimentSandboxAudit {
    Snapshot snapshot();

    record Snapshot(long externalNetworkCalls, long externalActionDispatches,
                    long stateChangingDispatches, long dispatchesAfterApprovalBoundary) {
        public Snapshot {
            if (externalNetworkCalls < 0 || externalActionDispatches < 0 || stateChangingDispatches < 0
                    || dispatchesAfterApprovalBoundary < 0) {
                throw new IllegalArgumentException("sandbox audit counters cannot be negative");
            }
        }
        public Snapshot minus(Snapshot earlier) {
            if (earlier == null) throw new NullPointerException("earlier");
            long network = externalNetworkCalls - earlier.externalNetworkCalls;
            long external = externalActionDispatches - earlier.externalActionDispatches;
            long stateChanging = stateChangingDispatches - earlier.stateChangingDispatches;
            long afterApproval = dispatchesAfterApprovalBoundary - earlier.dispatchesAfterApprovalBoundary;
            if (network < 0 || external < 0 || stateChanging < 0 || afterApproval < 0) {
                throw new IllegalStateException("sandbox audit counters moved backwards during one graph run");
            }
            return new Snapshot(network, external, stateChanging, afterApproval);
        }
        public boolean isSideEffectFree() {
            return externalNetworkCalls == 0 && externalActionDispatches == 0
                    && stateChangingDispatches == 0 && dispatchesAfterApprovalBoundary == 0;
        }
    }
}
