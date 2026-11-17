#!/bin/sh
# Minimal Maven Wrapper bootstrapper. The wrapper JAR and Maven distribution
# are fetched from Maven Central on first use; versions are pinned in properties.
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$BASE_DIR"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
PROPERTIES="$WRAPPER_DIR/maven-wrapper.properties"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"

if [ ! -f "$PROPERTIES" ]; then
  echo "Missing Maven wrapper properties: $PROPERTIES" >&2
  exit 1
fi

WRAPPER_URL=$(sed -n 's/^wrapperUrl=//p' "$PROPERTIES" | tail -n 1)
if [ ! -f "$WRAPPER_JAR" ]; then
  if [ -z "${WRAPPER_URL:-}" ]; then
    echo "Missing wrapperUrl in $PROPERTIES" >&2
    exit 1
  fi
  TMP_JAR="$WRAPPER_JAR.tmp"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "$WRAPPER_URL" --output "$TMP_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget --https-only -O "$TMP_JAR" "$WRAPPER_URL"
  else
    echo "curl or wget is required to bootstrap Maven Wrapper" >&2
    exit 1
  fi
  mv "$TMP_JAR" "$WRAPPER_JAR"
fi

if [ -n "${JAVA_HOME:-}" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi
exec "$JAVACMD" \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
