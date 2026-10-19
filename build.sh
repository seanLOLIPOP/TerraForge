#!/usr/bin/env bash
set -e
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
mkdir -p classes
mkdir -p classes/assets
cp assets/tree-library.trees classes/assets/
javac --release 21 -cp "lib/*" -d classes src/*.java
jar --create --file TerraForge.jar.new --manifest tools/manifest.mf -C classes .
mv -- TerraForge.jar.new TerraForge.jar
