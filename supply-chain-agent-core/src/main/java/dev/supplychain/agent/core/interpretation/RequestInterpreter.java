package dev.supplychain.agent.core.interpretation;

/** Application-owned port; framework adapters implement this contract. */
@FunctionalInterface
public interface RequestInterpreter {
    RequestInterpretation interpret(InterpretationRequest request);
}
