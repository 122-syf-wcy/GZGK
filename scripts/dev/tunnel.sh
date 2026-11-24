#!/usr/bin/env bash
# Establishes a secure SSH tunnel from local Mac to the remote GZGK v2 infra server.
# Middleware ports stay bound to 127.0.0.1 on the server and are never exposed publicly.
#
# Usage:
#   REMOTE_HOST=my-server bash scripts/dev/tunnel.sh
#   REMOTE_HOST=root@1.2.3.4 SSH_PORT=22 bash scripts/dev/tunnel.sh

set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:?Set REMOTE_HOST (SSH alias or user@ip)}"
SSH_PORT="${SSH_PORT:-22}"

echo "[tunnel] Forwarding PG(5432), Redis(6379), Kafka(9092), Nacos(8848), Grafana(3000), Prom(9095) -> ${REMOTE_HOST}:${SSH_PORT}"
echo "[tunnel] Press Ctrl+C to close."

exec ssh -N \
  -p "${SSH_PORT}" \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -o ExitOnForwardFailure=yes \
  -L 127.0.0.1:5432:127.0.0.1:5432 \
  -L 127.0.0.1:6379:127.0.0.1:6379 \
  -L 127.0.0.1:9092:127.0.0.1:9092 \
  -L 127.0.0.1:8848:127.0.0.1:8848 \
  -L 127.0.0.1:3000:127.0.0.1:3000 \
  -L 127.0.0.1:9095:127.0.0.1:9095 \
  "${REMOTE_HOST}"
