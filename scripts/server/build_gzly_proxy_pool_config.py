#!/usr/bin/env python3
"""Build a sanitized mihomo config for the GZLY crawler proxy pool.

Reads a Clash/mihomo subscription YAML (default: ``./ip_subscription.yaml``)
and emits a minimal config (default: ``./mihomo_gzly/config.yaml``) that:

* keeps **all SS / VMess / Trojan / Hysteria2 proxies** from the source;
* drops the source ``proxy-groups`` and ``rules``;
* adds a single ``GZLY_ROTATE`` proxy-group (``type: load-balance``,
  ``strategy: round-robin``) that contains every kept proxy and routes via a
  ``url-test`` filter against ``http://www.gstatic.com/generate_204``;
* sets ``mixed-port: <PORT>`` (default 7891) so it does NOT conflict with the
  primary mihomo instance running on 7890;
* disables ``tun`` and any global ``external-controller`` (we don't want this
  instance taking over the system DNS / TUN device);
* enables a ``external-controller-unix`` socket so the script can switch /
  inspect proxies programmatically (default
  ``/tmp/gzly-mihomo/external.sock``);
* sends all traffic through ``GZLY_ROTATE`` (catch-all rule).

Names that are **obviously not real nodes** (流量 / 套餐 / 官网 banners) are
filtered out so they don't poison the load-balance pool.

Usage::

  python3 server/build_gzly_proxy_pool_config.py \\
      --input  /root/gzly_scraper/ip_subscription.yaml \\
      --output /root/gzly_scraper/mihomo_gzly/config.yaml \\
      --mixed-port 7891
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Iterable

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover
    print("PyYAML is required: pip install pyyaml", file=sys.stderr)
    raise


BANNER_KEYWORDS = (
    "剩余流量",
    "套餐到期",
    "官网",
    "网站",
    "公告",
    "更新",
    "重置",
    "联系",
    "支持",
    "https://",
    "http://",
)

REAL_PROXY_TYPES = {"ss", "ssr", "vmess", "vless", "trojan", "hysteria", "hysteria2", "tuic"}


def looks_like_banner(name: str) -> bool:
    if not name:
        return True
    for kw in BANNER_KEYWORDS:
        if kw in name:
            return True
    if re.fullmatch(r"[\sA-Za-z0-9._-]+", name) and len(name) <= 3:
        return True
    return False


def collect_proxies(source: dict) -> list[dict]:
    proxies = source.get("proxies") or []
    if not isinstance(proxies, list):
        raise ValueError("source['proxies'] is not a list")
    kept: list[dict] = []
    seen: set[tuple] = set()
    for proxy in proxies:
        if not isinstance(proxy, dict):
            continue
        name = str(proxy.get("name") or "").strip()
        ptype = str(proxy.get("type") or "").strip().lower()
        server = str(proxy.get("server") or "").strip()
        port = proxy.get("port")
        if not name or not ptype or not server or port is None:
            continue
        if ptype not in REAL_PROXY_TYPES:
            continue
        if looks_like_banner(name):
            continue
        key = (server, str(port), proxy.get("password"), proxy.get("uuid"))
        if key in seen:
            continue
        seen.add(key)
        kept.append(proxy)
    return kept


def build_config(proxies: Iterable[dict], *, mixed_port: int, ctl_socket: str) -> dict:
    proxy_list = list(proxies)
    proxy_names = [p["name"] for p in proxy_list]
    if not proxy_names:
        raise ValueError("no usable proxies after filtering")
    return {
        "mode": "rule",
        "mixed-port": mixed_port,
        "allow-lan": False,
        "log-level": "warning",
        "ipv6": False,
        "external-controller": "",
        "external-controller-unix": ctl_socket,
        "tun": {"enable": False},
        "profile": {"store-selected": False},
        "dns": {
            "enable": True,
            "listen": "",
            "ipv6": False,
            "enhanced-mode": "redir-host",
            "default-nameserver": ["223.5.5.5", "119.29.29.29"],
            "nameserver": [
                "https://dns.alidns.com/dns-query",
                "https://doh.pub/dns-query",
            ],
            "fallback": [
                "https://1.1.1.1/dns-query",
                "https://dns.google/dns-query",
            ],
            "fallback-filter": {"geoip": True, "geoip-code": "CN"},
        },
        "proxies": proxy_list,
        "proxy-groups": [
            {
                "name": "GZLY_ROTATE",
                "type": "load-balance",
                "strategy": "round-robin",
                "url": "http://www.gstatic.com/generate_204",
                "interval": 600,
                "tolerance": 200,
                "lazy": False,
                "proxies": proxy_names,
            },
            {
                "name": "GZLY_AUTO",
                "type": "url-test",
                "url": "http://www.gstatic.com/generate_204",
                "interval": 600,
                "tolerance": 200,
                "lazy": False,
                "proxies": proxy_names,
            },
        ],
        "rules": [
            "MATCH,GZLY_ROTATE",
        ],
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True,
                        help="source clash subscription YAML")
    parser.add_argument("--output", type=Path, required=True,
                        help="destination mihomo config.yaml path")
    parser.add_argument("--mixed-port", type=int, default=7891)
    parser.add_argument("--ctl-socket", default="/tmp/gzly-mihomo/external.sock")
    parser.add_argument("--print-summary", action="store_true")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    raw = args.input.read_text(encoding="utf-8")
    source = yaml.safe_load(raw)
    proxies = collect_proxies(source)
    cfg = build_config(proxies, mixed_port=args.mixed_port, ctl_socket=args.ctl_socket)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as fh:
        yaml.safe_dump(cfg, fh, allow_unicode=True, sort_keys=False)
    print(f"wrote {args.output} mixed_port={args.mixed_port} proxies={len(proxies)}")
    if args.print_summary:
        types: dict[str, int] = {}
        for p in proxies:
            t = str(p.get("type"))
            types[t] = types.get(t, 0) + 1
        for t, n in sorted(types.items()):
            print(f"  {t}: {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
