#!/usr/bin/env sh
set -eu
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
chmod +x ./mvnw
./mvnw -B -ntp -pl supply-chain-agent-app -am install
./mvnw -B -ntp -pl supply-chain-agent-app exec:java
