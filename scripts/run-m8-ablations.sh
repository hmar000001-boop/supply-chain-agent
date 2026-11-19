#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
OUTPUT_DIR="${M8_B3_OUTPUT_DIR:-$ROOT_DIR/target/m8-b3-results}"

# Build the already CI-verified reactor without running tests, then execute only the deterministic probes.
./mvnw -B -ntp -DskipTests -pl supply-chain-agent-evaluation -am install >&2
./mvnw -q -ntp -pl supply-chain-agent-evaluation \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.supplychain.agent.evaluation.app.M8AblationCli \
  -Dexec.classpathScope=runtime \
  -Dexec.args="--output-dir $OUTPUT_DIR"
