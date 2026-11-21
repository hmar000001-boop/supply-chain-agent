package dev.supplychain.agent.mechanisms.b3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;

/**
 * Bounded workspace admission with mandatory-content protection and explicit competition
 * between singleton candidates and alternative complementary content coalitions.
 */
public final class WorkspaceAdmissionService {
    private final WorkspaceRepository repository;
    private final dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment outcomeAdjustment;
    private final WorkspaceCoalitionCompetitionPolicy coalitionPolicy;

    public WorkspaceAdmissionService(WorkspaceRepository repository) {
        this(repository, dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment.neutral());
    }

    /** Outcome feedback is opt-in; only a trusted activation service can mint the production overlay. */
    public WorkspaceAdmissionService(WorkspaceRepository repository,
            dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment outcomeAdjustment) {
        this(repository, outcomeAdjustment, WorkspaceCoalitionCompetitionPolicy.standard());
    }

    public WorkspaceAdmissionService(WorkspaceRepository repository,
            dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment outcomeAdjustment,
            WorkspaceCoalitionCompetitionPolicy coalitionPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outcomeAdjustment = Objects.requireNonNull(outcomeAdjustment, "outcomeAdjustment");
        this.coalitionPolicy = Objects.requireNonNull(coalitionPolicy, "coalitionPolicy");
    }

    public WorkspaceAdmissionOutcome admit(WorkspaceAdmissionRequest request) {
        Objects.requireNonNull(request, "request");
        var selected = new ArrayList<WorkspaceEntry>();
        var selectedIds = new HashSet<String>();
        var covered = new LinkedHashSet<String>();
        var coalitions = new ArrayList<WorkspaceCoalition>();
        var competitionTrace = new ArrayList<WorkspaceCoalitionProposal>();
        int used = 0;

        var mandatory = request.candidates().stream().filter(WorkspaceCandidate::mandatory)
                .sorted(Comparator.comparing(WorkspaceCandidate::id)).toList();
        if (mandatory.size() > request.maximumCandidates()) return failedMandatory("mandatory candidates exceed maximum candidate count");
        for (WorkspaceCandidate candidate : mandatory) {
            if (conflicts(candidate, selected)) return failedMandatory("mandatory candidates conflict: " + candidate.id());
            if (candidate.contextUnits() > request.capacityUnits() - used)
                return failedMandatory("mandatory candidates exceed workspace capacity");
            var newTags = new LinkedHashSet<>(candidate.coverageTags());
            newTags.removeAll(covered);
            selected.add(new WorkspaceEntry(candidate, adjustedBaseScore(candidate, request.scoringPolicy()), newTags));
            selectedIds.add(candidate.id());
            covered.addAll(candidate.coverageTags());
            used += candidate.contextUnits();
        }

        var remaining = new ArrayList<>(request.candidates().stream()
                .filter(candidate -> !selectedIds.contains(candidate.id()))
                .sorted(Comparator.comparing(WorkspaceCandidate::id)).toList());
        int round = 0;
        int coalitionAttemptsUsed = 0;
        boolean coalitionSearchTruncated = false;

        while (selected.size() < request.maximumCandidates() && !remaining.isEmpty()) {
            int slots = request.maximumCandidates() - selected.size();
            int freeUnits = request.capacityUnits() - used;
            var eligible = remaining.stream().filter(candidate -> candidate.contextUnits() <= freeUnits)
                    .filter(candidate -> !conflicts(candidate, selected)).toList();
            if (eligible.isEmpty()) break;

            var options = new ArrayList<BundleOption>();
            for (WorkspaceCandidate candidate : eligible) {
                Set<String> fresh = freshCoverage(List.of(candidate), covered);
                double base = adjustedBaseScore(candidate, request.scoringPolicy());
                double bonus = coverageBonus(fresh, request);
                options.add(new BundleOption(List.of(candidate), fresh, candidate.contextUnits(),
                        base, bonus, base + bonus, null));
            }

            if (!coalitionSearchTruncated && slots >= 2 && eligible.size() >= 2) {
                int remainingSearchBudget = coalitionPolicy.maximumEvaluatedCoalitionProposals() - coalitionAttemptsUsed;
                CoalitionSearchResult search = evaluateCoalitions(eligible, selected, covered, request,
                        freeUnits, slots, round, remainingSearchBudget);
                coalitionAttemptsUsed += search.attempts();
                if (search.truncated()) coalitionSearchTruncated = true;
                // If the search was truncated, its partial coalition set is not a comparable choice set.
                // Keep the trace, but fall back to exhaustive singleton comparison for this and later rounds.
                if (!search.truncated()) {
                    for (WorkspaceCoalitionProposal proposal : search.proposals()) {
                        List<WorkspaceCandidate> members = proposal.candidateIds().stream()
                                .map(id -> eligible.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow())
                                .toList();
                        options.add(new BundleOption(members, proposal.newlyCoveredTags(), proposal.contextUnits(),
                                proposal.baseScore(), proposal.coverageBonus(), proposal.totalScore(), proposal));
                    }
                }
                BundleOption bestPreview = chooseBest(options);
                for (WorkspaceCoalitionProposal proposal : search.proposals()) {
                    boolean chosen = !search.truncated() && bestPreview != null && bestPreview.proposal() != null
                            && bestPreview.proposal().id().equals(proposal.id());
                    competitionTrace.add(withSelected(proposal, chosen));
                }
            }

            BundleOption best = chooseBest(options);
            if (best == null || best.totalScore() <= 0.0) break;

            var newlyAdmitted = new ArrayList<WorkspaceCandidate>();
            for (WorkspaceCandidate candidate : best.members()) {
                Set<String> newTags = freshCoverage(List.of(candidate), covered);
                double score = adjustedBaseScore(candidate, request.scoringPolicy()) + coverageBonus(newTags, request);
                selected.add(new WorkspaceEntry(candidate, score, newTags));
                selectedIds.add(candidate.id());
                covered.addAll(newTags);
                used += candidate.contextUnits();
                newlyAdmitted.add(candidate);
            }
            remaining.removeAll(newlyAdmitted);

            if (best.members().size() > 1) {
                List<String> ids = best.members().stream().map(WorkspaceCandidate::id).sorted().toList();
                double aggregateScore = selected.stream().filter(entry -> ids.contains(entry.candidate().id()))
                        .mapToDouble(WorkspaceEntry::marginalScore).sum();
                String coalitionId = "coalition:" + best.proposal().id();
                coalitions.add(new WorkspaceCoalition(coalitionId, ids, best.newlyCoveredTags(),
                        best.contextUnits(), aggregateScore));
            }
            round++;
        }

        String id = UUID.randomUUID().toString();
        var snapshot = new WorkspaceSnapshot(id, request.cycleId(), request.scopeId(), request.capacityUnits(), used,
                selected, coalitions, covered, WorkspaceStatus.ADMITTED, 0, request.createdAt(), request.createdAt(),
                request.experimentUnitFingerprint(), competitionTrace, coalitionSearchTruncated);
        repository.saveNew(snapshot);
        var missing = request.requiredCoverage().stream().filter(tag -> !covered.contains(tag)).sorted().toList();
        String explanation = missing.isEmpty()
                ? "mandatory content protected; singleton and feasible coalition alternatives competed under bounded admission"
                : "workspace admitted; required coverage remains incomplete after singleton/coalition competition";
        if (coalitionSearchTruncated) explanation += "; coalition enumeration reached its configured bound and singleton fallback was used";
        return new WorkspaceAdmissionOutcome(missing.isEmpty() ? WorkspaceAdmissionStatus.ADMITTED
                : WorkspaceAdmissionStatus.INCOMPLETE_COVERAGE, snapshot, missing, explanation);
    }

