package dev.supplychain.agent.mechanisms.b3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Trains a shadow-only B3 coalition preference candidate, evaluates it on independent paired holdout
 * runs, and activates only after a separate host-issued approval.
 */
public final class B3CoalitionFeedbackPolicyService {
    private final int minimumHoldoutScenarios;
    private final double maximumMeanResourceIncrease;

    public B3CoalitionFeedbackPolicyService(int minimumHoldoutScenarios,double maximumMeanResourceIncrease) {
        if(minimumHoldoutScenarios<5)throw new IllegalArgumentException("at least five independent holdout scenarios are required");
        if(!Double.isFinite(maximumMeanResourceIncrease)||maximumMeanResourceIncrease<0||maximumMeanResourceIncrease>1)
            throw new IllegalArgumentException("maximumMeanResourceIncrease must be in [0,1]");
        this.minimumHoldoutScenarios=minimumHoldoutScenarios;this.maximumMeanResourceIncrease=maximumMeanResourceIncrease;
    }

    public B3CoalitionFeedbackCandidate train(String policyId,long version,
            List<VerifiedWorkspaceCoalitionOutcomeEvidence> verified,int minimumObservationsPerCoalition,
            double maximumAbsoluteAdjustment,Instant trainedAt) {
        Objects.requireNonNull(verified,"verified");Objects.requireNonNull(trainedAt,"trainedAt");
        if(verified.isEmpty())throw new IllegalArgumentException("cannot train coalition feedback without verified outcomes");
        if(minimumObservationsPerCoalition<1||!Double.isFinite(maximumAbsoluteAdjustment)||maximumAbsoluteAdjustment<=0||maximumAbsoluteAdjustment>5)
            throw new IllegalArgumentException("invalid coalition training limits");
        var evidence=verified.stream().map(v->Objects.requireNonNull(v,"verified outcome").evidence())
                .sorted(Comparator.comparing(WorkspaceCoalitionOutcomeEvidence::idempotencyKey)).toList();
        var scope=evidence.getFirst().scopeId();
        if(evidence.stream().anyMatch(e->!scope.equals(e.scopeId())))throw new SecurityException("coalition training cannot mix scopes");
        if(evidence.stream().anyMatch(e->e.verifiedAt().isAfter(trainedAt)))throw new IllegalArgumentException("training time predates verified outcomes");
        if(evidence.stream().map(WorkspaceCoalitionOutcomeEvidence::idempotencyKey).distinct().count()!=evidence.size())
            throw new IllegalArgumentException("coalition outcome IDs must be unique");
        Set<String> units=verified.stream().map(VerifiedWorkspaceCoalitionOutcomeEvidence::experimentUnitFingerprint).collect(Collectors.toUnmodifiableSet());
        if(units.size()<2)throw new IllegalArgumentException("coalition learning requires at least two distinct experimental units");
        var grouped=evidence.stream().collect(Collectors.groupingBy(WorkspaceCoalitionOutcomeEvidence::preferenceKey,TreeMap::new,Collectors.toList()));
        var adjustments=new TreeMap<String,Double>();
        grouped.forEach((key,events)->{
            if(events.size()<minimumObservationsPerCoalition)return;
            long useful=events.stream().filter(e->e.signal()==DownstreamOutcomeSignal.USEFUL).count();
            long harmful=events.stream().filter(e->e.signal()==DownstreamOutcomeSignal.HARMFUL).count();
            double normalized=(useful-harmful)/(double)events.size();
            adjustments.put(key,Math.max(-maximumAbsoluteAdjustment,Math.min(maximumAbsoluteAdjustment,normalized*maximumAbsoluteAdjustment)));
        });
        String canonicalOutcomes=evidence.stream().map(B3CoalitionFeedbackPolicyService::canonicalOutcome).toList().toString();
        String source=sha(scope.value()+"|"+policyId+"|"+version+"|"+canonicalOutcomes+"|"+new TreeMap<>(adjustments)+"|"+trainedAt);
        return new B3CoalitionFeedbackCandidate(policyId,version,scope,adjustments,
                evidence.stream().map(WorkspaceCoalitionOutcomeEvidence::idempotencyKey).toList(),units,
                minimumObservationsPerCoalition,maximumAbsoluteAdjustment,source,trainedAt,
                B3CoalitionFeedbackCandidate.Disposition.SHADOW);
    }

