#!/bin/bash
cd "$(dirname "$0")"

echo "[$(date)] Waiting for API unblock..."

while true; do
    CODE=$(python3 -c "
import requests
r=requests.get('https://api.zjzw.cn/web/api/',params={'local_province_id':'52','local_type_id':'2073','page':'1','school_id':'935','size':'1','uri':'apidata/api/gk/score/special','year':'2024'},timeout=10,headers={'User-Agent':'Mozilla/5.0','Referer':'https://www.gaokao.cn/'})
print(r.json().get('code','?'))
" 2>/dev/null)

    echo "[$(date)] API check: code=$CODE"

    if [ "$CODE" = "0000" ]; then
        echo "[$(date)] API unblocked! Starting scraper..."
        python3 -u scrape_major_api.py --workers 1 --delay 3.0 2>&1 | tee data/export/major_scrape_$(date +%Y%m%d_%H%M).log
        echo "[$(date)] Scraper finished."
        break
    fi

    echo "[$(date)] Still blocked. Retrying in 3 minutes..."
    sleep 180
done
