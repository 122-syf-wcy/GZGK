#!/usr/bin/env bash
set -euo pipefail
cd /root/gzly_scraper
source venv/bin/activate
exec python3 -u scrape_official_links.py \
  --workers "${WORKERS:-3}" \
  --delay "${DELAY:-1.2}" \
  --checkpoint-every "${CHECKPOINT_EVERY:-20}" \
  --seed-file "/root/gzly_scraper/data/official_links/university_seeds.json" \
  ${SCRAPER_PROXY_LIST:+--proxy "$SCRAPER_PROXY_LIST"}
