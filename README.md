# Niblet

*Every panel, every page, everywhere.*

A local-first, multi-source comic / manga / PDF / EPUB reader for Android.
No accounts. No scraping. No telemetry. You point it at files, it reads them.

**License:** GPL-3.0
**Min SDK:** 24 (Android 7.0)
**Target SDK:** 35 (Android 15)
**Package:** `com.niblet.app`

---

## What works

### Formats
- **`.cbz`** — ZIP of images (the standard comic archive)
- **`.cbr`** — RAR of images (via junrar)
- **`.pdf`** — via Android's native `PdfRenderer`, renders text and images
- **`.epub`** — image-based EPUBs (manga EPUBs); text-only EPUBs render as blank pages
- **`.cb7` / `.cbt`** — recognized by extension, no reader yet (falls back to CBZ, will fail)

### Sources
- **Local folder** — SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`)
- **SMB 2/3** — host, share, user, password, optional base path
- **SFTP** — host, port, user, password, optional base path
- **WebDAV** — URL, user, password (Nextcloud, ownCloud, Synology, generic)

Sources merge into one library grid. Credentials are encrypted via
`EncryptedSharedPreferences` (with plaintext fallback if keyring unavailable).

### Library
- Grid of series, 2 columns, extracted cover thumbnails
- Search box — live filter by title
- Continue reading — horizontal strip of last 6 opened series
- Add local / Add network buttons
- Settings button in the toolbar

### Reading
- Single page, tap zones for next/prev
- **Right-to-left (manga)** — left tap advances
- **Webtoon vertical scroll** — continuous, auto-loads next page on scroll
- **Double-page spread** — landscape only, two pages side by side, RTL-aware
- Pinch to zoom, drag to pan
- Double-tap to zoom
- Fit modes: PAGE / WIDTH / HEIGHT
- **Per-series direction override** — each series remembers its own LTR/RTL/WEBTOON
- **Next/prev chapter** — tap past last page advances to next chapter, tap before page 1 goes back
- **Page-count awareness** — no out-of-range errors; long-press shows `Page 3/20`

### Reader menu (long-press on a page)
- Direction toggle (LTR / RTL / WEBTOON)
- Fit mode cycle
- Bookmark this page (saved to SharedPreferences, no UI to view list yet)
- Sleep timer 15/30 min (activity closes when timer fires)
- Jump to page (numeric input)
- Page indicator (read-only, appears after page count known)

### Gestures
- **Volume** — vertical drag on right edge
- **Brightness** — vertical drag on left edge
- Swipe horizontally (page mode) or vertically (webtoon mode)
- **Volume/brightness gestures only work in single-page mode.** Webtoon mode uses touch for scroll.

### Settings screen
- Default direction (LTR / RTL / WEBTOON)
- Default fit (PAGE / WIDTH / HEIGHT)
- Keep screen on toggle
- Panel-by-panel (experimental) toggle
- Double-page spread toggle
- Stats: pages read counter, reset button

### Persistence
- **Reading progress** — per chapter, page index, survives restart
- **Last-read timestamps** — drives continue-reading strip
- **Per-series reading direction** — falls back to global default
- **Covers** — cached to `cacheDir/covers/` after first extraction
- **Picked folder URI** — persisted via `takePersistableUriPermission`

---

## What's rough

### Panel detection
`PanelDetector` analyses rows and columns for uniform gutter regions (>90% match
within a small color delta). It works on clean white gutters between panels.
It **fails** on:
- Full-bleed art (no gutters)
- Dark pages where the gutter isn't visually distinct
- Webtoons (vertical strip format — no panels)
- Pages with decorative borders or textured gutters

The "Panel-by-panel" setting is a **toggle that does nothing yet** — the
detector exists but isn't hooked into the tap handler. Wiring it is a 20-line
change in `ReaderView.onSingleTapConfirmed`. Left unwired because false
positives are worse than no panel mode.

### EPUB
Image-based EPUBs (manga EPUBs) work — the OPF is parsed for image hrefs and
rendered as pages. **Text-only EPUBs render blank white pages.** A real
EPUB renderer needs a WebView or a full HTML-to-canvas engine. Out of scope
for v1.

### Cover extraction
Only works for **local** sources. Network sources skip cover extraction
because each cover requires downloading an entire chapter's archive from
SMB/SFTP/WebDAV — slow and unreliable. Covers on network sources stay grey.

### Volume / brightness gestures
Work in single-page mode. **Do not work in webtoon mode** (RecyclerView
consumes touch events first). Vertical gestures are unimplemented in webtoon.

### Bookmarks
Saved to SharedPreferences, but there's no UI to view, jump to, or delete
bookmarks. The data is there; the UI isn't.

### Sync
`SyncStore` exists and is fully coded — WebDAV upload/download of progress
JSON. **No server configured** means it does nothing. There's no Sync button
in the current library layout (removed during a layout revert). Re-adding it
requires: a toolbar button + a dialog to configure URL/user/pass. Code is
ready.

### `.cb7` and `.cbt`
Recognized by extension. `UniversalArchive` falls through to `CbzReader`
which will fail on 7z and TAR. No `SevenZReader` or `TarReader` written.

### OPDS
Not implemented. `SourceType.OPDS` enum exists but no implementation.

### Google Drive
Not implemented as a distinct source. SAF picker works for local Google Drive
folders on some devices but is unreliable and device-dependent.

### Error handling
Errors surface as `title = "Error: ${it.message}"` — raw exception strings in
the toolbar. Not user-friendly. Should show a Snackbar with a readable message.

### Webtoon back-navigation
Scrolling up past the first page of a chapter in webtoon mode does not go to
the previous chapter. Only forward navigation (auto-load-more) is implemented.

### Per-series fit / per-series zoom
Only direction is per-series. Fit and zoom are global.

---

## What's missing

- **Panel-by-panel wired up** (detector exists, needs hook)
- **Bookmarks UI** (save works, view/jump doesn't)
- **Sync UI** (code exists, no button)
- **OPDS feeds**
- **Google Drive as a first-class source**
- **`.cb7` / `.cbt` readers**
- **Text-only EPUB rendering**
- **Per-series fit and zoom**
- **Webtoon back-navigation to previous chapter**
- **Double-page in portrait** (currently landscape-only)
- **Reader rotation in split-screen** (not tested)
- **Hardware volume buttons as page-turn** (not implemented — only gesture)

---

## Project structure


app/src/main/java/com/niblet/app/
├── data/
│ ├── archive/
│ │ ├── CbzReader.kt — ZIP of images
│ │ ├── CbrReader.kt — RAR (junrar)
│ │ ├── PdfReader.kt — PdfRenderer to Bitmap
│ │ ├── EpubReader.kt — OPF parse, image hrefs
│ │ └── UniversalArchive.kt — dispatches by extension
│ ├── source/
│ │ ├── ComicSource.kt — interface
│ │ ├── LocalFolderSource.kt
│ │ ├── SmbSource.kt
│ │ ├── SftpSource.kt
│ │ ├── WebDavSource.kt
│ │ └── SourceManager.kt — opens by config
│ ├── model/
│ │ └── Models.kt — Series, Chapter
│ ├── BookmarksStore.kt
│ ├── CoverCache.kt
│ ├── LastReadStore.kt
│ ├── PerSeriesStore.kt
│ ├── ProgressStore.kt
│ ├── SecureStore.kt — encrypted creds
│ ├── SettingsStore.kt
│ ├── SourceStore.kt — network source configs
│ ├── SyncStore.kt — WebDAV progress sync (unused)
│ └── TreeStore.kt — persisted SAF URI
├── reader/
│ ├── PageLoader.kt — Bitmap LRU cache
│ ├── PanelDetector.kt — gutter analysis
│ ├── ReaderView.kt — page mode with zoom/pan
│ ├── SleepTimer.kt
│ └── WebtoonReader.kt — vertical RecyclerView adapter
├── ui/
│ ├── MainActivity.kt — library
│ ├── AddNetworkSourceActivity.kt
│ ├── SeriesDetailActivity.kt — local chapter list
│ ├── SeriesDetailNetworkActivity.kt
│ ├── ReaderActivity.kt — local reader
│ ├── ReaderNetworkActivity.kt
│ ├── SettingsActivity.kt
│ ├── SeriesAdapter.kt
│ └── ChapterAdapter.kt
└── util/
└── FileUtils.kt


---

## Build

Requires JDK 17+, Android SDK 35, Gradle 8.7+.

```bash
cd ~/Desktop/Niblet
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

