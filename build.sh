#!/usr/bin/env bash
set -euo pipefail

if [ -z "${SPIGOT_API_JAR:-}" ]; then
  echo "Bitte weise den Pfad zur spigot-api-jar per Umgebungsvariable SPIGOT_API_JAR zu."
  echo "Beispiel: export SPIGOT_API_JAR=$HOME/.m2/repository/org/spigotmc/spigot-api/1.21-R0.1-SNAPSHOT/spigot-api-1.21-R0.1-SNAPSHOT.jar"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/target/classes"

mkdir -p "$OUT"
javac -cp "$SPIGOT_API_JAR" -d "$OUT" $(find "$ROOT/src/main/java" -name "*.java")

# Jar bauen
mkdir -p "$ROOT/target"
cd "$OUT"
jar cmf "$ROOT/manifest.mf" "$ROOT/target/ShopPlugin.jar" .
cd "$ROOT"
echo "Fertig: $ROOT/target/ShopPlugin.jar"
