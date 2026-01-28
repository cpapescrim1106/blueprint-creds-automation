#!/bin/bash
set -euo pipefail

# 1_harvest_keys.sh
# Automates the harvesting of AWS credentials from Blueprint OMS.

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEPS_DIR="$BASE_DIR/deps"
JDK_HOME="$DEPS_DIR/jdk/Contents/Home"
JAVA_BIN="$JDK_HOME/bin/java"
AGENT_JAR="$BASE_DIR/agent/build/blueprint-relay-agent.jar"
CLIENT_RUNTIME="$DEPS_DIR/client_runtime"
CLASSPATH_FILE="$DEPS_DIR/client_classpath.txt"
LOG_FILE="$BASE_DIR/BlueprintRelay.log" # Use local log file inside the automation dir

# Ensure dependencies exist
if [[ ! -d "$DEPS_DIR" ]]; then
    echo "Error: Dependencies not found. Please run './install_dependencies.sh <path_to_project>' first."
    exit 1
fi

# Truncate log to ensure we don't read old credentials
echo "Cleaning old logs..."
> "$LOG_FILE"

# Load OMS Credentials
CRED_FILE="$BASE_DIR/.oms_credentials"
OMS_USERNAME=""
OMS_PASSWORD=""

if [[ -f "$CRED_FILE" ]]; then
    source "$CRED_FILE"
fi

if [[ -z "$OMS_USERNAME" ]]; then
    read -p "Enter OMS Username: " OMS_USERNAME
fi

if [[ -z "$OMS_PASSWORD" || "$OMS_PASSWORD" == "put_your_password_here" ]]; then
    echo "Enter OMS Password (hidden):"
    read -s OMS_PASSWORD
fi

if [[ ! -f "$JAVA_BIN" ]]; then
  echo "Error: Java not found at $JAVA_BIN" >&2
  exit 1
fi

# Read classpath and prepend 'deps/' to each entry
# Original format: client_lib/shared/lib/foo.jar:client_lib/...
# New format: deps/client_lib/shared/lib/foo.jar:deps/client_lib/...
CLASSPATH=$(cat "$CLASSPATH_FILE" | sed "s|client_lib/|$DEPS_DIR/client_lib/|g")

echo "Starting Blueprint OMS with Auto-Login..."
echo "The agent will attempt to automatically log in."

# Start OMS in background
"$JAVA_BIN" \
  -javaagent:"$AGENT_JAR" \
  -Dblueprint.relay.log="$LOG_FILE" \
  -Drelay.dump.secrets="true" \
  -Doms.username="$OMS_USERNAME" \
  -Doms.password="$OMS_PASSWORD" \
  -Djavaws.codebase="file://$CLIENT_RUNTIME/" \
  -Dsun.java2d.fontpath="/System/Library/Fonts:/System/Library/Fonts/Supplemental:/Library/Fonts" \
  -Dswing.defaultlaf=javax.swing.plaf.nimbus.NimbusLookAndFeel \
  -cp "$CLASSPATH" \
  com.blueprint.oms.gui.OMSClient > /dev/null 2>&1 &

OMS_PID=$!

echo "OMS Client started (PID: $OMS_PID). Waiting for AWS credentials..."

# Wait loop
MAX_RETRIES=60
FOUND=0
ACCESS_KEY=""
SECRET_KEY=""
SESSION_TOKEN=""

for ((i=1; i<=MAX_RETRIES; i++)); do
    # Grep for the pattern 'secret=' which only appears in debug mode
    if grep -q "secret=" "$LOG_FILE"; then
        # Extract the LAST occurrence
        LINE=$(grep "secret=" "$LOG_FILE" | tail -n 1)
        
        # Regex parse
        if [[ $LINE =~ key=([^[:space:]]+) ]]; then ACCESS_KEY="${BASH_REMATCH[1]}"; fi
        if [[ $LINE =~ secret=([^[:space:]]+) ]]; then SECRET_KEY="${BASH_REMATCH[1]}"; fi
        
        FOUND=1
        break
    fi
    sleep 2
    echo -n "."
done

echo ""

# Kill OMS
kill "$OMS_PID" || true
wait "$OMS_PID" 2>/dev/null || true

if [[ "$FOUND" -eq 1 ]]; then
    echo "✅ Credentials captured!"
    echo "Access Key: $ACCESS_KEY"
    echo "Secret Key: $SECRET_KEY"
    
    # Save to .env.aws file in the automation root
    cat > "$BASE_DIR/.env.aws" <<EOF
AWS_ACCESS_KEY_ID=$ACCESS_KEY
AWS_SECRET_ACCESS_KEY=$SECRET_KEY
AWS_DEFAULT_REGION=us-east-2
EOF
    echo "Saved to $BASE_DIR/.env.aws"
else
    echo "❌ Timed out waiting for credentials."
    exit 1
fi