    /** Read-only inspection of the exact admitted snapshot for audit/evaluation consumers. */
    public java.util.Optional<WorkspaceSnapshot> findById(String id) {
        Objects.requireNonNull(id, "id");
        return repository.findById(id);
    }

    public WorkspaceSnapshot close(String id, dev.supplychain.agent.memory.common.MemoryScopeId scopeId,
            long expectedVersion, Instant at) {
        Objects.requireNonNull(scopeId, "scopeId");
        var current = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown workspace: " + id));
        if (!current.scopeId().equals(scopeId)) throw new SecurityException("workspace scope does not match caller scope");
        if (current.version() != expectedVersion) throw new IllegalStateException("workspace version conflict");
        var closed = current.close(at);
        if (closed == current) return current;
        if (!repository.compareAndSet(id, expectedVersion, closed)) throw new IllegalStateException("workspace changed concurrently");
        return closed;
    }

    private CoalitionSearchResult evaluateCoalitions(List<WorkspaceCandidate> eligible, List<WorkspaceEntry> selected,
            Set<String> covered, WorkspaceAdmissionRequest request, int freeUnits, int slots,
            int round, int remainingSearchBudget) {
        var proposals = new ArrayList<WorkspaceCoalitionProposal>();
        int[] attempts = {0};
        boolean[] truncated = {false};
        for (int size = 2; size <= Math.min(coalitionPolicy.maximumCoalitionSize(), slots); size++) {
            enumerate(eligible, selected, covered, request, freeUnits, size, 0, new ArrayList<>(),
                    round, remainingSearchBudget, attempts, truncated, proposals);
            if (truncated[0]) break;
        }
        return new CoalitionSearchResult(List.copyOf(proposals), attempts[0], truncated[0]);
    }

