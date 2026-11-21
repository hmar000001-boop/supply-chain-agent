package dev.supplychain.agent.mechanisms.b3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One feasible alternative bundle evaluated against other bundles and singleton candidates. */
public record WorkspaceCoalitionProposal(String id, int round, List<String> candidateIds,
        Set<String> newlyCoveredTags, int contextUnits, double baseScore,
        double coverageBonus, double coalitionOutcomeAdjustment, double totalScore, boolean selected) {
    public WorkspaceCoalitionProposal {
        id = required(id, "id");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        candidateIds = Objects.requireNonNull(candidateIds, "candidateIds").stream().map(v -> required(v, "candidateId")).sorted().toList();
        if (candidateIds.size() < 2 || candidateIds.size() != candidateIds.stream().distinct().count())
            throw new IllegalArgumentException("coalition proposal requires at least two unique candidate IDs");
        newlyCoveredTags = Objects.requireNonNull(newlyCoveredTags, "newlyCoveredTags").stream()
                .map(v -> required(v, "coverageTag")).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (newlyCoveredTags.size() < 2) throw new IllegalArgumentException("coalition proposal must add at least two distinct coverage tags");
        if (contextUnits < 2) throw new IllegalArgumentException("coalition proposal context cost must be at least two units");
        if (!Double.isFinite(baseScore) || !Double.isFinite(coverageBonus) || coverageBonus < 0
                || !Double.isFinite(coalitionOutcomeAdjustment) || Math.abs(coalitionOutcomeAdjustment) > 5.0
                || !Double.isFinite(totalScore) || Double.compare(totalScore, baseScore + coverageBonus + coalitionOutcomeAdjustment) != 0)
            throw new IllegalArgumentException("coalition proposal score accounting is inconsistent");
    }

    /** Source-compatible proposal constructor before coalition-level outcome adjustment. */
    public WorkspaceCoalitionProposal(String id,int round,List<String> candidateIds,Set<String> newlyCoveredTags,
            int contextUnits,double baseScore,double coverageBonus,double totalScore,boolean selected) {
        this(id,round,candidateIds,newlyCoveredTags,contextUnits,baseScore,coverageBonus,0.0,totalScore,selected);
    }

    /** Stable content identity used to learn preference across cycles; excludes round and score. */
    public String preferenceKey() { return preferenceKeyFor(candidateIds, newlyCoveredTags); }

    public static String preferenceKeyFor(List<String> candidateIds,Set<String> newlyCoveredTags) {
        String canonical = candidateIds.stream().sorted().toList() + "|" + newlyCoveredTags.stream().sorted().toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /** Stable identity of the exact proposal, including score components and round. */
    public String fingerprint() {
        String canonical = round + "|" + String.join("|", candidateIds) + "|"
                + newlyCoveredTags.stream().sorted().toList() + "|" + contextUnits + "|"
                + Double.toHexString(baseScore) + "|" + Double.toHexString(coverageBonus)
                + "|" + Double.toHexString(coalitionOutcomeAdjustment) + "|" + Double.toHexString(totalScore);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
