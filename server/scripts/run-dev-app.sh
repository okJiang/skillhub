#!/usr/bin/env bash

set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="${SPRING_PROFILES_ACTIVE:-local}"

resolve_java_bin() {
  local candidate=""
  local current=""

  if [[ -n "${JAVA_BIN:-}" ]]; then
    candidate="$JAVA_BIN"
  elif command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1; then
    candidate="$(command -v java)"
  else
    for current in \
      /opt/homebrew/opt/openjdk@21/bin/java \
      /usr/local/opt/openjdk@21/bin/java
    do
      if [[ -x "$current" ]] && "$current" -version >/dev/null 2>&1; then
        candidate="$current"
        break
      fi
    done
  fi

  if [[ -z "$candidate" ]] || [[ ! -x "$candidate" ]]; then
    echo "No usable Java runtime found. Install OpenJDK 21 or set JAVA_BIN/JAVA_HOME." >&2
    exit 1
  fi

  printf '%s\n' "$candidate"
}

cd "$SERVER_DIR"

JAVA_BIN_RESOLVED="$(resolve_java_bin)"
if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(cd "$(dirname "$JAVA_BIN_RESOLVED")/.." && pwd)"
  export JAVA_HOME
fi

./mvnw -pl skillhub-app -am clean package -DskipTests >/dev/null

APP_JAR="$(find skillhub-app/target -maxdepth 1 -type f -name 'skillhub-app-*.jar' ! -name '*.original' | head -n 1)"
if [[ -z "$APP_JAR" ]]; then
  echo "Could not locate packaged skillhub-app jar under skillhub-app/target" >&2
  exit 1
fi

exec "$JAVA_BIN_RESOLVED" -jar "$APP_JAR" --spring.profiles.active="$PROFILE" "$@"
