package dev.supplychain.agent.mechanisms.b4;

import java.util.Objects;

public record RecoveryMethodReference(String methodId,String candidatePlanReference){
    public RecoveryMethodReference{methodId=require(methodId,"methodId");candidatePlanReference=require(candidatePlanReference,"candidatePlanReference");}
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
