#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ -x ./runtime/bin/java ]]; then
  java_bin=./runtime/bin/java
elif [[ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]]; then
  java_bin=/usr/lib/jvm/java-21-openjdk-amd64/bin/java
elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] && "$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | grep -Eq '"21[.]'; then
  java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1 && java -version 2>&1 | head -n 1 | grep -Eq '"21[.]'; then
  java_bin=java
else
  echo 'TerraForge requires Java 21 desktop support. Use the portable download or install openjdk-21-jre.' >&2
  exit 2
fi
exec "$java_bin" -XX:MaxRAMPercentage=70 -jar TerraForge.jar "$@"
