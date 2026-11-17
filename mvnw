#!/bin/sh
# Pinned, checksum-verified Apache Maven bootstrapper.
# The distribution is downloaded directly from Apache, not through Maven Central.
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$PROPERTIES" ]; then
  echo "Missing Maven wrapper properties: $PROPERTIES" >&2
  exit 1
fi

property() {
  key=$1
  sed -n "s/^${key}=//p" "$PROPERTIES" | tail -n 1
}

MAVEN_VERSION=$(property distributionVersion)
PRIMARY_URL=$(property distributionUrl)
FALLBACK_URL=$(property distributionFallbackUrl)
EXPECTED_SHA512=$(property distributionSha512Sum)

if [ -z "$MAVEN_VERSION" ] || [ -z "$PRIMARY_URL" ] || [ -z "$EXPECTED_SHA512" ]; then
  echo "Incomplete Maven distribution configuration in $PROPERTIES" >&2
  exit 1
fi

if [ -n "${MAVEN_USER_HOME:-}" ]; then
  MAVEN_CACHE_ROOT=$MAVEN_USER_HOME
else
  MAVEN_CACHE_ROOT=${HOME:?HOME must be set}/.m2
fi
MAVEN_HOME="$MAVEN_CACHE_ROOT/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_EXECUTABLE="$MAVEN_HOME/bin/mvn"

if [ ! -x "$MAVEN_EXECUTABLE" ]; then
  CACHE_PARENT=$(dirname "$MAVEN_HOME")
  mkdir -p "$CACHE_PARENT"
  WORK_DIR=$(mktemp -d "$CACHE_PARENT/.maven-bootstrap.XXXXXX")
  trap 'rm -rf "$WORK_DIR"' 0 1 2 15
  ARCHIVE="$WORK_DIR/apache-maven-$MAVEN_VERSION-bin.zip"
  DOWNLOADED=0

  for URL in "$PRIMARY_URL" "${FALLBACK_URL:-}"; do
    [ -n "$URL" ] || continue
    echo "Downloading Apache Maven $MAVEN_VERSION from $URL"
    if command -v curl >/dev/null 2>&1; then
      if curl --fail --location --silent --show-error \
          --connect-timeout 20 --max-time 600 \
          --retry 5 --retry-all-errors --retry-delay 5 --retry-max-time 240 \
          "$URL" --output "$ARCHIVE"; then
        DOWNLOADED=1
        break
      fi
    elif command -v wget >/dev/null 2>&1; then
      if wget --https-only --tries=6 --waitretry=5 --timeout=30 \
          -O "$ARCHIVE" "$URL"; then
        DOWNLOADED=1
        break
      fi
    else
      echo "curl or wget is required to bootstrap Apache Maven" >&2
      exit 1
    fi
  done

  if [ "$DOWNLOADED" -ne 1 ]; then
    echo "Unable to download Apache Maven $MAVEN_VERSION from the configured Apache URLs" >&2
    exit 1
  fi

  if command -v sha512sum >/dev/null 2>&1; then
    ACTUAL_SHA512=$(sha512sum "$ARCHIVE" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA512=$(shasum -a 512 "$ARCHIVE" | awk '{print $1}')
  else
    echo "sha512sum or shasum is required to verify the Maven distribution" >&2
    exit 1
  fi

  if [ "$ACTUAL_SHA512" != "$EXPECTED_SHA512" ]; then
    echo "Apache Maven archive SHA-512 mismatch" >&2
    echo "Expected: $EXPECTED_SHA512" >&2
    echo "Actual:   $ACTUAL_SHA512" >&2
    exit 1
  fi

  if ! command -v unzip >/dev/null 2>&1; then
    echo "unzip is required to install the Maven distribution" >&2
    exit 1
  fi
  unzip -q "$ARCHIVE" -d "$WORK_DIR"
  EXTRACTED_HOME="$WORK_DIR/apache-maven-$MAVEN_VERSION"
  if [ ! -x "$EXTRACTED_HOME/bin/mvn" ]; then
    echo "Downloaded archive did not contain the expected Maven executable" >&2
    exit 1
  fi

  # Install atomically so a failed extraction cannot leave a partial cache entry.
  if [ ! -x "$MAVEN_EXECUTABLE" ]; then
    rm -rf "$MAVEN_HOME"
    mv "$EXTRACTED_HOME" "$MAVEN_HOME"
  fi
fi

exec "$MAVEN_EXECUTABLE" "$@"
