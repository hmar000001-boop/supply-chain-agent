package dev.supplychain.agent.mechanisms.b6;

import java.util.Objects;

public record MetaControlDecision(MetaControlAction action,MetaControlReason reason,ResumeCondition resumeCondition,
                                  String explanation,double maximumUncertainty){
    public MetaControlDecision{
        action=Objects.requireNonNull(action,"action");reason=Objects.requireNonNull(reason,"reason");
        explanation=Objects.requireNonNull(explanation,"explanation");
        if(explanation.isBlank()||!Double.isFinite(maximumUncertainty)||maximumUncertainty<0||maximumUncertainty>1)throw new IllegalArgumentException("invalid decision details");
        switch(action){
            case REQUEST_EVIDENCE -> {if(resumeCondition!=ResumeCondition.NEW_EVIDENCE)throw new IllegalArgumentException("evidence request must resume on new evidence");}
            case INTERRUPT_FOR_HUMAN -> {if(resumeCondition!=ResumeCondition.HUMAN_DECISION && resumeCondition!=ResumeCondition.BUDGET_REPLENISHED)throw new IllegalArgumentException("human interruption requires a human decision or authorized budget replenishment");}
            case STOP -> {if(resumeCondition!=ResumeCondition.NEVER)throw new IllegalArgumentException("terminal stop must use NEVER resume condition");}
            case CONTINUE, REDUCE_COMPUTATION -> {if(resumeCondition!=null)throw new IllegalArgumentException("non-interrupt decision cannot have resume condition");}
        }
    }
}
