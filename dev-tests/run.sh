#!/usr/bin/env bash
# Compiles and runs the message migration tests without Gradle/Bukkit.
# usage: dev-tests/run.sh <legacy-en.yml> [new-en.yml]
set -euo pipefail
cd "$(dirname "$0")/.."
LEGACY="${1:-dev-tests/fixtures/legacy-en.yml}"
NEW="${2:-src/main/resources/lang/en.yml}"
OUT="$(mktemp -d)"
SRC=src/main/java/me/SuperRonanCraft/BetterRTP/references/messages/delivery
# BukkitMessageTree and MessageDelivery need the Bukkit API, everything else in the package is plain Java.
# Some JREs ship the compiler module but no `javac` launcher; fall back to invoking it directly.
if command -v javac >/dev/null 2>&1; then JAVAC=(javac); else JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main); fi
"${JAVAC[@]}" -d "$OUT" $(ls $SRC/*.java | grep -v -e BukkitMessageTree -e MessageDelivery) dev-tests/MessageMigrationTest.java
PRE=src/main/java/me/SuperRonanCraft/BetterRTP/references/preload
SHAPE=src/main/java/me/SuperRonanCraft/BetterRTP/player/rtp/RTP_SHAPE.java
# only the pure (Bukkit free) classes of the preload package are compiled here
"${JAVAC[@]}" -d "$OUT" -cp "$OUT" $SHAPE $PRE/ChunkMath.java $PRE/ChunkTicketCounter.java $PRE/KeepLoadedType.java $PRE/PositionPool.java \
    $PRE/PositionRange.java $PRE/PreloadPosition.java $PRE/PreloadSettings.java $PRE/PreloadSettingsParser.java dev-tests/PreloadTest.java
CFG=src/main/java/me/SuperRonanCraft/BetterRTP/references/file/config
# config package: ConfigFileMigration, ConfigLists and YamlComments need Bukkit, the rest is plain Java
"${JAVAC[@]}" -d "$OUT" -cp "$OUT" $CFG/ConfigVersion.java $CFG/ConfigListSetting.java $CFG/ConfigMigrator.java $CFG/CustomWorldSettings.java dev-tests/ConfigMigrationTest.java
echo "######## message migration"
java -cp "$OUT" MessageMigrationTest "$LEGACY" "$NEW"
echo "######## chunk / position pre-generation (pure parts)"
java -cp "$OUT" PreloadTest

echo "######## config-version / config migration (3.7.2)"
java -cp "$OUT" ConfigMigrationTest
