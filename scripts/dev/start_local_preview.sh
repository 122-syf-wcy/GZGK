#!/usr/bin/env bash
# Zero-container lightweight local preview runner (uses ~300 MB total RAM on Mac):
#   - gzgk-core  (Java 21 Virtual Threads, local-files mode reading real scripts/data/, port 8082)
#   - gzgk-agent (Python 3.12 FastAPI + LangGraph Multi-Agent, port 8001)
#   - gzgk-web   (Vue 3 + Vite dev server, port 5173)
#
# Press Ctrl+C to stop all three processes cleanly.

set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

if [ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

cleanup() {
  echo ""
  echo "[dev] Shutting down preview services..."
  kill $(jobs -p) 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "[dev] Starting gzgk-core (8082)..."
(cd "${ROOT_DIR}/backend-java" && mvn -q -pl gzgk-core spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx256m") &

echo "[dev] Starting gzgk-agent (8001)..."
(cd "${ROOT_DIR}/backend-python/gzgk-agent" && .venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8001) &

echo "[dev] Starting gzgk-web (http://127.0.0.1:5173)..."
(cd "${ROOT_DIR}/frontend" && pnpm dev) &

wait
