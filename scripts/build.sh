#!/usr/bin/env bash
set -euo pipefail
root=${FIESTA_SERVER_DIR:?Set FIESTA_SERVER_DIR to a compatible Purpur server directory}
repo=$(cd "$(dirname "$0")/.." && pwd)
api="$root/libraries/org/purpurmc/purpur/purpur-api/1.21.11-R0.1-SNAPSHOT/purpur-api-1.21.11-R0.1-SNAPSHOT.jar"
auth="$root/plugins/AuthMe-6.0.0-Paper.jar"
luck="$root/plugins/LuckPerms-Bukkit-5.5.71.jar"
for file in "$api" "$auth" "$luck"; do [[ -f "$file" ]] || { echo "Missing build dependency: $file" >&2; exit 1; }; done
classpath=$(find "$root/libraries" -type f -name '*.jar' -print | paste -sd:)
mkdir -p "$repo/build/classes"
rm -rf "$repo/build/classes"
mkdir -p "$repo/build/classes"
javac --release 21 -proc:none -cp "$auth:$luck:$api:$classpath" -d "$repo/build/classes" "$repo/src/com/fiesta/spawnguard/SpawnGuard.java"
cp "$repo/src/plugin.yml" "$repo/src/config.yml" "$repo/build/classes/"
jar cf "$repo/build/FiestaSpawnGuard.jar" -C "$repo/build/classes" .
printf 'Built %s\n' "$repo/build/FiestaSpawnGuard.jar"
