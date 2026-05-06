#!/usr/bin/env bash
set -euo pipefail
cd /root/gzly_scraper
source venv/bin/activate
while true; do
  python3 -u scrape_photos.py \
    --workers "${PHOTO_WORKERS:-3}" \
    --headless true \
    --seed-file "/root/gzly_scraper/data/guizhou_schools.json" \
    --checkpoint-every "${PHOTO_CHECKPOINT_EVERY:-10}" \
    ${PHOTO_PROXY:+--proxy "$PHOTO_PROXY"} || true
  sleep "${PHOTO_RESTART_DELAY_SECONDS:-30}"
done
