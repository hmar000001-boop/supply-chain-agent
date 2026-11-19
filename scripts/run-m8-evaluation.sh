#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Install the reactor first so the evaluation module can be launched alone with its runtime
# dependency classpath. This intentionally runs the full test suite before generating results.
./mvnw -B -ntp -pl supply-chain-agent-evaluation -am clean install >&2

ARGS="${*:-}"
./mvnw -q -ntp -pl supply-chain-agent-evaluation \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.supplychain.agent.evaluation.app.M8EvaluationCli \
  -Dexec.classpathScope=runtime \
  -Dexec.args="$ARGS"
