#!/usr/bin/env python3
"""
load_content_supabase.py — load content-pack.json into a Supabase project.

NEVER run this with keys written in code. Required environment variables:

    SUPABASE_URL          e.g. https://abcdefgh.supabase.co
    SUPABASE_SERVICE_KEY  service_role key (from Supabase dashboard → API)

What it does (idempotent — safe to re-run):
  1. uploads content-pack.json to Storage bucket 'content-packs'
  2. upserts levels → categories → collections → words → meanings → links
  3. registers the pack in public.content_packs and marks it active

The service key bypasses RLS and must NEVER ship inside the Android app.
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "content-pack.json"
BATCH = 400


def rest(url, key, path, method="GET", payload=None, headers=None, raw=False):
    data = None
    hdrs = {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }
    if payload is not None:
        data = payload if isinstance(payload, bytes) else json.dumps(payload).encode("utf-8")
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(f"{url}{path}", data=data, headers=hdrs, method=method)
    last = None
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                body = r.read()
                return (body if raw else (json.loads(body) if body else None)), r.status
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")
            if e.code in (429, 500, 502, 503, 504):
                last = f"{e.code} {body[:200]}"
                time.sleep(1.5 * (attempt + 1))
                continue
            sys.exit(f"!! {method} {path} → {e.code}: {body[:500]}")
        except urllib.error.URLError as e:
            last = str(e)
            time.sleep(1.5 * (attempt + 1))
    sys.exit(f"!! {method} {path} failed after retries: {last}")


def upsert(url, key, table, rows, on_conflict):
    total = 0
    for i in range(0, len(rows), BATCH):
        chunk = rows[i:i + BATCH]
        rest(url, key, f"/rest/v1/{table}?on_conflict={on_conflict}",
             method="POST", payload=chunk,
             headers={"Prefer": "resolution=merge-duplicates,return=minimal"})
        total += len(chunk)
        print(f"   {table}: {total}/{len(rows)}")
    return total


def main():
    url = os.environ.get("SUPABASE_URL")
    key = os.environ.get("SUPABASE_SERVICE_KEY")
    if not url or not key:
        sys.exit("!! set SUPABASE_URL and SUPABASE_SERVICE_KEY environment variables")
    if "service_role" not in key and len(key) < 200:
        print("!! warning: key does not look like a service_role key — aborting")
        sys.exit(1)

    pack = json.loads(PACK.read_text(encoding="utf-8"))
    version = pack["v"]
    print(f"content pack v{version}: {len(pack['words'])} words, "
          f"{len(pack['cats'])} categories, {len(pack['colls'])} collections")

    # 1 ── storage upload (x-upsert true → re-runs overwrite the object)
    obj_path = f"packs/content-v{version}.json"
    rest(url, key, f"/storage/v1/object/content-packs/{obj_path}",
         method="POST", payload=json.dumps(pack, ensure_ascii=False).encode("utf-8"),
         headers={"x-upsert": "true", "Content-Type": "application/json"})
    print(f"storage: uploaded {obj_path}")

    # 2 ── levels
    upsert(url, key, "levels",
           [{"id": l["id"], "title": l["t"], "sort_order": l["s"]} for l in pack["lvls"]],
           "id")

    # 3 ── categories (natural key: level_id+key)
    upsert(url, key, "categories",
           [{"level_id": c["lv"], "key": c["k"], "title": c["t"],
             "icon": c["i"], "sort_order": i}
            for i, c in enumerate(pack["cats"])],
           "level_id,key")

    # resolve category ids
    body, _ = rest(url, key, "/rest/v1/categories?select=id,level_id,key&limit=1000")
    cat_id = {(c["level_id"], c["key"]): c["id"] for c in body}

    # 4 ── collections (slug natural key)
    upsert(url, key, "collections",
           [{"slug": c["s"], "title": c["t"], "sort_order": i}
            for i, c in enumerate(pack["colls"])],
           "slug")
    body, _ = rest(url, key, "/rest/v1/collections?select=id,slug")
    col_id = {c["slug"]: c["id"] for c in body}

    # 5 ── words + meanings (word+pos natural key)
    word_rows, meaning_rows = [], []
    lvl_index = {l["id"]: i for i, l in enumerate(pack["lvls"])}
    for w, pos, fa, cat_idx, _cols in pack["words"]:
        cat = pack["cats"][cat_idx]
        word_rows.append({"word": w, "pos": pos, "level_id": cat["lv"],
                          "category_id": cat_id[(cat["lv"], cat["k"])],
                          "difficulty": min(5, 1 + lvl_index[cat["lv"]])})
    upsert(url, key, "words", word_rows, "word,pos")

    body, _ = rest(url, key, "/rest/v1/words?select=id,word,pos&limit=100000")
    word_id = {(w["word"].lower(), w["pos"]): w["id"] for w in body}
    for w, pos, fa, cat_idx, _cols in pack["words"]:
        wid = word_id[(w.lower(), pos)]
        meaning_rows.append({"word_id": wid, "meaning_fa": fa, "sort_order": 0})
    upsert(url, key, "word_meanings", meaning_rows, "word_id,sort_order")

    # 6 ── collection links
    link_rows = []
    for w, pos, fa, cat_idx, cols in pack["words"]:
        wid = word_id[(w.lower(), pos)]
        for ci in cols:
            slug = pack["colls"][ci - 1]["s"]
            link_rows.append({"collection_id": col_id[slug], "word_id": wid})
    upsert(url, key, "collection_words", link_rows, "collection_id,word_id")

    # 7 ── register + activate the pack
    rest(url, key, "/rest/v1/content_packs?on_conflict=version", method="POST",
         payload=[{"version": version, "storage_path": obj_path,
                   "word_count": len(pack["words"]), "is_active": False}],
         headers={"Prefer": "resolution=merge-duplicates,return=minimal"})
    rest(url, key, "/rest/v1/content_packs?version=neq." + str(version),
         method="PATCH", payload={"is_active": False},
         headers={"Prefer": "return=minimal"})
    rest(url, key, "/rest/v1/content_packs?version=eq." + str(version),
         method="PATCH", payload={"is_active": True},
         headers={"Prefer": "return=minimal"})

    print(f"\nDONE: pack v{version} is active "
          f"({len(word_rows)} words, {len(link_rows)} collection links).")


if __name__ == "__main__":
    main()