    private void enumerate(List<WorkspaceCandidate> eligible, List<WorkspaceEntry> selected, Set<String> covered,
            WorkspaceAdmissionRequest request, int freeUnits, int targetSize, int start,
            List<WorkspaceCandidate> current, int round, int budget, int[] attempts, boolean[] truncated,
            List<WorkspaceCoalitionProposal> proposals) {
        if (truncated[0]) return;
        if (current.size() == targetSize) {
            if (attempts[0] >= budget) {
                truncated[0] = true;
                return;
            }
            attempts[0]++;
            int units = current.stream().mapToInt(WorkspaceCandidate::contextUnits).sum();
            if (units > freeUnits) return;
            Set<String> fresh = freshCoverage(current, covered);
            if (fresh.size() < 2) return;
            double base = current.stream().mapToDouble(candidate -> adjustedBaseScore(candidate, request.scoringPolicy())).sum();
            double bonus = coverageBonus(fresh, request);
            List<String> ids = current.stream().map(WorkspaceCandidate::id).sorted().toList();
            String proposalId = proposalId(round, ids, fresh);
            proposals.add(new WorkspaceCoalitionProposal(proposalId, round, ids, fresh, units, base, bonus,
                    base + bonus, false));
            return;
        }
        for (int index = start; index < eligible.size(); index++) {
            WorkspaceCandidate candidate = eligible.get(index);
            if (candidate.contextUnits() > freeUnits || conflicts(candidate, selected) || conflictsWithin(candidate, current)) continue;
            current.add(candidate);
            enumerate(eligible, selected, covered, request, freeUnits, targetSize, index + 1, current,
                    round, budget, attempts, truncated, proposals);
            current.removeLast();
            if (truncated[0]) return;
        }
    }

    private double adjustedBaseScore(WorkspaceCandidate candidate, WorkspaceScoringPolicy policy) {
        double adjustment = outcomeAdjustment.adjustmentFor(candidate.scopeId(), candidate.id());
        if (!Double.isFinite(adjustment) || Math.abs(adjustment) > 5.0)
            throw new SecurityException("B3 outcome feedback adjustment is non-finite or exceeds its safety bound");
        return policy.baseScore(candidate) + adjustment;
    }

    private static double coverageBonus(Set<String> tags, WorkspaceAdmissionRequest request) {
        return tags.stream().mapToDouble(tag -> request.coverageValue().getOrDefault(tag, 0.0)).sum()
                * request.scoringPolicy().coverageBonusPerUnit();
    }

    private static Set<String> freshCoverage(List<WorkspaceCandidate> candidates, Set<String> alreadyCovered) {
        var tags = new LinkedHashSet<String>();
        candidates.stream().sorted(Comparator.comparing(WorkspaceCandidate::id))
                .forEach(candidate -> tags.addAll(candidate.coverageTags()));
        tags.removeAll(alreadyCovered);
        return Set.copyOf(tags);
    }

    private static boolean conflicts(WorkspaceCandidate candidate, List<WorkspaceEntry> selected) {
        return selected.stream().anyMatch(entry -> candidate.conflictsWith().contains(entry.candidate().id())
                || entry.candidate().conflictsWith().contains(candidate.id()));
    }

    private static boolean conflictsWithin(WorkspaceCandidate candidate, List<WorkspaceCandidate> group) {
        return group.stream().anyMatch(member -> candidate.conflictsWith().contains(member.id())
                || member.conflictsWith().contains(candidate.id()));
    }

    private static BundleOption chooseBest(List<BundleOption> options) {
        return options.stream().max(Comparator.comparingDouble(BundleOption::totalScore)
                .thenComparing(Comparator.comparingInt(BundleOption::contextUnits).reversed())
                .thenComparing(Comparator.comparingInt((BundleOption option) -> option.members().size()).reversed())
                .thenComparing(option -> option.members().stream().map(WorkspaceCandidate::id).sorted()
                        .collect(java.util.stream.Collectors.joining("\u001f")), Comparator.reverseOrder()))
                .orElse(null);
    }

    private static WorkspaceCoalitionProposal withSelected(WorkspaceCoalitionProposal proposal, boolean selected) {
        return new WorkspaceCoalitionProposal(proposal.id(), proposal.round(), proposal.candidateIds(),
                proposal.newlyCoveredTags(), proposal.contextUnits(), proposal.baseScore(),
                proposal.coverageBonus(), proposal.totalScore(), selected);
    }

    private static String proposalId(int round, List<String> ids, Set<String> tags) {
        String canonical = round + "|" + String.join("|", ids) + "|" + tags.stream().sorted().collect(java.util.stream.Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static WorkspaceAdmissionOutcome failedMandatory(String why) {
        return new WorkspaceAdmissionOutcome(WorkspaceAdmissionStatus.INSUFFICIENT_MANDATORY_CAPACITY, null, List.of(), why);
    }

    private record BundleOption(List<WorkspaceCandidate> members, Set<String> newlyCoveredTags, int contextUnits,
                                double baseScore, double coverageBonus, double totalScore,
                                WorkspaceCoalitionProposal proposal) { }
    private record CoalitionSearchResult(List<WorkspaceCoalitionProposal> proposals, int attempts, boolean truncated) { }
}
