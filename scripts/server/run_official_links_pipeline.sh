#!/usr/bin/env bash
set -euo pipefail

WORK_DIR="/root/gzly_scraper"
PROXY_LIST="${1:-${SCRAPER_PROXY_LIST:-http://127.0.0.1:7890}}"
WORKERS="${WORKERS:-3}"
DELAY="${DELAY:-1.2}"
CHECKPOINT_EVERY="${CHECKPOINT_EVERY:-20}"
RUN_IMPORTER="${RUN_IMPORTER:-1}"

cd "$WORK_DIR"
source venv/bin/activate

echo "Stopping old official links jobs ..."
ps -ef | grep '[s]crape_official_links.py' | awk '{print $2}' | xargs -r kill -9 || true
ps -ef | grep '[i]mport_official_links.sh' | awk '{print $2}' | xargs -r kill -9 || true

echo "Starting scraper ..."
nohup python3 -u scrape_official_links.py \
  --workers "$WORKERS" \
  --delay "$DELAY" \
  --checkpoint-every "$CHECKPOINT_EVERY" \
  --proxy "$PROXY_LIST" \
  > data/official_links/official_links.log 2>&1 < /dev/null &
echo "SCRAPER_PID:$!"

if [[ "$RUN_IMPORTER" == "1" ]]; then
  echo "Starting importer ..."
  nohup bash server/import_official_links.sh \
    > data/official_links/official_links_import.log 2>&1 < /dev/null &
  echo "IMPORTER_PID:$!"
else
  echo "Importer disabled on this host."
fi
