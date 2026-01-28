#!/bin/bash
set -euo pipefail

# 3_download_result.sh
# Downloads the report result from S3.

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PYTHON_SCRIPT="$BASE_DIR/lib/download_report.py"
ENV_FILE="$BASE_DIR/.env.aws"

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <s3_key> [--wait]"
    exit 1
fi

KEY="$1"
shift

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: Credentials file not found at $ENV_FILE"
    echo "Run 'bin/1_harvest_keys.sh' first."
    exit 1
fi

echo "Downloading report..."
python "$PYTHON_SCRIPT" "$KEY" --env "$ENV_FILE" "$@"
