package dev.supplychain.agent.mechanisms.b6;

/** Computational/execution budgets only; inventory, money and supplier capacity remain domain resources. */
public enum ResourceUnit { MODEL_CALLS, INPUT_TOKENS, OUTPUT_TOKENS, REASONING_EVALUATIONS, SEARCH_EXPANSIONS, MCTS_ROLLOUTS, EXTERNAL_READS, VERIFICATION_CALLS }
