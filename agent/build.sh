#!/bin/bash
set -euo pipefail

# Resolve important paths.
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_JDK="$BASE_DIR/../jdk8u462-b08/Contents/Home"
JDK_HOME="${JAVA_HOME:-$DEFAULT_JDK}"
JAVAC_BIN="$JDK_HOME/bin/javac"
JAR_BIN="$JDK_HOME/bin/jar"

if [[ ! -x "$JAVAC_BIN" ]]; then
  echo "javac not found at $JAVAC_BIN" >&2
  exit 1
fi

BUILD_DIR="$BASE_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
MANIFEST_PATH="$BUILD_DIR/MANIFEST.MF"
JAR_PATH="$BUILD_DIR/blueprint-relay-agent.jar"

rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR"

# Compile the agent sources (Java 8 bytecode keeps us consistent with the captured client).
find "$BASE_DIR/src" -name '*.java' | sort > "$BUILD_DIR/sources.list"
"$JAVAC_BIN" \
  -source 1.8 \
  -target 1.8 \
  -encoding US-ASCII \
  -cp "$BASE_DIR/lib/byte-buddy-1.14.10.jar:$BASE_DIR/lib/asm-9.5.jar" \
  -d "$CLASSES_DIR" \
  @"$BUILD_DIR/sources.list"

cat > "$MANIFEST_PATH" <<'EOF'
Premain-Class: com.blueprint.relay.Agent
Can-Redefine-Classes: true
EOF

# Bundle ByteBuddy + ASM into the agent jar (fat jar) so javaagent can load it.
if [[ -f "$BASE_DIR/lib/byte-buddy-1.14.10.jar" ]]; then
  (cd "$CLASSES_DIR" && "$JAR_BIN" xf "$BASE_DIR/lib/byte-buddy-1.14.10.jar")
fi
if [[ -f "$BASE_DIR/lib/asm-9.5.jar" ]]; then
  (cd "$CLASSES_DIR" && "$JAR_BIN" xf "$BASE_DIR/lib/asm-9.5.jar")
fi

"$JAR_BIN" cmf "$MANIFEST_PATH" "$JAR_PATH" -C "$CLASSES_DIR" .

echo "Created agent jar at $JAR_PATH"
