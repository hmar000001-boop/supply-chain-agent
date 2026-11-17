package dev.supplychain.agent.mechanisms.b4;

import java.util.Objects;
import java.util.UUID;

public record RecoveryIntentionId(String value){public RecoveryIntentionId{value=require(value);}public static RecoveryIntentionId random(){return new RecoveryIntentionId(UUID.randomUUID().toString());}private static String require(String v){String s=Objects.requireNonNull(v,"value").trim();if(s.isEmpty())throw new IllegalArgumentException("value cannot be blank");return s;}}
