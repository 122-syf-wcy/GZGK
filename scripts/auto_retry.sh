#!/bin/bash
cd /root/gzly_scraper
source venv/bin/activate

CHECK_SCRIPT='
import hmac,hashlib,base64,requests
K="D23ABC@#56"
p={"local_province_id":"52","local_type_id":"2073","page":"1","school_id":"935","size":"20","uri":"apidata/api/gk/score/special","year":"2024"}
q="&".join(f"{k}={v}" for k,v in sorted(p.items()))
r=f"api.zjzw.cn/web/api/?{q}"
m=hmac.new(K.encode(),r.encode(),hashlib.sha1).digest()
p["signsafe"]=hashlib.md5(base64.b64encode(m).decode().encode()).hexdigest()
s=requests.Session()
s.headers.update({"User-Agent":"Mozilla/5.0","Referer":"https://www.gaokao.cn/"})
x=s.get("https://api.zjzw.cn/web/api/",params=p,timeout=10)
print(x.json().get("code","?"))
'

while true; do
    CODE=$(python3 -c "$CHECK_SCRIPT" 2>/dev/null)
    echo "[$(date)] API check: code=$CODE"

    if [ "$CODE" = "0000" ]; then
        echo "[$(date)] API unblocked! Starting scraper..."
        python3 -u scrape_major_api.py --workers 1 --delay 1.0 2>&1 | tee major_scrape.log
        echo "[$(date)] Scraper finished."
        break
    fi

    echo "[$(date)] Still blocked. Retrying in 5 minutes..."
    sleep 300
done
