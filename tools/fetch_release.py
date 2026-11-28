#!/usr/bin/env python3
"""Download built jars from a GitHub Release into dist/.

Usage:
    python3 tools/fetch_release.py            # latest release
    python3 tools/fetch_release.py v1.2.4     # specific tag

Token is optional for public repos; if present it is read from
GITHUB_TOKEN or ../_private/tokens/githubtoken.txt (machine-local, not committed).
"""
from __future__ import annotations

import json
import os
import sys
import urllib.request
from pathlib import Path

REPO = "furina1013-love/ae2_split_cpu"
ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"


def token() -> str | None:
    value = os.environ.get("GITHUB_TOKEN")
    if value:
        return value.strip()
    candidate = ROOT.parent / "_private" / "tokens" / "githubtoken.txt"
    if candidate.is_file():
        return candidate.read_text(encoding="utf-8").strip()
    return None


def request_json(url: str, headers: dict[str, str]) -> dict:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def main() -> int:
    tag = sys.argv[1] if len(sys.argv) > 1 else None
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "ae2-split-cpu-fetch"}
    tok = token()
    if tok:
        headers["Authorization"] = f"Bearer {tok}"

    base = f"https://api.github.com/repos/{REPO}/releases"
    release = request_json(f"{base}/tags/{tag}" if tag else f"{base}/latest", headers)
    if release.get("message"):
        print(f"GitHub API error: {release['message']}", file=sys.stderr)
        return 1

    tag_name = release["tag_name"]
    target = DIST / tag_name
    target.mkdir(parents=True, exist_ok=True)

    assets = release.get("assets") or []
    if not assets:
        print(f"No assets on release {tag_name}", file=sys.stderr)
        return 1

    for asset in assets:
        name = asset["name"]
        if not name.endswith(".jar"):
            continue
        out = target / name
        req = urllib.request.Request(asset["browser_download_url"], headers=headers)
        with urllib.request.urlopen(req, timeout=180) as resp, out.open("wb") as fh:
            fh.write(resp.read())
        print(f"{out}  ({out.stat().st_size} bytes)")

    latest = DIST / "latest"
    latest.mkdir(parents=True, exist_ok=True)
    # latest 只保留最新一版，避免新旧 jar 混在一起。
    for old in latest.glob("*.jar"):
        old.unlink()
    for jar in target.glob("*.jar"):
        dest = latest / jar.name
        dest.write_bytes(jar.read_bytes())
    print(f"Saved to {target} (mirrored in {latest})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
