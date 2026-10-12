#!/usr/bin/env python3
"""
load_content_supabase.py — load content-pack.json into a Supabase project.

NEVER run this with keys written in code. Required environment variables:

    SUPABASE_URL          e.g. https://abcdefgh.supabase.co
    SUPABASE_SERVICE_KEY  secret key: sb_secret_... (new) or the legacy
                          service_role JWT (from Supabase dashboard → API)

What it does (idempotent — safe to re-run):
  1. uploads content-pack.json to Storage bucket 'content-packs'
  2. upserts levels → categories → collections → words → meanings → links
  3. registers the pack in public.content_packs and marks it active

Word ids: the app keys progress by its LOCAL word id = position in the pack
(1-based). Synced rows (user_word_progress, review_log) reference
public.words(id), so the server MUST use the same ids. This script writes
them explicitly and aborts if an existing row disagrees, instead of silently
mapping a user's progress onto the wrong words.

The secret key bypasses RLS and must NEVER ship inside the Android app.
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
PAGE = 1000   # PostgREST/Supabase cap rows per response (max_rows, default 1000)


def rest(url, key, path, method="GET", payload=None, headers=None, raw=False):
    data = None
    hdrs = {
        "apikey": key,
        "Content-Type": "application/json",
    }
    # Legacy JWT keys also go in Authorization; new sb_secret_ keys are not
    # JWTs and are authenticated by the apikey header alone.
    if key.startswith("eyJ"):
        hdrs["Authorization"] = f"Bearer {key}"
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


def fetch_all(url, key, path):
    """GET every row of `path` (must contain '?' and an order=), page by page."""
    rows, offset = [], 0
    while True:
        body, _ = rest(url, key, f"{path}&limit={PAGE}&offset={offset}")
        body = body or []
        rows.extend(body)
        if len(body) < PAGE:
            return rows
        offset += PAGE


def main():
    url = os.environ.get("SUPABASE_URL")
    key = os.environ.get("SUPABASE_SERVICE_KEY")
    if not url or not key:
        sys.exit("!! set SUPABASE_URL and SUPABASE_SERVICE_KEY environment variables")
    url = url.rstrip("/")
    if key.startswith("sb_publishable_"):
        sys.exit("!! that is the PUBLISHABLE key — this script needs the secret key (sb_secret_...)")
    if not key.startswith("sb_secret_") and "service_role" not in key and len(key) < 200:
        print("!! warning: key does not look like a secret/service_role key — aborting")
        sys.exit(1)

    pack = json.loads(PACK.read_text(encoding="utf-8"))
    version = pack["v"]
    print(f"content pack v{version}: {len(pack['words'])} words, "
          f"{len(pack['cats'])} categories, {len(pack['colls'])} collections")

    # 0 ── word-id guard: server ids must equal the app's local ids (pack order)
    expected = {(w, pos): i + 1 for i, (w, pos, *_rest) in enumerate(pack["words"])}
    existing = fetch_all(url, key, "/rest/v1/words?select=id,word,pos&order=id")
    clashes = [r for r in existing
               if (r["word"], r["pos"]) in expected and expected[(r["word"], r["pos"])] != r["id"]]
    if clashes:
        sample = ", ".join(f"{r['word']}/{r['pos']} id={r['id']}" for r in clashes[:5])
        sys.exit(f"!! {len(clashes)} words already exist with ids that differ from the app's "
                 f"(e.g. {sample}). Synced progress would point at the wrong words. "
                 f"Empty the content tables (words, word_meanings, collection_words) "
                 f"on this project and re-run.")

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
    body = fetch_all(url, key, "/rest/v1/categories?select=id,level_id,key&order=id")
    cat_id = {(c["level_id"], c["key"]): c["id"] for c in body}

    # 4 ── collections (slug natural key)
    upsert(url, key, "collections",
           [{"slug": c["s"], "title": c["t"], "sort_order": i}
            for i, c in enumerate(pack["colls"])],
           "slug")
    body = fetch_all(url, key, "/rest/v1/collections?select=id,slug&order=id")
    col_id = {c["slug"]: c["id"] for c in body}

    # 5 ── words (explicit ids = pack order) + meanings
    word_rows, meaning_rows = [], []
    lvl_index = {l["id"]: i for i, l in enumerate(pack["lvls"])}
    for i, (w, pos, fa, cat_idx, _cols) in enumerate(pack["words"]):
        cat = pack["cats"][cat_idx]
        word_rows.append({"id": i + 1, "word": w, "pos": pos, "level_id": cat["lv"],
                          "category_id": cat_id[(cat["lv"], cat["k"])],
                          "difficulty": min(5, 1 + lvl_index[cat["lv"]])})
        meaning_rows.append({"word_id": i + 1, "meaning_fa": fa, "sort_order": 0})
    upsert(url, key, "words", word_rows, "id")
    upsert(url, key, "word_meanings", meaning_rows, "word_id,sort_order")

    # 6 ── collection links
    link_rows = []
    for i, (w, pos, fa, cat_idx, cols) in enumerate(pack["words"]):
        for ci in cols:
            slug = pack["colls"][ci - 1]["s"]
            link_rows.append({"collection_id": col_id[slug], "word_id": i + 1})
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
