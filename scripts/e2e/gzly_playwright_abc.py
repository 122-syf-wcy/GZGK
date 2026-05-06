"""GZLY 生产 E2E 烟雾脚本（流程 A/B/C + R1 绑定入口）。

使用方式（服务器 / 本地均可，需要能访问生产域名）：

    # 1. 准备环境变量
    export CARD=XXXX-XXXX-XXXX-XXXX     # 已有方案的测试卡密
    export PLAN=187                     # 该卡密下最新方案 id
    export SECRET=8213                  # 激活时设置的安全码
    export GZLY_BASE=https://gzly.dongsiwei.com  # 可选，默认值已设

    # 2. 运行
    source /root/gzly_scraper/venv/bin/activate
    python scripts/e2e/gzly_playwright_abc.py

执行完成后会打印 JSON 结果，并将截图落到 /tmp/gzly-playwright-abc.png。
所有断言按「UI 入口可见 + 状态转移正确」判定，避免 accessible-name 的误伤。
"""

import asyncio
import json
import os
import sys

from playwright.async_api import async_playwright

BASE = os.environ.get("GZLY_BASE", "https://gzly.dongsiwei.com")
SECRET = os.environ.get("SECRET", "")
CARD = os.environ.get("CARD", "")
PLAN = os.environ.get("PLAN", "")
OUT = os.environ.get("OUT", "/tmp/gzly-playwright-abc.png")


async def flow_c_unauth_redirect(browser):
    """流程 C：未登录访问 /me/plans 预期被重定向到 /auth/login。"""
    ctx = await browser.new_context(ignore_https_errors=True, viewport={"width": 390, "height": 844})
    page = await ctx.new_page()
    await page.goto(f"{BASE}/me/plans", wait_until="networkidle", timeout=60000)
    url = page.url
    is_login = "/auth/login" in url or "/auth/activate" in url
    await ctx.close()
    return {"ok": is_login, "finalUrl": url}


async def flow_b_cross_device_login(browser):
    """流程 B：全新浏览器上下文模拟跨设备 → 登录 → 我的空间 → 点击历史进结果页。"""
    ctx = await browser.new_context(ignore_https_errors=True, viewport={"width": 390, "height": 844})
    page = await ctx.new_page()
    await page.goto(f"{BASE}/auth/login?redirect=/me/plans", wait_until="networkidle", timeout=60000)
    await page.locator("input").nth(0).fill(CARD)
    await page.locator("input").nth(1).fill(SECRET)
    await page.locator("button.auth-submit").click()
    await page.wait_for_url("**/me/plans", timeout=60000)
    await page.wait_for_selector("text=我的志愿空间", timeout=30000)
    await page.wait_for_selector("article.plan-card", timeout=30000)
    card_count = await page.locator("article.plan-card").count()
    await page.locator("article.plan-card").first.click()
    await page.wait_for_url("**/volunteer/result?planId=*", timeout=60000)
    await page.wait_for_load_state("networkidle", timeout=60000)
    # 等待 VolunteerResult 关键区块（算法解读卡）出现，避免 SPA 还没 mount 完就截图
    await page.locator(".algorithm-card, .algorithm-metrics").first.wait_for(state="visible", timeout=30000)
    # 滚动到"主列表招生类型分布"区，使其进入截图视野
    breakdown = page.locator(".algorithm-recruit-breakdown")
    if await breakdown.count():
        await breakdown.first.scroll_into_view_if_needed()
    url = page.url
    body = await page.locator("body").inner_text(timeout=30000)
    restored = ("96" in body) or ("志愿" in body and "方案" in body)
    has_breakdown = await breakdown.count() > 0
    await page.screenshot(path=OUT, full_page=True)
    await ctx.close()
    return {
        "ok": (f"planId={PLAN}" in url) and restored,
        "finalUrl": url.replace(BASE, ""),
        "planCardCount": card_count,
        "hasRecruitTypeBreakdown": has_breakdown,
    }


async def flow_r1_claim_dialog(browser):
    """流程 R1：登录后通过 CSS 选择器点击"绑定旧方案"按钮 → 对话框打开 → 关闭按钮生效。"""
    ctx = await browser.new_context(ignore_https_errors=True, viewport={"width": 390, "height": 844})
    page = await ctx.new_page()
    await page.goto(f"{BASE}/auth/login?redirect=/me/plans", wait_until="networkidle", timeout=60000)
    await page.locator("input").nth(0).fill(CARD)
    await page.locator("input").nth(1).fill(SECRET)
    await page.locator("button.auth-submit").click()
    await page.wait_for_url("**/me/plans", timeout=60000)
    # 通过文案匹配，避开 lucide svg 图标造成 accessible-name 干扰；
    # 移动端 390x844 下可能滚动才能见到"绑定旧方案"按钮，故强制 scroll_into_view。
    claim_btn = page.locator('button:has-text("绑定旧方案")').first
    await claim_btn.scroll_into_view_if_needed(timeout=30000)
    await claim_btn.click()
    dialog_head = page.locator(".claim-dialog__head h3:has-text('绑定旧方案到本账号')")
    await dialog_head.wait_for(state="visible", timeout=10000)
    close_btn = page.locator(".claim-dialog__close")
    await close_btn.click()
    try:
        await page.locator(".claim-dialog").wait_for(state="detached", timeout=5000)
        dialog_closed = True
    except Exception:
        dialog_closed = not await page.locator(".claim-dialog").is_visible()
    await ctx.close()
    return {"ok": dialog_closed, "dialogOpened": True, "dialogClosed": dialog_closed}


async def main():
    if not CARD or not PLAN or not SECRET:
        print(json.dumps({"ok": False, "step": "prepare", "message": "CARD/PLAN/SECRET 环境变量缺失"}, ensure_ascii=False))
        return 1
    results = {}
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True, args=["--no-sandbox"])
        try:
            results["flowC_unauthRedirect"] = await flow_c_unauth_redirect(browser)
            results["flowB_crossDevice"] = await flow_b_cross_device_login(browser)
            results["flowR1_claimDialog"] = await flow_r1_claim_dialog(browser)
        finally:
            await browser.close()
    all_ok = all(v.get("ok") for v in results.values())
    print(json.dumps({"ok": all_ok, "results": results, "screenshot": OUT}, ensure_ascii=False))
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
