# Guide for agents / contributors

Read `README.md` for build commands, then the spec and the plan's **Status** section (end of the plan file)
for what is done, deviations, and known gaps.

## Architecture (dependency direction →)
`core:model` ← `core:japanese`, `core:yomitan`, `core:radicals` ← `core:db` ← `domain` ← `app` ← `androidApp` / `webApp`

- `core:japanese` — Kana, Romaji (`toHiragana` = live preview with `pending`; `finalize` = for submit), `normalizeQuery`,
  `Deinflector`. **`JapaneseTransforms.kt` is generated** from Yomitan by `tools/yomitan-transforms/regenerate.sh` — don't hand-edit.
- `core:yomitan` — `ZipArchive` (JVM/Android impl here; web impl in `webApp`), `DictionaryReader`, `BankParser`, `GlossaryParser` (structured-content → `ScNode`).
- `core:db` — one `SQLiteConnection` behind a mutex (`Database.read/write`), `Sql` helper, `Schema`, DAOs as `Sql` extensions.
- `domain` — `ImportService` (Flow of `ImportEvent`), `SearchService`, `LibraryService` (dictionaries/profiles/settings/media).
- `app` — Compose UI. `AppGraph` is the application-lifetime object graph; `PlatformServices` is the platform seam.
- `webApp/sqlite-worker/worker.js` implements the androidx `WebWorkerSQLiteDriver` message protocol over `@sqlite.org/sqlite-wasm` + `opfs-sahpool`.
  `webApp/web-helpers/index.js` = fflate zip, file picker, storage, Web Lock, history. Both are local npm packages copied into
  `build/wasm/node_modules` by `syncLocalNpmPackages` (edits take effect on next webApp build).

## Behaviours worth knowing
- Search: normalize → romaji→kana (raw query also tried exactly, for headwords like `Tシャツ`) → exact, deinflected (incl. する-stem), PARTIAL
  (longest leading word of a pasted phrase), prefix. Ranked by match type → #reasons → frequency rank → dict priority → score.
  Candidates are ranked from lightweight rows; full rows/glossaries load only for the shown page (glossary parsed lazily).
- Results merge by `(expression, reading)`; import normalizes empty reading to the expression so kana-only words merge.
- Frequency: `term_meta.freqValue` (numeric) drives ranking; "occurrence-based" dictionaries are inverted to a rank.
- Import is crash-safe: dictionaries start `IMPORTING`; any non-`READY` dictionary is deleted at startup (`cleanupIncomplete`).
  Everything after `READY` (REPLACE cleanup etc.) must never delete the new dictionary.
- Web: `driver` is lazy and the worker must only start **after** the Web Lock is held (otherwise tabs fight over OPFS handles).
  BEGIN/COMMIT/ROLLBACK are non-cancellable (a cancelled worker round trip would leave the connection mid-transaction).
- Schema changes need a real migration in `Schema.kt` (bump `VERSION`); v1 was edited in place pre-release, so dev databases from older builds must be wiped
  (web: clear site data / OPFS).
- Android compileSdk 37 / targetSdk 36 (needed by Compose 1.12 deps).

## Testing
- `./gradlew jvmTest`. Fixtures use **invented** entries only (`domain/src/jvmTest/.../TestDictionaries.kt`) — never commit real dictionary data.
- `RealDictionarySmokeTest` is opt-in: `JIBIKI_DICT_DIR=<dir of zips> JIBIKI_DICTS="jitendex,KANJIDIC" ./gradlew :domain:jvmTest --tests '*RealDictionarySmokeTest*' -i`
  (prints import times and search latencies/results).
- Web UI is a canvas; verify manually or via Playwright (not yet written — see TODO).

## Known gaps / next steps
1. Playwright E2E (Chromium + Firefox) and an Android instrumentation smoke test; Firefox is unverified by automation.
2. Web import parses JSON on the UI thread (yields between files); move import into the worker for huge dictionaries.
3. Word results capped at 50 (no paging); duplicate-import prompt doesn't distinguish same vs. new revision.
4. REPLACE import resets profile order/enabled flags; deleting a dictionary doesn't clear `primaryFrequencyDictId`.
5. Spec §9: storage-quota error doesn't show used/available; SVG media shows a placeholder.
6. Android: file-picker result is lost on rotation during the picker.
7. Single-kanji search loads all term keys for that kanji (fine at ~600k entries; consider a stored per-term best rank for scale).
