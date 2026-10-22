#!/bin/bash
# TerraForge launcher for macOS (unsigned app; Java 21 runtime included).
APP="$(cd "$(dirname "$0")/.." && pwd)"
DATA="$HOME/Library/Application Support/TerraForge"
mkdir -p "$DATA/cache/lwjgl"
JAVA="$APP/Java/runtime/bin/java"
if ! "$JAVA" -version >/dev/null 2>&1; then
  osascript -e 'display alert "TerraForge could not start Java" message "This copy of TerraForge is for a different Mac processor (Apple Silicon vs Intel), or macOS blocked it. Download the matching version, then run in Terminal:  xattr -cr /Applications/TerraForge.app" as critical'
  exit 1
fi
cd "$DATA"
exec "$JAVA" -XX:MaxRAMPercentage=70 \
  -Dterraforge.home="$DATA" \
  -Dorg.lwjgl.glfw.libname=glfw_async \
  -Dorg.lwjgl.system.SharedLibraryExtractPath="$DATA/cache/lwjgl" \
  -Dapple.awt.application.name=TerraForge \
  -Xdock:name=TerraForge -Xdock:icon="$APP/Resources/TerraForge.icns" \
  -jar "$APP/Java/TerraForge.jar" "$@" >>"$DATA/terraforge.log" 2>&1
