#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
OUTPUT_DIR="${M8_B2_OUTPUT_DIR:-$ROOT_DIR/target/m8-b2-results}"

# Full reactor verification precedes experiment execution. CI should run this same command.
./mvnw -B -ntp -Dphase0.postgres=true -pl supply-chain-agent-evaluation -am clean install >&2

./mvnw -q -ntp -pl supply-chain-agent-evaluation \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.supplychain.agent.evaluation.app.M8ComparisonCli \
  -Dexec.classpathScope=runtime \
  -Dexec.args="--output-dir $OUTPUT_DIR"
