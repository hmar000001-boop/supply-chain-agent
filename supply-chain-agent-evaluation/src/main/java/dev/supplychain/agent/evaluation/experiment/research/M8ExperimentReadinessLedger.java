package dev.supplychain.agent.evaluation.experiment.research;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit, non-automating readiness/reminder ledger. A gate is COMPLETE only when the caller
 * supplies an immutable evidence receipt; this class never fabricates completion from source presence.
 */
public final class M8ExperimentReadinessLedger {
    public enum Gate {
        HOSTED_CI_GREEN,
        DETERMINISTIC_TRAIN_HOST_QUALIFIED,
        TRAIN_TELEMETRY_EXECUTED,
        TRAIN_TELEMETRY_INTEGRITY_AUDITED,
        TRAIN_SAFETY_GATE_VERIFIED,
        TRAIN_VERIFIED_OUTCOMES_COLLECTED,
        TRAIN_ARTIFACT_BUNDLE_VERIFIED,
        TRAIN_ARTIFACTS_FROZEN,
        HOLDOUT_EXECUTION_AUTHORIZED,
        HOLDOUT_EXECUTED,
        HOLDOUT_SAFETY_GATE_VERIFIED,
        HOLDOUT_OUTCOMES_INDEPENDENTLY_VERIFIED,
        HOLDOUT_CONFIRMATORY_ANALYSIS_COMPLETE,
        TRANSFER_EXECUTED,
        TRANSFER_SAFETY_GATE_VERIFIED,
        TRANSFER_OUTCOMES_INDEPENDENTLY_VERIFIED,
        TRANSFER_ANALYSIS_COMPLETE,
        REAL_LLM_PROFILE_QUALIFIED
    }
    public enum Status { BLOCKED, READY, COMPLETE, OPTIONAL_NOT_STARTED }

    private static final Map<Gate, List<Gate>> DEPENDENCIES = dependencies();
    private final Map<Gate, Receipt> receipts;

    public M8ExperimentReadinessLedger(Map<Gate, Receipt> receipts) {
        var copy = new EnumMap<Gate, Receipt>(Gate.class);
        copy.putAll(Objects.requireNonNull(receipts, "receipts"));
        this.receipts = Map.copyOf(copy);
    }

    public Snapshot evaluate() {
        var statuses = new EnumMap<Gate, Status>(Gate.class);
        var reminders = new ArrayList<String>();
        for (Gate gate : Gate.values()) {
            Receipt receipt = receipts.get(gate);
            boolean dependenciesComplete = DEPENDENCIES.get(gate).stream()
                    .allMatch(dependency -> statuses.get(dependency) == Status.COMPLETE);
            Status status;
            if (receipt != null) {
                if (!dependenciesComplete)
                    throw new SecurityException("readiness receipt supplied before its prerequisite gates: " + gate);
                for (Gate dependency : DEPENDENCIES.get(gate)) {
                    Receipt prerequisite = receipts.get(dependency);
                    if (prerequisite == null || receipt.verifiedAt().isBefore(prerequisite.verifiedAt()))
                        throw new SecurityException("readiness receipt predates its prerequisite verification: " + gate);
                }
                status = Status.COMPLETE;
            } else if (gate == Gate.REAL_LLM_PROFILE_QUALIFIED) {
                status = Status.OPTIONAL_NOT_STARTED;
            } else if (dependenciesComplete) {
                status = Status.READY;
            } else {
                status = Status.BLOCKED;
            }
            statuses.put(gate, status);
            if (status == Status.READY) reminders.add(reminder(gate));
        }
        return new Snapshot(Instant.now(), statuses, reminders, receipts);
    }