    public B3CoalitionFeedbackEvaluationReport evaluate(B3CoalitionFeedbackCandidate candidate,
            List<B3CoalitionFeedbackObservation> observations,B3CoalitionFeedbackEvidenceVerifier verifier,Instant evaluatedAt) {
        Objects.requireNonNull(candidate,"candidate");Objects.requireNonNull(observations,"observations");
        Objects.requireNonNull(verifier,"verifier");Objects.requireNonNull(evaluatedAt,"evaluatedAt");
        var ordered=observations.stream().map(v->Objects.requireNonNull(v,"observation"))
                .sorted(Comparator.comparing(B3CoalitionFeedbackObservation::scenarioId)).toList();
        if(ordered.stream().map(B3CoalitionFeedbackObservation::scenarioId).distinct().count()!=ordered.size())
            throw new IllegalArgumentException("holdout scenario IDs must be unique");
        if(ordered.stream().map(B3CoalitionFeedbackObservation::experimentUnitFingerprint).distinct().count()!=ordered.size())
            throw new IllegalArgumentException("holdout experimental units must be independent and unique");
        if(ordered.stream().anyMatch(o->candidate.trainingUnitFingerprints().contains(o.experimentUnitFingerprint())))
            throw new SecurityException("training and holdout experimental units overlap");
        if(ordered.stream().anyMatch(o->!verifier.verify(candidate,o,evaluatedAt)))
            throw new SecurityException("one or more coalition holdout run pairs failed authoritative verification");
        if(ordered.stream().anyMatch(o->o.experimentUnitFingerprint().isBlank()))throw new SecurityException("holdout unit fingerprint is missing");
        double[] gains=ordered.stream().mapToDouble(o->o.candidateUtility()-o.baselineUtility()).toArray();
        double mean=mean(gains);double variance=0;
        if(gains.length>1){for(double gain:gains)variance+=(gain-mean)*(gain-mean);variance/=gains.length-1;}
        double lcb=ordered.size()>1?mean-1.96*Math.sqrt(variance/ordered.size()):Double.NEGATIVE_INFINITY;
        int safety= (int)ordered.stream().filter(o->o.candidateSafetyViolations()>o.baselineSafetyViolations()).count();
        double resourceIncrease=ordered.stream().mapToDouble(o->o.candidateResourceUnits()-o.baselineResourceUnits()).average().orElse(0.0);
        boolean eligible=ordered.size()>=minimumHoldoutScenarios&&safety==0&&lcb>0
                &&resourceIncrease<=maximumMeanResourceIncrease;
        String reason=eligible?"independent paired holdout passed utility, safety and resource thresholds"
                :"holdout rejected: insufficient scenarios, non-positive utility lower bound, safety regression, or excessive resource increase";
        String fp=sha(candidateFingerprint(candidate)+"|"+ordered+"|"+Double.toHexString(mean)+"|"+Double.toHexString(lcb)
                +"|"+safety+"|"+Double.toHexString(resourceIncrease)+"|"+evaluatedAt);
        return new B3CoalitionFeedbackEvaluationReport(candidate.policyId(),candidate.version(),candidateFingerprint(candidate),
                ordered.size(),mean,lcb,safety,resourceIncrease,eligible
                ?B3CoalitionFeedbackEvaluationReport.Disposition.ELIGIBLE_FOR_AUTHORIZED_ACTIVATION
                :B3CoalitionFeedbackEvaluationReport.Disposition.REJECTED,reason,fp,evaluatedAt);
    }

    public ActiveB3CoalitionFeedbackPolicy activate(B3CoalitionFeedbackCandidate candidate,
            B3CoalitionFeedbackEvaluationReport report,B3CoalitionFeedbackActivationApproval approval,
            B3CoalitionFeedbackActivationAuthority authority,Instant now) {
        Objects.requireNonNull(candidate,"candidate");Objects.requireNonNull(report,"report");Objects.requireNonNull(approval,"approval");
        Objects.requireNonNull(authority,"authority");Objects.requireNonNull(now,"now");
        if(!report.policyId().equals(candidate.policyId())||report.policyVersion()!=candidate.version()
                ||!report.candidateFingerprint().equals(candidateFingerprint(candidate)))
            throw new SecurityException("coalition activation report does not refer to this exact candidate");
        if(report.decision()!=B3CoalitionFeedbackEvaluationReport.Disposition.ELIGIBLE_FOR_AUTHORIZED_ACTIVATION)
            throw new SecurityException("coalition candidate did not pass independent holdout evaluation");
        if(!approval.scopeId().equals(candidate.scopeId())||!approval.policyId().equals(candidate.policyId())
                ||approval.policyVersion()!=candidate.version()||!approval.evaluationFingerprint().equals(report.evaluationFingerprint()))
            throw new SecurityException("coalition activation approval is not bound to this exact candidate and report");
        if(approval.approvedAt().isBefore(report.evaluatedAt())||approval.approvedAt().isAfter(now))
            throw new IllegalArgumentException("coalition activation approval timestamp is invalid");
        if(!authority.isAuthorized(candidate,report,approval.approverId(),approval.approvalReference(),now))
            throw new SecurityException("coalition policy activation approval is not authorized");
        return new ActiveB3CoalitionFeedbackPolicy(candidate,report,approval);
    }

    /** Stable source-ledger representation; avoids iteration-order dependence in Set-valued record fields. */
    private static String canonicalOutcome(WorkspaceCoalitionOutcomeEvidence value) {
        return value.idempotencyKey()+"|"+value.receiptId()+"|"+value.scopeId().value()+"|"+value.workspaceId()+"|"
                +value.cycleId()+"|"+value.consumerId()+"|"+value.coalitionId()+"|"+value.proposalFingerprint()+"|"
                +value.preferenceKey()+"|"+value.candidateIds().stream().sorted().toList()+"|"+value.signal()+"|"
                +value.verifierId()+"|"+value.evidence().stream().map(e->e.evidenceType()+":"+e.evidenceId()).sorted().toList()+"|"
                +value.experimentUnitFingerprint()+"|"+value.admissionPolicyVersion()+"|"+value.verifiedAt();
    }

    public static String candidateFingerprint(B3CoalitionFeedbackCandidate candidate) {
        return sha(candidate.policyId()+"|"+candidate.version()+"|"+candidate.scopeId().value()+"|"
                +new TreeMap<>(candidate.preferenceAdjustments())+"|"+candidate.sourceOutcomeIds()+"|"
                +candidate.trainingUnitFingerprints().stream().sorted().toList()+"|"+candidate.sourceFingerprint());
    }
    private static double mean(double[] values){if(values.length==0)return 0;double sum=0;for(double v:values)sum+=v;return sum/values.length;}
    private static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}}
}
