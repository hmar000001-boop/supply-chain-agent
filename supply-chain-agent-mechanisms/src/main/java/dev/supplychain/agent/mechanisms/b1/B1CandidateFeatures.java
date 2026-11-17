package dev.supplychain.agent.mechanisms.b1;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Trusted feature sidecar; it contains estimates, not memory contents or authority. */
public record B1CandidateFeatures(List<Instant> accessHistory,Map<String,Double> cueAssociationStrengths,
                                  double expectedDownstreamUtility,int contextUnits,
                                  Duration measuredInfrastructureLatency,MaintenanceProfile maintenanceProfile){
    public B1CandidateFeatures{accessHistory=List.copyOf(accessHistory);cueAssociationStrengths=Map.copyOf(cueAssociationStrengths);if(!Double.isFinite(expectedDownstreamUtility)||expectedDownstreamUtility<0||contextUnits<1)throw new IllegalArgumentException("invalid candidate utility/context cost");if(measuredInfrastructureLatency==null||measuredInfrastructureLatency.isNegative())throw new IllegalArgumentException("invalid measured latency");if(maintenanceProfile==null)throw new NullPointerException("maintenanceProfile");}
}