    private static String reminder(Gate gate) {
        return switch (gate) {
            case HOSTED_CI_GREEN -> "Wait for the full hosted 12-module CI workflow to conclude SUCCESS; a green build step alone is insufficient.";
            case DETERMINISTIC_TRAIN_HOST_QUALIFIED -> "Complete the reusable deterministic ports factory and pass full Maven qualification before any TRAIN run.";
            case TRAIN_TELEMETRY_EXECUTED -> "Execute all five exact 280-unit TRAIN batches; outputs must remain telemetry-only.";
            case TRAIN_TELEMETRY_INTEGRITY_AUDITED -> "Re-read all five TRAIN JSONL files and verify byte hashes, exact unit IDs, input fingerprints and no pending files.";
            case TRAIN_SAFETY_GATE_VERIFIED -> "Independently reconstruct all ten safety counters for both arms of all five mechanisms; every counter must be zero.";
            case TRAIN_VERIFIED_OUTCOMES_COLLECTED -> "Collect verifier-attributed B1/B2/B3/B4 outcomes and independently replay B6 labels; do not infer endpoints from telemetry.";
            case TRAIN_ARTIFACT_BUNDLE_VERIFIED -> "Build the exact TRAIN-only B1/B2/B3/B6 typed bundle and pass all independent mechanism verifiers.";
            case TRAIN_ARTIFACTS_FROZEN -> "Archive/read-back the complete typed bundle and publish the immutable manifest with commit, successful CI run and dependency fingerprint.";
            case HOLDOUT_EXECUTION_AUTHORIZED -> "Review the frozen manifest and explicitly authorize HOLDOUT; this ledger does not open it automatically.";
            case HOLDOUT_EXECUTED -> "Run the exact 280 HOLDOUT units with the frozen TRAIN artifacts and record append-only outputs.";
            case HOLDOUT_SAFETY_GATE_VERIFIED -> "Independently reconstruct all ten safety counters for both arms of all five mechanisms; any nonzero value blocks analysis.";
            case HOLDOUT_OUTCOMES_INDEPENDENTLY_VERIFIED -> "Verify every H1-H4 endpoint plus independent B6 simulator labels for all exact HOLDOUT episodes.";
            case HOLDOUT_CONFIRMATORY_ANALYSIS_COMPLETE -> "Run 10,000-resample stratified paired inference and Holm correction across H1-H5; publish the immutable report.";
            case TRANSFER_EXECUTED -> "Run the exact 210 TRANSFER units using the frozen bundle; do not retune on transfer results.";
            case TRANSFER_SAFETY_GATE_VERIFIED -> "Verify zero safety violations across every TRANSFER family, mechanism and matched arm.";
            case TRANSFER_OUTCOMES_INDEPENDENTLY_VERIFIED -> "Independently verify all endpoint evidence and all B6 labels for the held-out transfer families.";
            case TRANSFER_ANALYSIS_COMPLETE -> "Publish transfer-family-stratified paired diagnostics; do not apply confirmatory HOLDOUT language.";
            case REAL_LLM_PROFILE_QUALIFIED -> "Optional: configure a real LangChain4j provider, trace model/version/prompts/costs, and keep this dataset separate from deterministic confirmatory evidence.";
        };
    }

