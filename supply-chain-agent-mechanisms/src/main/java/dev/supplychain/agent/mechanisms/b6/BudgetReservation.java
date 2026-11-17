package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.Objects;

public record BudgetReservation(String id,String executionId,ResourceUsage reserved,ReservationStatus status,
                                Instant createdAt,ResourceUsage actualUsage){
    public BudgetReservation{
        id=require(id,"id");executionId=require(executionId,"executionId");reserved=Objects.requireNonNull(reserved,"reserved");
        status=Objects.requireNonNull(status,"status");createdAt=Objects.requireNonNull(createdAt,"createdAt");
        if((status==ReservationStatus.COMMITTED)!=(actualUsage!=null))throw new IllegalArgumentException("actual usage is required exactly for committed reservation");
    }
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
