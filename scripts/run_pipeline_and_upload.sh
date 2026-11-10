#!/bin/bash
set -euo pipefail

PROJECT_DIR="$HOME/TransitPulse"
VENV_PYTHON="$HOME/transitpulse-aws-venv/bin/python"

cd "$PROJECT_DIR"

echo "===================================="
echo " TransitPulse Analytics Pipeline"
echo "===================================="

echo "[1/2] Running Python data engine..."

docker compose run --rm --no-deps data-engine

echo "[2/2] Uploading analytics to AWS S3..."

"$VENV_PYTHON" data-engine/upload_to_s3.py

echo "===================================="
echo " Pipeline and S3 upload completed!"
echo "===================================="