    private static Map<Gate, List<Gate>> dependencies() {
        var map = new EnumMap<Gate, List<Gate>>(Gate.class);
        map.put(Gate.HOSTED_CI_GREEN, List.of());
        map.put(Gate.DETERMINISTIC_TRAIN_HOST_QUALIFIED, List.of(Gate.HOSTED_CI_GREEN));
        map.put(Gate.TRAIN_TELEMETRY_EXECUTED, List.of(Gate.DETERMINISTIC_TRAIN_HOST_QUALIFIED));
        map.put(Gate.TRAIN_TELEMETRY_INTEGRITY_AUDITED, List.of(Gate.TRAIN_TELEMETRY_EXECUTED));
        map.put(Gate.TRAIN_SAFETY_GATE_VERIFIED, List.of(Gate.TRAIN_TELEMETRY_INTEGRITY_AUDITED));
        map.put(Gate.TRAIN_VERIFIED_OUTCOMES_COLLECTED, List.of(Gate.TRAIN_SAFETY_GATE_VERIFIED));
        map.put(Gate.TRAIN_ARTIFACT_BUNDLE_VERIFIED, List.of(Gate.TRAIN_VERIFIED_OUTCOMES_COLLECTED));
        map.put(Gate.TRAIN_ARTIFACTS_FROZEN, List.of(Gate.TRAIN_ARTIFACT_BUNDLE_VERIFIED));
        map.put(Gate.HOLDOUT_EXECUTION_AUTHORIZED, List.of(Gate.TRAIN_ARTIFACTS_FROZEN));
        map.put(Gate.HOLDOUT_EXECUTED, List.of(Gate.HOLDOUT_EXECUTION_AUTHORIZED));
        map.put(Gate.HOLDOUT_SAFETY_GATE_VERIFIED, List.of(Gate.HOLDOUT_EXECUTED));
        map.put(Gate.HOLDOUT_OUTCOMES_INDEPENDENTLY_VERIFIED, List.of(Gate.HOLDOUT_SAFETY_GATE_VERIFIED));
        map.put(Gate.HOLDOUT_CONFIRMATORY_ANALYSIS_COMPLETE, List.of(Gate.HOLDOUT_OUTCOMES_INDEPENDENTLY_VERIFIED));
        map.put(Gate.TRANSFER_EXECUTED, List.of(Gate.HOLDOUT_CONFIRMATORY_ANALYSIS_COMPLETE));
        map.put(Gate.TRANSFER_SAFETY_GATE_VERIFIED, List.of(Gate.TRANSFER_EXECUTED));
        map.put(Gate.TRANSFER_OUTCOMES_INDEPENDENTLY_VERIFIED, List.of(Gate.TRANSFER_SAFETY_GATE_VERIFIED));
        map.put(Gate.TRANSFER_ANALYSIS_COMPLETE, List.of(Gate.TRANSFER_OUTCOMES_INDEPENDENTLY_VERIFIED));
        map.put(Gate.REAL_LLM_PROFILE_QUALIFIED, List.of(Gate.HOSTED_CI_GREEN));
        return Map.copyOf(map);
    }

    public record Receipt(String evidenceFingerprint, String verifierId, Instant verifiedAt) {
        public Receipt {
            evidenceFingerprint = M8TrainingFreezeManifest.sha(evidenceFingerprint, "evidenceFingerprint");
            verifierId = Objects.requireNonNull(verifierId, "verifierId").trim();
            verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
            if (verifierId.isEmpty()) throw new IllegalArgumentException("verifierId cannot be blank");
        }
    }

    public record Snapshot(Instant generatedAt, Map<Gate, Status> statuses,
            List<String> reminders, Map<Gate, Receipt> receipts) {
        public Snapshot {
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
            var statusCopy = new EnumMap<Gate, Status>(Gate.class);
            statusCopy.putAll(statuses);
            statuses = Map.copyOf(statusCopy);
            reminders = List.copyOf(reminders);
            var receiptCopy = new EnumMap<Gate, Receipt>(Gate.class);
            receiptCopy.putAll(receipts);
            receipts = Map.copyOf(receiptCopy);
        }
        public String toJson() {
            StringBuilder out = new StringBuilder("{\"recordType\":\"m8ExperimentReadiness\",\"generatedAt\":")
                    .append(q(generatedAt.toString())).append(",\"gates\":[");
            boolean first = true;
            for (Gate gate : Gate.values()) {
                if (!first) out.append(',');
                first = false;
                out.append("{\"gate\":").append(q(gate.name()))
                        .append(",\"status\":").append(q(statuses.get(gate).name()));
                Receipt receipt = receipts.get(gate);
                if (receipt != null) out.append(",\"evidenceFingerprint\":").append(q(receipt.evidenceFingerprint()))
                        .append(",\"verifierId\":").append(q(receipt.verifierId()))
                        .append(",\"verifiedAt\":").append(q(receipt.verifiedAt().toString()));
                out.append('}');
            }
            out.append("],\"reminders\":[");
            for (int i = 0; i < reminders.size(); i++) {
                if (i > 0) out.append(',');
                out.append(q(reminders.get(i)));
            }
            return out.append("]}\n").toString();
        }
        private static String q(String value) {
            StringBuilder out = new StringBuilder("\"");
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '\\' -> out.append("\\\\");
                    case '"' -> out.append("\\\"");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> { if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch); }
                }
            }
            return out.append('\"').toString();
        }
    }
}
