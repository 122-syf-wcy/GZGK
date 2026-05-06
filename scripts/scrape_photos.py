#!/usr/bin/env python3
"""
Scrape campus photo URLs from gaokao.cn school pages.
Uses Playwright to load pages and extract image URLs.

Output: data/universities/school_photos.json
Format: { "school_id": ["url1", "url2", ...], ... }

Usage:
    python scrape_photos.py --limit 50
    python scrape_photos.py --school 935
    python scrape_photos.py --workers 4
    python scrape_photos.py --proxy http://127.0.0.1:7890
"""

import argparse, asyncio, json, os, re, subprocess, sys
from playwright.async_api import async_playwright

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
UNI_DIR = os.path.join(DATA_DIR, "universities")
os.makedirs(UNI_DIR, exist_ok=True)

OUTPUT_FILE = os.path.join(UNI_DIR, "school_photos.json")
CDN_PATTERN = re.compile(r'https?://static-data\.gaokao\.cn/upload/school/\d+/\d+_\d+_thumb\.\w+')
VIDEO_PATTERN = re.compile(r'https?://static-data\.gaokao\.cn//upload/svideo/\d+_\d+_thumb\.\w+')
DEFAULT_MAX_PHOTOS_PER_SCHOOL = 12

lock = asyncio.Lock()
all_photos: dict[str, list[str]] = {}
completed = 0
total = 0
checkpoint_every = 10
max_photos_per_school = DEFAULT_MAX_PHOTOS_PER_SCHOOL


def sync_output_to_remote():
    host = os.getenv("PHOTO_SYNC_HOST", "").strip()
    user = os.getenv("PHOTO_SYNC_USER", "root").strip()
    port = os.getenv("PHOTO_SYNC_PORT", "22").strip()
    key = os.getenv("PHOTO_SYNC_KEY", "").strip()
    remote_path = os.getenv("PHOTO_SYNC_REMOTE_PATH", "").strip()
    if not host or not remote_path or not os.path.exists(OUTPUT_FILE):
        return
    strict_host_key_checking = os.getenv("PHOTO_SYNC_STRICT_HOST_KEY_CHECKING", "accept-new").strip() or "accept-new"
    cmd = ["scp", "-q", "-o", f"StrictHostKeyChecking={strict_host_key_checking}", "-P", port]
    if key:
        cmd.extend(["-i", key])
    target = f"{user}@{host}:{remote_path}"
    try:
        subprocess.run(cmd + [OUTPUT_FILE, target], check=True, timeout=60)
    except Exception as exc:
        print(f"[sync] failed to upload school_photos.json: {exc}", flush=True)


def normalize_photo_list(urls: list[str], max_count: int) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for url in urls:
        normalized = str(url or "").strip()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        result.append(normalized)
        if len(result) >= max_count:
            break
    return result


async def scrape_school_photos(page, sid: str) -> list[str]:
    async def collect_once(wait_ms: int) -> list[str]:
        try:
            await page.goto(
                f'https://www.gaokao.cn/school/{sid}',
                wait_until='domcontentloaded',
                timeout=12000,
            )
            await page.wait_for_timeout(wait_ms)
        except Exception:
            return []

        img_urls = await page.evaluate("""() => {
            const result = new Set();
            document.querySelectorAll('img').forEach(img => {
                const src = img.src || img.getAttribute('data-src') || '';
                if (src.includes('upload/school/') && src.includes('_thumb.')) result.add(src);
                if (src.includes('upload/svideo/') && src.includes('_thumb.')) result.add(src);
            });
            return [...result];
        }""")

        html = await page.content()
        html_urls = CDN_PATTERN.findall(html) + VIDEO_PATTERN.findall(html)
        return normalize_photo_list(list(img_urls) + html_urls, max_photos_per_school)

    photos = await collect_once(2500)
    if not photos:
        photos = await collect_once(5000)
    return photos


