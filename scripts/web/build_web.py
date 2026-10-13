#!/usr/bin/env python3
"""Build the kiara web UI exactly like the Gradle task `prepareKiaraWeb` does.

Usage: python3 scripts/web/build_web.py [out_dir]   (default: build/kiara-web)
Open <out_dir>/web/index.html in Chrome (phone size 390x843) to preview the app.
"""
import base64
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WEB = ROOT / "web"
TYPES = {
    "image/png": "png", "image/jpeg": "jpg", "image/jpg": "jpg", "image/webp": "webp",
    "image/gif": "gif", "image/svg+xml": "svg", "font/woff2": "woff2", "font/woff": "woff",
    "font/ttf": "ttf", "font/otf": "otf", "application/font-woff2": "woff2",
    "application/x-font-ttf": "ttf",
}
URI = re.compile(r"data:([a-zA-Z0-9.+/-]+);base64,([A-Za-z0-9+/=]+)")
SHELL = ("kiara-shell.css", "kiara-dark.css", "kiara-fit.js", "kiara-auth.js", "kiara-live.js")


def supabase_cfg() -> dict:
    """Publishable values for window.KIARA_CFG (env > local.properties)."""
    import os
    url = os.environ.get("KIARA_URL", "").strip()
    anon = os.environ.get("KIARA_ANON", "").strip()
    lp = ROOT / "local.properties"
    if lp.is_file():
        for line in lp.read_text(encoding="utf-8").splitlines():
            if "=" not in line:
                continue
            k, v = line.split("=", 1)
            k, v = k.strip(), v.strip()
            if k == "kiara.supabase.url" and not url:
                url = v
            elif k == "kiara.supabase.anonKey" and not anon:
                anon = v
    return {"url": url, "anon": anon}


def main() -> int:
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build" / "kiara-web"
    page = WEB / "kiara-app.html"
    if not page.is_file():
        print("web/kiara-app.html not found")
        return 1
    dest = out / "web"
    (dest / "m").mkdir(parents=True, exist_ok=True)
    html = page.read_text(encoding="utf-8").replace("\r\n", "\n")
    for p in json.loads((WEB / "patches.json").read_text(encoding="utf-8")):
        at = html.find(p["find"])
        if at < 0:
            print(f"warning: hook '{p['name']}' not found (skipped)")
            continue
        html = html[:at] + p["replace"] + html[at + len(p["find"]):]
    names: dict[str, str] = {}

    def unpack(m: re.Match) -> str:
        ext = TYPES.get(m.group(1).lower())
        if not ext:
            return m.group(0)
        b64 = m.group(2)
        if b64 not in names:
            data = base64.b64decode(b64)
            name = f"m/{hashlib.sha1(data).hexdigest()[:16]}.{ext}"
            (dest / name).write_bytes(data)
            names[b64] = name
        return names[b64]

    html = URI.sub(unpack, html)
    cfg = supabase_cfg()
    esc = lambda s: s.replace("\\", "\\\\").replace('"', '\\"')
    cfg_js = ("window.KIARA_CFG={url:\"%s\",anon:\"%s\"};" % (esc(cfg["url"]), esc(cfg["anon"]))
              if cfg["url"] and cfg["anon"] else "")
    head = ("<script>" + cfg_js + "</script>\n"
            "<script>try{var d=localStorage.getItem('kiara.dark');"
            "if(d!=='0'&&d!=='1')d=matchMedia('(prefers-color-scheme: dark)').matches?'1':'0';"
            "if(d==='1')document.documentElement.classList.add('kdark')}catch(e){}</script>\n"
            '<link rel="stylesheet" href="kiara-shell.css">\n'
            '<link rel="stylesheet" href="kiara-dark.css">\n'
            '<script src="kiara-fit.js"></script>\n')
    html = html.replace("</head>", head + "</head>", 1)
    tail = '<script src="kiara-auth.js"></script>\n<script src="kiara-live.js"></script>\n'
    end = html.rfind("</body>")
    html = html[:end] + tail + html[end:] if end >= 0 else html + tail
    (dest / "index.html").write_text(html, encoding="utf-8")
    for n in SHELL:
        if (WEB / n).is_file():
            (dest / n).write_bytes((WEB / n).read_bytes())
    print(f"{len(html) // 1024} KB page, {len(names)} media files -> {dest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
