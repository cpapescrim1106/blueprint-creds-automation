#!/bin/bash
set -euo pipefail

# 2_trigger_report.sh
# Sends a report request payload to the SQS queue.

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PYTHON_SCRIPT="$BASE_DIR/lib/invoke_report.py"
ENV_FILE="$BASE_DIR/.env.aws"

# Check dependencies
# We assume python is available in the environment. 
# If strict isolation is needed, we should create a venv inside this folder.
# For now, we rely on the system or activated python.

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <payload.json> [queue_url]"
    exit 1
fi

PAYLOAD="$1"
QUEUE="${2:-https://sqs.us-east-2.amazonaws.com/438704307340/FL_accQueue}"

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: Credentials file not found at $ENV_FILE"
    echo "Run 'bin/1_harvest_keys.sh' first."
    exit 1
fi

echo "Triggering report..."
python "$PYTHON_SCRIPT" --payload "$PAYLOAD" --queue "$QUEUE" --env "$ENV_FILE"
