#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
chmod +x ./mvnw
./mvnw -B -ntp clean verify
