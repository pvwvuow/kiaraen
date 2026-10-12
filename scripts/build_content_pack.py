#!/usr/bin/env python3
"""
build_content_pack.py — single source of truth pipeline.

Reads the Kiara word bank (content/wordbank/*.tsv, 5000 words, 6 levels,
~101 categories, 16 collections) and emits a compact, versioned content pack:

    content-pack.json   →  committed to git, bundled in the APK (assets),
                           and uploaded to Supabase Storage by load_content_supabase.py

Pack format (compact arrays keep the APK asset small):
{
  "v": 1,
  "generated_at": "...",
  "lvls":  [{"id":"A1","t":"مبتدی","s":1}, ...],
  "cats":  [{"lv":"A1","k":"conv","t":"مکالمه روزمره","i":"chat","s":5}, ...],
  "colls": [{"s":"daily","t":"مکالمه روزمره","d":"..."}, ...],
  "words": [["hello","int","سلام",<catIndex>,[<collIdx>,...]], ...]
}

Run:  python3 scripts/build_content_pack.py
"""
import json
import re
import sys
import glob
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BANK = ROOT / "content" / "wordbank"
OUT = ROOT / "content-pack.json"

LVLS = ["A1", "A2", "B1", "B2", "C1", "C2"]
LEVEL_TITLES = {
    "A1": "مبتدی (A1)", "A2": "پایه (A2)", "B1": "متوسط (B1)",
    "B2": "متوسط رو به بالا (B2)", "C1": "پیشرفته (C1)", "C2": "تسلط (C2)",
}
COLL_NAMES = [
    "فیلم و موسیقی", "کار و کسب‌وکار", "غذا و نوشیدنی", "سفر",
    "مکالمه روزمره", "خانه و خانواده", "خرید", "سلامت", "آموزش",
    "تکنولوژی", "طبیعت و محیط‌زیست", "ورزش", "آب‌وهوا", "احساسات و روابط",
    "علم", "پول و اقتصاد",
]
COLL_SLUGS = [
    "movies-music", "business", "food-drink", "travel", "daily-talk",
    "home-family", "shopping", "health", "education", "technology",
    "nature-env", "sports", "weather", "feelings-relations", "science",
    "money-economy",
]


def parse_level(lv: str):
    files = sorted(glob.glob(str(BANK / f"{lv.lower()}-part*.tsv")))
    if not files:
        sys.exit(f"!! no tsv files for level {lv}")

    cats, words = [], []      # cats: [key,title,icon,colstr]; words: [w,pos,fa,catIdx,mask]
    seen = {}
    cur = None
    for f in files:
        for lineno, raw in enumerate(open(f, encoding="utf-8"), 1):
            line = raw.strip()
            if not line or line.startswith("//"):
                continue
            if line.startswith("#"):
                parts = line[1:].split("|")
                key = parts[0].strip()
                title = parts[1].strip() if len(parts) > 1 else key
                icon = parts[2].strip() if len(parts) > 2 else "chat"
                colstr = parts[3].strip() if len(parts) > 3 else ""
                cur = next((i for i, c in enumerate(cats) if c[0] == key), None)
                if cur is None:
                    cats.append([key, title, icon, colstr])
                    cur = len(cats) - 1
                continue

            bits = line.split("|")
            if len(bits) < 3:
                sys.exit(f"!! {lv} {f}:{lineno} bad line: {line}")
            w, pos, fa = bits[0].strip(), bits[1].strip(), bits[2].strip()
            key = (w.lower(), pos)
            if key in seen:
                sys.exit(f"!! dup in {lv}: {w}|{pos}")
            seen[key] = True

            mask = 0
            if cats[cur][3]:
                for c in cats[cur][3].split(","):
                    c = c.strip()
                    if c.isdigit():
                        ci = int(c)
                        if not (1 <= ci <= 16):
                            sys.exit(f"!! bad collection index {ci}")
                        mask |= 1 << (ci - 1)
            words.append([w, pos, fa, cur, mask])
    return cats, words


def main():
    pack = {
        "v": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "lvls": [{"id": lv, "t": LEVEL_TITLES[lv], "s": i + 1} for i, lv in enumerate(LVLS)],
        "cats": [],
        "colls": [
            {"s": COLL_SLUGS[i], "t": COLL_NAMES[i]} for i in range(len(COLL_NAMES))
        ],
        "words": [],
    }

    total = 0
    per_level = {}
    seen_global = {}          # (word,pos) → level; lowest level wins (a word lives in ONE level)
    dropped = []
    for lv in LVLS:
        cats, words = parse_level(lv)
        base = len(pack["cats"])
        for key, title, icon, _colstr in cats:
            pack["cats"].append({"lv": lv, "k": key, "t": title, "i": icon})
        lvl_words = 0
        for w, pos, fa, cat_idx, mask in words:
            k = (w.lower(), pos)
            if k in seen_global:
                dropped.append(f"{w}|{pos} ({seen_global[k]} kept, {lv} dropped)")
                continue
            seen_global[k] = lv
            cols = [b + 1 for b in range(16) if mask & (1 << b)]
            pack["words"].append([w, pos, fa, base + cat_idx, cols])
            lvl_words += 1
        per_level[lv] = lvl_words
        total += lvl_words
        print(f"{lv}: {lvl_words:5d} words, {len(cats):3d} categories")

    if dropped:
        print(f"\ndropped {len(dropped)} cross-level repeats (first occurrence kept):")
        for d in dropped:
            print(f"   - {d}")

    OUT.write_text(json.dumps(pack, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    kb = OUT.stat().st_size / 1024
    print(f"\nTOTAL: {total} words, {len(pack['cats'])} categories, {len(pack['colls'])} collections")
    print(f"written: {OUT} ({kb:.0f} KB)")


if __name__ == "__main__":
    main()