Dependencies:

AndroidX core, appcompat, material, constraintlayout, recyclerview

documentfile, lifecycle-runtime-ktx

kotlinx-coroutines-android

Apache Commons Compress (CBZ, EPUB)

junrar via JitPack (CBR)

smbj (SMB)

jsch (SFTP)

OkHttp (WebDAV)


Test with sample files

cat > ~/Desktop/Niblet/README.md << 'EOF'


# Local CBZ
mkdir -p /sdcard/Comics/one-piece
cd ~/Desktop/test-comics/one-piece
for i in 01 02 03; do
  curl -sL "https://picsum.photos/seed/niblet$i/800/1200" -o "$i.jpg"
done
zip -j vol-001.cbz 01.jpg 02.jpg 03.jpg
adb push vol-001.cbz /sdcard/Comics/one-piece/

# PDF
curl -sL "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf" -o /tmp/test.pdf
adb shell mkdir -p /sdcard/Comics/pdf-test
adb push /tmp/test.pdf /sdcard/Comics/pdf-test/

# Open Niblet → Add local → select /sdcard/Comics → tap series → tap chapter


Known bugs
Fit mode / direction changes require re-opening the reader in webtoon mode (activity doesn't fully recreate).

First cover extraction can block the UI thread for very large first pages (not observed in tests, but BitmapFactory.decodeByteArray is synchronous in CoverCache.extract).

Page count is estimated by trial-and-error — the reader tries readPage(index) until it fails, then clamps. Costs one wasted decode per chapter.

Webtoon memory — long webtoon chapters (100+ pages) will eventually OOM the 32MB LRU cache. Scrolling back re-decodes from the archive.

Sleep timer title update — the title bar updates on every tick (1s), which is aggressive; cosmetic.

Contributing
Fork, PR. No CLA. GPL-3.0 means forks stay open.

Before opening a PR:

./gradlew assembleDebug must succeed

Test in both portrait and landscape

Test both local and one network source

License
GPL-3.0 — see LICENSE file (or https://www.gnu.org/licenses/gpl-3.0.html)

Niblet is free software. It is not affiliated with any comic/manga publisher,
scanner, or aggregator. It reads files you already have.
