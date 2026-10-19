#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ -x "./runtime/bin/java" ]]; then
  java_bin="./runtime/bin/java"
elif [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  java_bin="${JAVA_HOME}/bin/java"
else
  java_bin="java"
fi
exec "$java_bin" -XX:MaxRAMPercentage=70 -jar TerraForge.jar "$@"
