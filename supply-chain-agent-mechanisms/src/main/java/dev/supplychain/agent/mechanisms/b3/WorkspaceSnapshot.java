package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable result of one bounded B3 admission and coalition-competition cycle. */
public record WorkspaceSnapshot(String id, String cycleId, MemoryScopeId scopeId, int capacityUnits, int usedUnits,
                                List<WorkspaceEntry> entries, List<WorkspaceCoalition> coalitions,
                                Set<String> coveredTags, WorkspaceStatus status, long version,
                                Instant createdAt, Instant updatedAt, String experimentUnitFingerprint,
                                List<WorkspaceCoalitionProposal> evaluatedCoalitionProposals,
                                boolean coalitionSearchTruncated) {
    public WorkspaceSnapshot {
        id = require(id, "id");
        cycleId = require(cycleId, "cycleId");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        if (capacityUnits < 1 || usedUnits < 0 || usedUnits > capacityUnits || version < 0) {
            throw new IllegalArgumentException("invalid workspace accounting/version");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        coalitions = List.copyOf(Objects.requireNonNull(coalitions, "coalitions"));
        coveredTags = Set.copyOf(Objects.requireNonNull(coveredTags, "coveredTags"));
        evaluatedCoalitionProposals = List.copyOf(Objects.requireNonNull(evaluatedCoalitionProposals, "evaluatedCoalitionProposals"));
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        experimentUnitFingerprint = experimentUnitFingerprint == null ? "" : experimentUnitFingerprint.trim();
        if (!experimentUnitFingerprint.isEmpty() && !experimentUnitFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("experimentUnitFingerprint must be an empty value or SHA-256");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");

        long sum = 0L;
        var entriesById = new java.util.HashMap<String, WorkspaceEntry>();
        var recomputedCoverage = new HashSet<String>();
        for (WorkspaceEntry entry : entries) {
            if (!scopeId.equals(entry.candidate().scopeId())) {
                throw new SecurityException("workspace entry belongs to another scope");
            }
            if (entriesById.putIfAbsent(entry.candidate().id(), entry) != null) {
                throw new IllegalArgumentException("workspace snapshot contains duplicate candidate IDs");
            }
            sum += entry.candidate().contextUnits();
            if (!java.util.Collections.disjoint(recomputedCoverage, entry.newlyCoveredTags())) {
                throw new IllegalArgumentException("a workspace coverage tag cannot be newly covered twice");
            }
            recomputedCoverage.addAll(entry.newlyCoveredTags());
        }
        if (sum != usedUnits) throw new IllegalArgumentException("usedUnits does not match entries");
        if (!recomputedCoverage.equals(coveredTags)) {
            throw new IllegalArgumentException("coveredTags does not match ordered workspace admission entries");
        }

        var proposalIds = new HashSet<String>();
        var selectedRounds = new HashSet<Integer>();
        int previousRound = -1;
        for (WorkspaceCoalitionProposal proposal : evaluatedCoalitionProposals) {
            if (!proposalIds.add(proposal.id())) throw new IllegalArgumentException("duplicate coalition proposal ID");
            if (proposal.round() < previousRound) throw new IllegalArgumentException("coalition proposals are not ordered by evaluation round");
            previousRound = proposal.round();
            if (proposal.selected()) {
                if (!selectedRounds.add(proposal.round())) throw new IllegalArgumentException("multiple coalitions selected in one admission round");
                if (!entriesById.keySet().containsAll(proposal.candidateIds())
                        || !coveredTags.containsAll(proposal.newlyCoveredTags())) {
                    throw new IllegalArgumentException("selected coalition proposal references content outside the admitted snapshot");
                }
            }
        }

        if (!evaluatedCoalitionProposals.isEmpty()) {
            for (WorkspaceCoalition coalition : coalitions) {
                boolean traced = evaluatedCoalitionProposals.stream().anyMatch(proposal -> proposal.selected()
                        && proposal.candidateIds().equals(coalition.candidateIds())
                        && proposal.newlyCoveredTags().equals(coalition.jointCoverageTags())
                        && proposal.contextUnits() == coalition.contextUnits());
                if (!traced) throw new IllegalArgumentException("admitted coalition has no matching selected competition proposal");
            }
            long selectedProposalCount = evaluatedCoalitionProposals.stream().filter(WorkspaceCoalitionProposal::selected).count();
            if (selectedProposalCount != coalitions.size())
                throw new IllegalArgumentException("selected coalition proposal count does not match admitted coalitions");
        }

        for (WorkspaceCoalition coalition : coalitions) {
            if (!entriesById.keySet().containsAll(coalition.candidateIds())
                    || !coveredTags.containsAll(coalition.jointCoverageTags())) {
                throw new IllegalArgumentException("coalition references content outside the workspace snapshot");
            }
            long coalitionUnits = 0L;
            double coalitionScore = 0.0;
            for (String candidateId : coalition.candidateIds()) {
                WorkspaceEntry member = entriesById.get(candidateId);
                coalitionUnits += member.candidate().contextUnits();
                coalitionScore += member.marginalScore();
            }
            if (coalitionUnits != coalition.contextUnits()
                    || Double.compare(coalitionScore, coalition.aggregateMarginalScore()) != 0) {
                throw new IllegalArgumentException("coalition accounting does not match its workspace members");
            }
        }
    }

    /** Compatibility constructor for callers that predate explicit coalition competition traces. */
    public WorkspaceSnapshot(String id, String cycleId, MemoryScopeId scopeId, int capacityUnits, int usedUnits,
                             List<WorkspaceEntry> entries, List<WorkspaceCoalition> coalitions,
                             Set<String> coveredTags, WorkspaceStatus status, long version,
                             Instant createdAt, Instant updatedAt, String experimentUnitFingerprint) {
        this(id, cycleId, scopeId, capacityUnits, usedUnits, entries, coalitions, coveredTags, status, version,
                createdAt, updatedAt, experimentUnitFingerprint, List.of(), false);
    }

    /** Compatibility constructor for historical workspace fixtures without experimental provenance. */
    public WorkspaceSnapshot(String id, String cycleId, MemoryScopeId scopeId, int capacityUnits, int usedUnits,
                              List<WorkspaceEntry> entries, List<WorkspaceCoalition> coalitions, Set<String> coveredTags,
                              WorkspaceStatus status, long version, Instant createdAt, Instant updatedAt) {
        this(id, cycleId, scopeId, capacityUnits, usedUnits, entries, coalitions, coveredTags, status, version,
                createdAt, updatedAt, "", List.of(), false);
    }

    public WorkspaceSnapshot close(Instant at) {
        if (status == WorkspaceStatus.CLOSED) return this;
        Objects.requireNonNull(at, "at");
        if (at.isBefore(updatedAt)) throw new IllegalArgumentException("workspace time cannot move backwards");
        return new WorkspaceSnapshot(id, cycleId, scopeId, capacityUnits, usedUnits, entries, coalitions,
                coveredTags, WorkspaceStatus.CLOSED, version + 1, createdAt, at, experimentUnitFingerprint,
                evaluatedCoalitionProposals, coalitionSearchTruncated);
    }

    private static String require(String value, String name) {
        String trimmed = Objects.requireNonNull(value, name).trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return trimmed;
    }
}
