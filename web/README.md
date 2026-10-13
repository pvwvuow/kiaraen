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
   `kiara-fit.js` (scales the 390x843 design to any screen) and
   `kiara-live.js` (real data: lessons from the 5000-word bank, spaced review,
   quiz results, streak, daily goal, coins, badges, bookmarks, search,
   reminders, settings, profile, all saved on the phone).

Preview on a computer: `python3 scripts/web/build_web.py` and open
`build/kiara-web/web/index.html` in Chrome with a 390x843 phone viewport.

Updating the design: export the new file over `kiara-app.html` and build.
Check the build log for `hook ... not found` warnings.
