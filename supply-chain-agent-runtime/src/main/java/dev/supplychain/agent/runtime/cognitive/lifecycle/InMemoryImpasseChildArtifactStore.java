package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.mechanisms.b2.ChildResolutionResult;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryImpasseChildArtifactStore implements ImpasseChildArtifactStore {
    private final ConcurrentHashMap<String, ImpasseChildArtifact> artifacts = new ConcurrentHashMap<>();
    @Override public void save(String caseId, ChildResolutionResult result, DelegationReturn intentionReturn) {
        String key = required(caseId);
        var value = new ImpasseChildArtifact(result, intentionReturn);
        var existing = artifacts.putIfAbsent(key, value);
        if (existing != null && !existing.equals(value)) throw new SecurityException("B2 child result changed after it was recorded");
    }
    @Override public Optional<ImpasseChildArtifact> find(String caseId) { return Optional.ofNullable(artifacts.get(required(caseId))); }
    private static String required(String v) { String s=Objects.requireNonNull(v,"caseId").trim(); if(s.isEmpty())throw new IllegalArgumentException("caseId cannot be blank"); return s; }
}
