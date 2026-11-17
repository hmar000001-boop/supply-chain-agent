package dev.supplychain.agent.mechanisms.b2;

import java.util.List;

@FunctionalInterface
public interface ImpasseDetector { List<ImpasseSignal> consolidate(List<ImpasseSignal> signals); }
