#!/usr/bin/env python3
"""Generate web/kiara-dark.css from the approved design's own stylesheet.

Every rule of kiara-app.html is re-issued under an `html.kdark` scope with a
warm-charcoal remap of the palette, so the dark theme covers 100% of the
design (including screens added later) without touching the original file.

Property-aware mapping:
  background/border/shadow/fill  -> light surfaces become dark surfaces
  color/text-shadow              -> dark ink becomes warm light ink
  gold accents stay gold (they carry the brand on dark too)

Run once per design update:  python3 web/build_dark.py
Output is committed; the Gradle task only injects the <link>.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "web" / "kiara-app.html"
OUT = ROOT / "web" / "kiara-dark.css"

# ---- palette maps -----------------------------------------------------------
SURF = {  # backgrounds / panels / borders (bg-map)
    "#f7f6f3": "#161411", "#faf9f6": "#1d1a16", "#f8f7f2": "#1b1916",
    "#fffdf7": "#232019", "#fffdf8": "#232019", "#fffef9": "#232019",
    "#fcfbf6": "#201d17", "#f5f3ec": "#1b1915", "#f3f0e8": "#211e18",
    "#f0ebdd": "#2b2721", "#e9e2d0": "#302b23", "#efe8d8": "#302b23",
    "#e7e3d9": "#2e2a23", "#e5e2da": "#35312a", "#e0ddd5": "#36322b",
    "#dcd9d1": "#383329", "#d2d0c8": "#3b362d", "#cfcbc2": "#3b362d",
    "#d8d5cd": "#3b362d", "#ffffff": "#232019",
    "#fef7cc": "#3a3117", "#ffe9a8": "#3d3418", "#fff6d8": "#383016",
    "#8a6d1f": "#e7b93c",
}
INK = {  # text / glyphs (fg-map)
    "#1c1a17": "#f0ede5", "#29261f": "#e9e5db", "#2e2a24": "#e9e5db",
    "#363535": "#454037", "#4a453c": "#d8d3c8", "#55503f": "#d0cabb",
    "#75705f": "#a9a395", "#6e675a": "#a9a395", "#70695d": "#a9a395",
    "#9c968b": "#918b7f", "#8b8578": "#9c968b", "#a09a8d": "#b3ac9e",
    "#5c564a": "#c2bcae",
}
GOLD_KEEP = {"#fec60d", "#ffcf35", "#ffd34d", "#ffc24b", "#ff9a1f",
             "#f0a900", "#ffb700", "#fdce23", "#e7a902", "#ff6a00"}

BG_PROP = re.compile(r"^(background|border[^:]*|box-shadow|fill|stroke|outline[^:]*|-webkit-text-decoration)", re.I)
FG_PROP = re.compile(r"^(color|text-shadow|caret-color|-webkit-text-fill)", re.I)


def map_hex(h: str, surf: bool) -> str:
    h6 = h.lower()
    if len(h6) == 4:  # #abc -> #aabbcc
        h6 = "#" + "".join(c * 2 for c in h6[1:])
    if len(h6) != 7:
        return h
    if h6 in GOLD_KEEP:
        return h6 if surf else h6
    table = SURF if surf else {**SURF, **INK}
    return table.get(h6, h6)


def map_rgba(m: re.Match, surf: bool) -> str:
    body = m.group(0)
    vals = [v.strip() for v in body[body.find("(") + 1:body.rfind(")")].split(",")]
    if len(vals) < 3:
        return body
    r, g, b = (int(float(v)) for v in vals[:3])
    a = float(vals[3]) if len(vals) > 3 else 1.0
    key = (r, g, b)
    if key == (255, 255, 255):  # white: glass fills vs. white text
        if surf:
            if a >= 0.85:
                return "rgba(42,38,31,%.2f)" % min(a, 0.95)
            return "rgba(240,237,229,%.2f)" % (a * 0.22)
        return "rgba(240,237,229,%.2f)" % a
    if key == (248, 247, 244):
        return "rgba(26,23,18,%.2f)" % min(a, 0.98)
    if key == (120, 105, 70):  # warm shadow tint -> neutral deep shadow
        return "rgba(0,0,0,%.2f)" % min(a * 1.8, 0.55) if surf else body
    if key == (255, 150, 40):
        return "rgba(255,150,40,%.2f)" % (a * 0.55)
    if key == (255, 183, 0):
        return "rgba(255,183,0,%.2f)" % (a * 0.7)
    return body


HEX = re.compile(r"#[0-9a-fA-F]{3,8}\b")
RGBA = re.compile(r"rgba?\([^)]*\)")


def map_value(val: str, surf: bool) -> str:
    val = RGBA.sub(lambda m: map_rgba(m, surf), val)
    return HEX.sub(lambda m: map_hex(m.group(0), surf), val)


def map_body(body: str) -> str:
    out, buf, depth = [], "", 0
    for ch in body:
        if ch == ";" and depth == 0:
            out.append(buf); buf = ""
        else:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            buf += ch
    out.append(buf)
    parts = []
    for decl in out:
        if ":" not in decl:
            parts.append(decl)
            continue
        prop, val = decl.split(":", 1)
        pname = prop.strip()
        if pname.startswith("--"):  # custom props: decide by name
            if pname in ("--ink", "--mut"):
                parts.append(prop + ":" + map_value(val, False))
            elif pname == "--dark":
                parts.append(prop + ":#454037")
            elif pname in ("--gold", "--gold-d", "--ang"):
                parts.append(decl)
            else:  # --bg, --panel, ... surface colors
                parts.append(prop + ":" + map_value(val, True))
        elif BG_PROP.match(pname):
            parts.append(prop + ":" + map_value(val, True))
        elif FG_PROP.match(pname):
            parts.append(prop + ":" + map_value(val, False))
        else:
            parts.append(decl)
    return ";".join(parts)


def prefix_sel(sel: str) -> str:
    parts = []
    for p in sel.split(","):
        p = p.strip()
        if not p:
            continue
        if p in (":root", "html", "html body"):
            parts.append("html.kdark" if p != "html body" else "html.kdark body")
        elif p == "body":
            parts.append("html.kdark body")
        else:
            parts.append("html.kdark " + p)
    return ", ".join(parts)


def transform(css: str) -> str:
    out, i, n = [], 0, len(css)
    while i < n:
        # leading whitespace/comments
        m = re.match(r"\s+", css[i:])
        if m:
            out.append(m.group(0)); i += m.end(); continue
        if css.startswith("/*", i):
            j = css.find("*/", i)
            j = n if j < 0 else j + 2
            out.append(css[i:j]); i = j; continue
        if css[i] == "@":
            j = css.find("{", i)
            name = css[i:j].strip()
            if name.startswith(("@keyframes", "@font-face", "@property")):
                # already declared by the base page verbatim; fonts/keyframes
                # are theme-independent -> skip entirely (no 800KB font dupe)
                depth, k = 0, j
                while k < n:
                    if css[k] == "{":
                        depth += 1
                    elif css[k] == "}":
                        depth -= 1
                        if depth == 0:
                            break
                    k += 1
                i = k + 1; continue
            # @media / @supports -> recurse
            depth, k = 0, j
            while k < n:
                if css[k] == "{":
                    depth += 1
                elif css[k] == "}":
                    depth -= 1
                    if depth == 0:
                        break
                k += 1
            inner = transform(css[j + 1:k])
            out.append(name + "{" + inner + "}")
            i = k + 1; continue
        # style rule: selector { body }
        j = css.find("{", i)
        if j < 0:
            out.append(css[i:]); break
        sel = css[i:j]
        depth, k = 0, j
        while k < n:
            if css[k] == "{":
                depth += 1
            elif css[k] == "}":
                depth -= 1
                if depth == 0:
                    break
            k += 1
        body = css[j + 1:k]
        sel = sel.strip()
        if sel:
            out.append(prefix_sel(sel) + "{" + map_body(body) + "}")
        else:
            out.append(css[i:k + 1])
        i = k + 1
    return "".join(out)


MANUAL = """
/* ---- manual dark adjustments (glyphs, chrome) ---- */
html.kdark { color-scheme: dark; }
html.kdark, html.kdark body { background: #161411 !important; }
html.kdark img { -webkit-user-drag: none; }
/* mono UI glyphs become light silhouettes (nav icons, row icons, arrows) */
html.kdark .nic .ic, html.kdark .mic, html.kdark .chev {
  filter: brightness(0) invert(.87);
}
/* active tab glyph sits on the gold pill: keep it dark for contrast */
html.kdark .nitem.active .nic .ic { filter: brightness(0); }
html.kdark .nitem.active span { color: var(--gold) !important; }
/* subtle dim on big decorative illustrations so they sit in the dark UI */
html.kdark .stage img { image-rendering: auto; }
/* inline SVG glyphs authored with the light ink get the dark ink */
html.kdark svg[fill="#1C1A17"] { fill: #F0EDE5; }
"""


def main() -> int:
    html = SRC.read_text(encoding="utf-8")
    styles = re.findall(r"<style[^>]*>(.*?)</style>", html, re.S)
    css = "\n".join(styles)
    dark = transform(css) + MANUAL
    OUT.write_text(dark, encoding="utf-8")
    rules = dark.count("{")
    print(f"kiara-dark.css: {len(dark) // 1024} KB, {rules} rules -> {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
