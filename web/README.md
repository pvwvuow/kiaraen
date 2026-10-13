# kiara web UI

The approved kiara design is the app UI, exactly as designed.

**Put the exported design file here as `web/kiara-app.html`** (the single
self-contained file with all images, icons and fonts inside). The next build
packs it into the APK; without it the older native screens are used.

What the build does (`prepareKiaraWeb` task in `app/build.gradle.kts`):

1. reads `kiara-app.html` and applies the small hooks in `patches.json`
   (each one is a find/replace; a hook that no longer matches is skipped with
   a warning in the build log),
2. turns every inline base64 image/font into a real file under `assets/web/m/`
   (deduplicated: the 16 MB page becomes ~350 KB + ~90 images, so weak phones
   open it fast),
3. adds the shell: `kiara-shell.css` (full screen, no mockup frame),
   `kiara-fit.js` (fills the real screen: portrait fills the full height with
   up to 8% side crop, so the bottom nav sits on the true bottom edge),
   `kiara-dark.css` (the whole design re-issued dark under `html.kdark`;
   regenerate after design updates with `python3 web/build_dark.py`),
   `kiara-auth.js` (account screens + Supabase auth + cloud sync of the saved
   state + the dark-mode toggle; needs `kiara.supabase.*` in
   local.properties / CI secrets, otherwise it stays in guest mode) and
   `kiara-live.js` (real data: lessons from the 5000-word bank, spaced review,
   quiz results, streak, daily goal, coins, badges, bookmarks, search,
   reminders, settings, profile, all saved on the phone).

Cloud sync storage: `supabase/migrations/00006_app_state.sql` creates the
`app_state` table (one jsonb blob per user, RLS = owner only). Run it once in
the Supabase SQL editor; until then the app works and syncs nothing.

Preview on a computer: `python3 scripts/web/build_web.py` and open
`build/kiara-web/web/index.html` in Chrome with a 390x843 phone viewport.

Updating the design: export the new file over `kiara-app.html` and build.
Check the build log for `hook ... not found` warnings.