async def worker(wid: int, schools: list, browser):
    global completed, all_photos
    ctx = await browser.new_context(
        user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
        viewport={'width': 1280, 'height': 900}, locale='zh-CN')
    page = await ctx.new_page()

    for school in schools:
        sid = str(school.get('school_id', ''))
        sname = school.get('name', sid)
        photos = normalize_photo_list(await scrape_school_photos(page, sid), max_photos_per_school)

        async with lock:
            if photos:
                all_photos[sid] = photos
            completed += 1
            n = completed

        print(f"  W{wid} [{n}/{total}] {sname}: {len(photos)} photos", flush=True)

        if checkpoint_every and n % checkpoint_every == 0:
            async with lock:
                with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
                    json.dump(all_photos, f, ensure_ascii=False, indent=2)
                print(f"  [checkpoint] {len(all_photos)} schools saved", flush=True)
            sync_output_to_remote()

        await asyncio.sleep(0.8)

    await ctx.close()


async def main():
    global total, all_photos, checkpoint_every, max_photos_per_school

    ap = argparse.ArgumentParser()
    ap.add_argument('--workers', type=int, default=4)
    ap.add_argument('--school', default='')
    ap.add_argument('--limit', type=int, default=0)
    ap.add_argument('--resume', type=int, default=0)
    ap.add_argument('--headless', default='true')
    ap.add_argument('--proxy', default='')
    ap.add_argument('--seed-file', default='')
    ap.add_argument('--checkpoint-every', type=int, default=10)
    ap.add_argument('--max-photos-per-school', type=int, default=DEFAULT_MAX_PHOTOS_PER_SCHOOL)
    args = ap.parse_args()
    headless = args.headless.lower() != 'false'
    max_photos_per_school = max(args.max_photos_per_school, 1)

    if args.school:
        schools = [{'school_id': args.school, 'name': f'school_{args.school}'}]
    else:
        cache = args.seed_file or os.path.join(DATA_DIR, 'guizhou_schools.json')
        if not os.path.exists(cache):
            print(f'Error: {cache} not found')
            sys.exit(1)
        with open(cache) as f:
            schools = json.load(f)

    if os.path.exists(OUTPUT_FILE):
        with open(OUTPUT_FILE) as f:
            raw_photos = json.load(f)
        all_photos = {
            str(sid): normalize_photo_list(urls if isinstance(urls, list) else [], max_photos_per_school)
            for sid, urls in raw_photos.items()
        }
        print(f"Loaded checkpoint: {len(all_photos)} schools")

    done = set(all_photos.keys())
    schools = [s for s in schools if str(s.get('school_id', '')) not in done]

    if args.resume > 0:
        schools = schools[args.resume:]
    if args.limit > 0:
        schools = schools[:args.limit]

    total = len(schools)
    w = min(args.workers, total)
    checkpoint_every = max(args.checkpoint_every, 1)

    print(
        f"Scraping campus photos: {total} schools, {w} workers, "
        f"checkpoint_every={checkpoint_every}, max_photos_per_school={max_photos_per_school}"
    )

    async with async_playwright() as pw:
        launch_kwargs = {'headless': headless}
        if args.proxy:
            launch_kwargs['proxy'] = {'server': args.proxy}
        browser = await pw.chromium.launch(**launch_kwargs)
        chunks = [[] for _ in range(w)]
        for i, s in enumerate(schools):
            chunks[i % w].append(s)

        await asyncio.gather(*[worker(i, chunks[i], browser) for i in range(w)])
        await browser.close()

    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(all_photos, f, ensure_ascii=False, indent=2)
    sync_output_to_remote()

    has_photos = sum(1 for v in all_photos.values() if v)
    total_photos = sum(len(v) for v in all_photos.values())
    print(f"\nDone! {has_photos} schools with photos, {total_photos} total URLs")
    print(f"Saved to {OUTPUT_FILE}")


if __name__ == '__main__':
    asyncio.run(main())
