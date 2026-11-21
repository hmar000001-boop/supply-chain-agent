package dev.supplychain.agent.mechanisms.b6;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UncertaintyCalibrationTrainerTest {
    private final MemoryScopeId scope = new MemoryScopeId("tenant-calibration");
    private final Instant at = Instant.parse("2026-11-21T15:00:00Z");
    private final UncertaintyCalibrationTrainer trainer = new UncertaintyCalibrationTrainer();

    @Test void createsTargetBoundProfileOnlyWithSufficientLabelsAndIndependentValidation() {
        var profile = trainer.train(scope, "supply-chain-recovery-v1", samples("train", 20), "trainer", at);
        assertEquals(UncertaintyCalibrationProfile.Status.TRAINED, profile.status());
        var validated = trainer.validate(profile, samples("holdout", 20), "reviewer");
        assertEquals(UncertaintyCalibrationProfile.Status.VALIDATED, validated.profile().status());
        assertTrue(validated.expectedCalibrationError() < UncertaintyCalibrationTrainer.MAXIMUM_VALIDATION_ECE);
        assertNotEquals(profile.trainingDatasetFingerprint(), validated.validationDatasetFingerprint());
    }

    @Test void refusesTrainerSelfValidationAndInsufficientSamples() {
        var profile = trainer.train(scope, "supply-chain-recovery-v1", samples("train", 20), "trainer", at);
        assertThrows(SecurityException.class, () -> trainer.validate(profile, samples("holdout", 20), "trainer"));
        assertThrows(IllegalArgumentException.class, () -> trainer.train(scope, "supply-chain-recovery-v1",
                samples("small", 19), "trainer", at));
    }

    @Test void rejectsHoldoutThatReusesTrainingSampleIdentityEvenWhenDatasetFingerprintDiffers() {
        var training = samples("sample-overlap-train", 20);
        var profile = trainer.train(scope, "supply-chain-recovery-v1", training, "trainer", at);
        var holdout = new ArrayList<>(samples("sample-overlap-holdout", 20));
        var reused = training.getFirst();
        holdout.set(0, new UncertaintyCalibrationObservation(reused.sampleId(), scope, reused.targetId(),
                reused.dimension(), 0.1, false,
                new EvidenceReference("label", "new-evidence-for-same-sample"), at.plusSeconds(100)));
        assertNotEquals(profile.trainingDatasetFingerprint(), UncertaintyCalibrationTrainer.fingerprint(holdout));
        assertThrows(SecurityException.class, () -> trainer.validate(profile, holdout, "reviewer"));
    }

    @Test void rejectsHoldoutThatReusesTrainingEvidenceUnderANewSampleIdentity() {
        var training = samples("evidence-overlap-train", 20);
        var profile = trainer.train(scope, "supply-chain-recovery-v1", training, "trainer", at);
        var holdout = new ArrayList<>(samples("evidence-overlap-holdout", 20));
        var reused = training.getFirst();
        holdout.set(0, new UncertaintyCalibrationObservation("different-sample-id", scope, reused.targetId(),
                reused.dimension(), 0.1, false, reused.labelEvidence(), at.plusSeconds(100)));
        assertThrows(SecurityException.class, () -> trainer.validate(profile, holdout, "reviewer"));
    }

    @Test void activatedProfileCalibratesAllDimensionsButUncalibratedAssessmentRemainsUnchanged() {
        var repo = new InMemoryUncertaintyCalibrationRepository();
        var service = new UncertaintyCalibrationService(repo, trainer);
        var profile = service.train(scope, "supply-chain-recovery-v1", samples("train", 20), "trainer", at);
        var validated = service.validate(profile.id(), profile.version(), samples("holdout", 20), "reviewer");
        var active = service.activate(validated.id(), validated.version(), "approver", at.plusSeconds(20));
        var raw = assessment(at.plusSeconds(2));
        assertEquals(UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED, raw.calibration().status());
        var calibrated = service.calibrate(raw, "supply-chain-recovery-v1");
        assertEquals(UncertaintyAssessment.CalibrationStatus.CALIBRATED, calibrated.calibration().status());
        assertEquals(active.id(), calibrated.calibration().profileId());
        assertTrue(calibrated.signals().values().stream().allMatch(v -> v.uncertainty() >= 0 && v.uncertainty() <= 1));
        assertEquals(raw.reference(), calibrated.reference());
    }

    @Test void refusesCrossTargetAndCrossScopeCalibration() {
        var repo = new InMemoryUncertaintyCalibrationRepository();
        var service = new UncertaintyCalibrationService(repo, trainer);
        var profile = service.train(scope, "supply-chain-recovery-v1", samples("train", 20), "trainer", at);
        var validated = service.validate(profile.id(), profile.version(), samples("holdout", 20), "reviewer");
        service.activate(validated.id(), validated.version(), "approver", at.plusSeconds(20));
        assertEquals(UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED,
                service.calibrate(assessment(at.plusSeconds(21)), "target-b").calibration().status());
        var otherScope = new UncertaintyAssessment.Reference("assessment-2", 1, "execution-2", "another-tenant");
        var foreign = new UncertaintyAssessment(otherScope, at, at.plusSeconds(60), "evidence:source",
                UncertaintyAssessment.Calibration.notCalibrated(), assessment(at).signals());
        assertEquals(UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED,
                service.calibrate(foreign, "target-a").calibration().status());
    }


    @Test void activatingReplacementAtomicallyRetiresThePriorTargetProfile() {
        var repository = new InMemoryUncertaintyCalibrationRepository();
        var service = new UncertaintyCalibrationService(repository, trainer);
        var first = service.train(scope, "supply-chain-recovery-v1", samples("first-train", 20), "trainer-a", at);
        var firstValidated = service.validate(first.id(), first.version(), samples("first-holdout", 20), "reviewer-a");
        var firstActive = service.activate(firstValidated.id(), firstValidated.version(), "approver-a", at.plusSeconds(20));
        var second = service.train(scope, "supply-chain-recovery-v1", samples("second-train", 20), "trainer-b", at.plusSeconds(2));
        var secondValidated = service.validate(second.id(), second.version(), samples("second-holdout", 20), "reviewer-b");

        var secondActive = service.activate(secondValidated.id(), secondValidated.version(), "approver-b", at.plusSeconds(23));

        assertEquals(UncertaintyCalibrationProfile.Status.RETIRED,
                repository.findById(firstActive.id()).orElseThrow().status());
        assertEquals(UncertaintyCalibrationProfile.Status.ACTIVE, secondActive.status());
        assertEquals(secondActive.id(), repository.findActive(scope, "supply-chain-recovery-v1").orElseThrow().id());
    }

    @Test void calibrationLifecycleRejectsActivationBeforeValidationAndActorReuse() {
        var profile = trainer.train(scope, "supply-chain-recovery-v1", samples("guard-train", 20), "same-actor", at);
        assertThrows(SecurityException.class,
                () -> trainer.validate(profile, samples("guard-holdout", 20), "same-actor"));
        var validated = trainer.validate(profile, samples("guard-holdout", 20), "reviewer").profile();
        Instant validationCompletedAt = at.plusSeconds(19);
        assertThrows(IllegalArgumentException.class, () -> validated.activate("approver", validationCompletedAt.minusSeconds(1)));
        assertThrows(SecurityException.class, () -> validated.activate("same-actor", validationCompletedAt.plusSeconds(1)));
    }

    private List<UncertaintyCalibrationObservation> samples(String prefix, int perDimension) {
        var result = new ArrayList<UncertaintyCalibrationObservation>();
        for (var dimension : UncertaintyDimension.values()) for (int i = 0; i < perDimension; i++) {
            result.add(new UncertaintyCalibrationObservation(prefix + "-" + dimension + "-" + i, scope,
                    "supply-chain-recovery-v1", dimension, 0.1, i < 2,
                    new EvidenceReference("label", prefix + "-" + dimension + "-" + i), at.plusSeconds(i)));
        }
        return List.copyOf(result);
    }
    private UncertaintyAssessment assessment(Instant now) {
        var signals = new java.util.EnumMap<UncertaintyDimension, UncertaintySignal>(UncertaintyDimension.class);
        for (var dimension : UncertaintyDimension.values()) signals.put(dimension,
                new UncertaintySignal(dimension, 0.1, "host assessment", 20));
        return new UncertaintyAssessment(new UncertaintyAssessment.Reference("assessment-1", 1,
                "execution-1", scope.value()), now, now.plusSeconds(60), "evidence:source",
                UncertaintyAssessment.Calibration.notCalibrated(), signals);
    }
}
