package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeSignal;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationTrainer;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Revalidates TRAIN-only B1/B2/B3/B6 provenance before any graph session is constructed. */
public final class MechanismTrainingArtifactVerifier {
    private final MechanismTrainingArtifactVerificationServices services;

    public MechanismTrainingArtifactVerifier(MechanismTrainingArtifactVerificationServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public void verify(MechanismTrainingArtifactBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        if (!bundle.protocolId().equals(dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory.PROTOCOL_ID))
            throw new SecurityException("training artifact protocol mismatch");
        if (!MechanismTrainingArtifactFingerprints.b1(bundle.b1()).equals(bundle.b1().evidenceFingerprint())
                || !MechanismTrainingArtifactFingerprints.b2(bundle.b2()).equals(bundle.b2().evidenceFingerprint())
                || !MechanismTrainingArtifactFingerprints.b3(bundle.b3()).equals(bundle.b3().evidenceFingerprint())
                || !MechanismTrainingArtifactFingerprints.b6(bundle.b6()).equals(bundle.b6().evidenceFingerprint()))
            throw new SecurityException("one or more TRAIN mechanism evidence fingerprints do not reproduce from their complete contents");
        verifyB1(bundle);
        verifyB2(bundle);
        verifyB3(bundle);
        verifyB6(bundle);
    }

    private void verifyB1(MechanismTrainingArtifactBundle bundle) {
        var artifact = bundle.b1();
        if (services.b1OutcomeVerifier() == null) throw new SecurityException("B1 independent outcome verifier is required");
        MemoryScopeId expectedScope = new MemoryScopeId(
                dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory.EXPERIMENT_MEMORY_SCOPE_ID);
        var outcomesByReference = new java.util.HashMap<B1MemoryCandidateReference, java.util.List<DownstreamOutcomeEvidence>>();
        var outcomeUnits = new java.util.HashSet<String>();
        var idempotencyKeys = new java.util.HashSet<String>();
        for (var outcome : artifact.outcomes()) {
            String unit = outcome.experimentUnitFingerprint();
            if (!bundle.trainUnitFingerprints().contains(unit) || !artifact.trainingUnitFingerprints().contains(unit))
                throw new SecurityException("B1 outcome is not bound to the frozen TRAIN split");
            if (!idempotencyKeys.add(outcome.idempotencyKey()))
                throw new SecurityException("B1 training outcomes contain duplicate idempotency keys");
            outcomeUnits.add(unit);
            var receipt = artifact.receiptsById().get(outcome.receiptId());
            if (receipt == null || receipt.status() != ConsumerReceiptStatus.ACCEPTED
                    || !receipt.scopeId().equals(outcome.scopeId())
                    || !receipt.cycleId().equals(outcome.cycleId())
                    || !receipt.workspaceId().equals(outcome.workspaceId())
                    || !receipt.consumerId().equals(outcome.consumerId())
                    || !receipt.experimentUnitFingerprint().equals(unit)
                    || outcome.verifiedAt().isBefore(receipt.receivedAt())
                    || !outcome.evidence().contains(new EvidenceReference(
                            dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceService.EXPERIMENT_UNIT_EVIDENCE_TYPE, unit))
                    || !outcome.candidateFingerprint().equals(receipt.consumedCandidateFingerprints().get(outcome.candidateId()))
                    || !outcome.memoryAttributions().equals(receipt.memoryAttributionsByCandidate().getOrDefault(outcome.candidateId(), java.util.List.of()))) {
                throw new SecurityException("B1 downstream outcome does not match its accepted receipt/attribution");
            }
            if (!outcome.scopeId().equals(expectedScope) || outcome.memoryAttributions().isEmpty())
                throw new SecurityException("B1 training outcome has no exact attribution in the synthetic research scope");
            if (!artifact.verifierId().equals(outcome.verifierId()) || !services.b1OutcomeVerifier().verify(outcome))
                throw new SecurityException("B1 downstream verifier rejected a training outcome");
            var referencesInOutcome = new java.util.HashSet<B1MemoryCandidateReference>();
            for (var attribution : outcome.memoryAttributions()) {
                B1MemoryCandidateReference reference = attribution.reference();
                if (!reference.scopeId().equals(expectedScope)
                        || !attribution.sourceCandidateFingerprint().equals(outcome.candidateFingerprint())
                        || !referencesInOutcome.add(reference)
                        || !artifact.featureSeeds().containsKey(reference))
                    throw new SecurityException("B1 attribution is duplicated, cross-scope, not consumed, or absent from the frozen feature map");
                outcomesByReference.computeIfAbsent(reference, ignored -> new java.util.ArrayList<>()).add(outcome);
            }
        }
        if (outcomeUnits.size() < 2 || !artifact.trainingUnitFingerprints().containsAll(outcomeUnits))
            throw new SecurityException("B1 feature learning must be attributable to at least two distinct TRAIN units");
        if (!artifact.featureSeeds().keySet().equals(artifact.initialFeatureStates().keySet()))
            throw new SecurityException("B1 initial/final feature references do not match");

        for (var entry : artifact.featureSeeds().entrySet()) {
            B1MemoryCandidateReference reference = entry.getKey();
            B1MemoryFeatureState initial = artifact.initialFeatureStates().get(reference);
            B1MemoryFeatureState observed = entry.getValue();
            if (!reference.scopeId().equals(expectedScope) || !initial.reference().equals(reference)
                    || !observed.reference().equals(reference))
                throw new SecurityException("B1 feature state crosses the synthetic research scope or reference version");
            var events = outcomesByReference.getOrDefault(reference, java.util.List.of()).stream()
                    .sorted(java.util.Comparator.comparing(DownstreamOutcomeEvidence::verifiedAt)
                            .thenComparing(DownstreamOutcomeEvidence::idempotencyKey)).toList();
            if (events.isEmpty()) throw new SecurityException("B1 feature seed has no independently verified source outcome");
            B1MemoryFeatureState reconstructed = initial;
            var touchedCues = new java.util.HashSet<String>();
            for (DownstreamOutcomeEvidence outcome : events) {
                if (initial.appliedOutcomeKeys().contains(outcome.idempotencyKey()))
                    throw new SecurityException("B1 training source outcome was already present in its initial feature state");
                var attribution = outcome.memoryAttributions().stream()
                        .filter(value -> value.reference().equals(reference)).findFirst().orElseThrow();
                touchedCues.addAll(attribution.queryCues());
                reconstructed = reconstructed.applyVerifiedOutcome(outcome.idempotencyKey(), outcome.signal(),
                        attribution.queryCues(), outcome.verifiedAt());
            }
            if (!reconstructed.appliedOutcomeKeys().equals(observed.appliedOutcomeKeys())
                    || reconstructed.usefulOutcomes() != observed.usefulOutcomes()
                    || reconstructed.harmfulOutcomes() != observed.harmfulOutcomes()
                    || reconstructed.neutralOutcomes() != observed.neutralOutcomes()
                    || Double.compare(reconstructed.expectedDownstreamUtility(), observed.expectedDownstreamUtility()) != 0
                    || !reconstructed.verifiedUseHistory().equals(observed.verifiedUseHistory()))
                throw new SecurityException("B1 final feature outcome counters/utility cannot be reproduced from the attributed verified outcomes");
            for (String cue : touchedCues) {
                if (!Objects.equals(reconstructed.cueAssociationStrengths().get(cue), observed.cueAssociationStrengths().get(cue)))
                    throw new SecurityException("B1 final query-cue association cannot be reproduced from the verified attributed outcomes");
            }
        }
    }

    private void verifyB2(MechanismTrainingArtifactBundle bundle) {
        if (services.b2ProcedureVerifier() == null) throw new SecurityException("B2 independent procedure verifier is required");
        Instant now = bundle.frozenAt();
        bundle.b2().proceduresByCondition().forEach((condition, procedure) -> {
            var version = procedure.procedure();
            var artifact = procedure.artifact();
            if (version.status() != dev.supplychain.agent.memory.procedural.ProcedureStatus.ACTIVE
                    || !version.knownActiveAt(now)
                    || !version.scopeId().equals(artifact.scopeId())
                    || !version.provenance().sourceId().equals(artifact.sourceDraftId())
                    || !Objects.equals(version.provenance().sourceVersion(), artifact.sourceFingerprint())
                    || !artifact.conditionKey().equals(condition)
                    || !artifact.approvedBy().equals(version.createdBy())
                    || artifact.approvedAt().isAfter(now)
                    || version.activatedAt().isAfter(now)
                    || !services.b2ProcedureVerifier().verify(procedure, now)) {
                throw new SecurityException("B2 source procedure was not independently verified as active at the frozen TRAIN boundary");
            }
            Set<String> units = bundle.b2().sourceUnitsByCondition().get(condition);
            if (units == null || units.size() < 2 || !bundle.trainUnitFingerprints().containsAll(units))
                throw new SecurityException("B2 procedure source units are not independent TRAIN units");
            Set<String> referencedUnits = artifact.evidence().stream()
                    .filter(reference -> reference.evidenceType().equals(
                            dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceService.EXPERIMENT_UNIT_EVIDENCE_TYPE))
                    .map(EvidenceReference::evidenceId).filter(units::contains).collect(java.util.stream.Collectors.toSet());
            if (!referencedUnits.equals(units))
                throw new SecurityException("B2 approved procedure must reference every declared TRAIN source unit exactly by identity");
        });
    }

    private void verifyB3(MechanismTrainingArtifactBundle bundle) {
        if (services.b3CoalitionOutcomeVerifier() == null) throw new SecurityException("B3 independent coalition outcome verifier is required");
        var artifact = bundle.b3();
        var candidate = artifact.candidate();
        var expectedScope = new MemoryScopeId(
                dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory.EXPERIMENT_MEMORY_SCOPE_ID);
        if (!candidate.scopeId().equals(expectedScope)
                || candidate.disposition() != dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackCandidate.Disposition.SHADOW)
            throw new SecurityException("B3 candidate is cross-scope or not SHADOW-only");
        var ids = new java.util.HashSet<String>();
        var units = new java.util.HashSet<String>();
        var orderedOutcomes = artifact.outcomes().stream()
                .sorted(java.util.Comparator.comparing(dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence::idempotencyKey))
                .toList();
        for (var outcome : orderedOutcomes) {
            String unit = outcome.experimentUnitFingerprint();
            var receipt = artifact.receiptsById().get(outcome.receiptId());
            var coalition = receipt == null ? null : receipt.selectedCoalitions().get(outcome.coalitionId());
            if (!bundle.trainUnitFingerprints().contains(unit) || !ids.add(outcome.idempotencyKey())
                    || receipt == null || receipt.status() != ConsumerReceiptStatus.ACCEPTED
                    || !receipt.scopeId().equals(outcome.scopeId()) || !outcome.scopeId().equals(expectedScope)
                    || !receipt.workspaceId().equals(outcome.workspaceId())
                    || !receipt.cycleId().equals(outcome.cycleId())
                    || !receipt.consumerId().equals(outcome.consumerId())
                    || !receipt.experimentUnitFingerprint().equals(unit)
                    || outcome.verifiedAt().isBefore(receipt.receivedAt())
                    || coalition == null
                    || !coalition.proposalFingerprint().equals(outcome.proposalFingerprint())
                    || !coalition.preferenceKey().equals(outcome.preferenceKey())
                    || !coalition.candidateIds().equals(outcome.candidateIds())
                    || !coalition.experimentUnitFingerprint().equals(unit)
                    || !coalition.candidateFingerprints().entrySet().stream().allMatch(entry ->
                            Objects.equals(receipt.consumedCandidateFingerprints().get(entry.getKey()), entry.getValue()))
                    || !outcome.evidence().contains(new EvidenceReference(
                            dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceService.EXPERIMENT_UNIT_EVIDENCE_TYPE, unit))
                    || !services.b3CoalitionOutcomeVerifier().verify(outcome)) {
                throw new SecurityException("B3 outcome does not match an accepted receipt's exact selected coalition and independent verifier");
            }
            units.add(unit);
        }
        if (units.size() < 2 || !candidate.trainingUnitFingerprints().equals(units)
                || !bundle.trainUnitFingerprints().containsAll(units))
            throw new SecurityException("B3 candidate source units are not exactly the distinct verified TRAIN outcomes");
        List<String> orderedIds = orderedOutcomes.stream()
                .map(dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence::idempotencyKey).toList();
        if (!candidate.sourceOutcomeIds().equals(orderedIds))
            throw new SecurityException("B3 candidate source outcome IDs do not preserve the exact ordered verified ledger");
        if (orderedOutcomes.stream().anyMatch(outcome -> outcome.verifiedAt().isAfter(candidate.trainedAt())))
            throw new SecurityException("B3 policy training timestamp predates one or more source outcomes");

        var grouped = orderedOutcomes.stream().collect(java.util.stream.Collectors.groupingBy(
                dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence::preferenceKey,
                java.util.TreeMap::new, java.util.stream.Collectors.toList()));
        var expectedAdjustments = new java.util.TreeMap<String, Double>();
        grouped.forEach((key, events) -> {
            if (events.size() < candidate.minimumObservationsPerCoalition()) return;
            long useful = events.stream().filter(value -> value.signal() == DownstreamOutcomeSignal.USEFUL).count();
            long harmful = events.stream().filter(value -> value.signal() == DownstreamOutcomeSignal.HARMFUL).count();
            double normalized = (useful - harmful) / (double) events.size();
            double adjustment = Math.max(-candidate.maximumAbsoluteAdjustment(),
                    Math.min(candidate.maximumAbsoluteAdjustment(), normalized * candidate.maximumAbsoluteAdjustment()));
            expectedAdjustments.put(key, adjustment);
        });
        if (!expectedAdjustments.equals(new java.util.TreeMap<>(candidate.preferenceAdjustments())))
            throw new SecurityException("B3 candidate preference adjustments cannot be reproduced from the verified coalition outcomes");
        String canonicalOutcomes = orderedOutcomes.stream().map(MechanismTrainingArtifactVerifier::canonicalCoalitionOutcome).toList().toString();
        String sourceFingerprint = sha256(expectedScope.value() + "|" + candidate.policyId() + "|" + candidate.version()
                + "|" + canonicalOutcomes + "|" + expectedAdjustments + "|" + candidate.trainedAt());
        if (!sourceFingerprint.equals(candidate.sourceFingerprint()))
            throw new SecurityException("B3 candidate source fingerprint does not reproduce from the verified outcome ledger");
    }

    private void verifyB6(MechanismTrainingArtifactBundle bundle) {
        if (services.b6LabelVerifier() == null) throw new SecurityException("B6 simulator label verifier is required");
        var artifact = bundle.b6();
        var profile = artifact.profile();
        var researchScope = new MemoryScopeId(
                dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory.EXPERIMENT_MEMORY_SCOPE_ID);
        var distinctUnits = artifact.labels().stream().map(B6SimulatorLabelEvidence::experimentUnitFingerprint)
                .collect(java.util.stream.Collectors.toSet());
        if (!distinctUnits.equals(artifact.trainingUnitFingerprints())
                || !bundle.trainUnitFingerprints().containsAll(distinctUnits)
                || !profile.scopeId().equals(researchScope)
                || profile.status() != dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationProfile.Status.TRAINED
                || profile.validationSampleCount() != 0 || profile.validatedAt() != null
                || !profile.validationSampleIdHashes().isEmpty() || !profile.validationEvidenceHashes().isEmpty())
            throw new SecurityException("B6 training profile is cross-scope, not TRAIN-only, or carries validation/activation data");

        var sampleIds = new java.util.HashSet<String>();
        var evidenceIds = new java.util.HashSet<String>();
        for (var label : artifact.labels()) {
            var observation = label.observation();
            if (label.split() != ExperimentSplit.TRAIN || !observation.scopeId().equals(researchScope)
                    || !observation.targetId().equals(profile.targetId())
                    || !sampleIds.add(observation.sampleId())
                    || !evidenceIds.add(observation.labelEvidence().evidenceType() + ":" + observation.labelEvidence().evidenceId())
                    || !services.b6LabelVerifier().verify(label))
                throw new SecurityException("B6 independent simulator label verification failed or reused a sample/evidence record");
        }
        String fingerprint = UncertaintyCalibrationTrainer.fingerprint(artifact.observations());
        if (!fingerprint.equals(profile.trainingDatasetFingerprint()))
            throw new SecurityException("B6 TRAIN profile does not reproduce the verified label dataset");
        if (profile.trainingSampleCount() != artifact.labels().size()
                || !profile.trainingSampleIdHashes().equals(artifact.observations().stream()
                    .map(value -> sha256("sample:" + value.sampleId())).collect(java.util.stream.Collectors.toSet()))
                || !profile.trainingEvidenceHashes().equals(artifact.observations().stream()
                    .map(value -> sha256("evidence:" + value.labelEvidence().evidenceType() + ":" + value.labelEvidence().evidenceId()))
                    .collect(java.util.stream.Collectors.toSet())))
            throw new SecurityException("B6 profile's sample/evidence provenance hashes do not identify the exact TRAIN labels");
        Instant latestObservation = artifact.observations().stream().map(value -> value.observedAt())
                .max(java.util.Comparator.naturalOrder()).orElseThrow();
        if (profile.createdAt().isBefore(latestObservation))
            throw new SecurityException("B6 calibration profile predates its verified TRAIN labels");
        for (var dimension : dev.supplychain.agent.mechanisms.b6.UncertaintyDimension.values()) {
            var samples = artifact.observations().stream().filter(value -> value.dimension() == dimension).toList();
            if (samples.size() < 100) throw new SecurityException("B6 sample count fell below preregistered TRAIN gate: " + dimension);
            int[] counts = new int[10];
            double[] predicted = new double[10];
            long[] errors = new long[10];
            for (var sample : samples) {
                int bin = Math.min(9, (int) Math.floor(sample.predictedUncertainty() * 10.0));
                counts[bin]++;
                predicted[bin] += sample.predictedUncertainty();
                if (sample.realizedError()) errors[bin]++;
            }
            var bins = profile.bins().get(dimension);
            for (int index = 0; index < 10; index++) {
                double mean = counts[index] == 0 ? 0.0 : predicted[index] / counts[index];
                double calibrated = counts[index] == 0 ? 0.0 : (errors[index] + 1.0) / (counts[index] + 2.0);
                var observed = bins.get(index);
                if (observed.sampleCount() != counts[index] || observed.realizedErrors() != errors[index]
                        || Double.compare(observed.meanPredictedUncertainty(), mean) != 0
                        || Double.compare(observed.calibratedUncertainty(), calibrated) != 0)
                    throw new SecurityException("B6 calibration bin cannot be reconstructed from the exact simulator TRAIN labels: " + dimension + "/" + index);
            }
        }
    }

    /** Independent stable canonicalization of a B3 source row, including sorted set/map-like fields. */
    private static String canonicalCoalitionOutcome(
            dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence value) {
        return value.idempotencyKey()+"|"+value.receiptId()+"|"+value.scopeId().value()+"|"+value.workspaceId()+"|"
                +value.cycleId()+"|"+value.consumerId()+"|"+value.coalitionId()+"|"+value.proposalFingerprint()+"|"
                +value.preferenceKey()+"|"+value.candidateIds().stream().sorted().toList()+"|"+value.signal()+"|"
                +value.verifierId()+"|"+value.evidence().stream().map(e->e.evidenceType()+":"+e.evidenceId()).sorted().toList()+"|"
                +value.experimentUnitFingerprint()+"|"+value.admissionPolicyVersion()+"|"+value.verifiedAt();
    }

    private static String sha256(String value) {
        return ExperimentEvidenceFingerprint.sha256(value);
    }
}
