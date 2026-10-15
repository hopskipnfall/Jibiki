# Jibiki (字引) — Product Requirements & Design

- **Status:** Approved
- **Date:** 2026-10-15
- **Platforms:** Android, Web (Kotlin/Wasm) — Compose Multiplatform
- **Audience:** Personal tool first; may be shared/open-sourced later

## 1. Summary

Jibiki is a Japanese dictionary app for Android and the web that ships **no dictionary data**. Users import their own Yomitan-format dictionaries (JP→EN bilingual and JP→JP monolingual), which persist locally across sessions and reloads — including in Firefox. Users can search by word, by reading (kana or romaji, with deinflection), by kanji, and by radicals (for kanji seen in the real world). Results show which dictionary each definition came from and surface the kanji in each word.

## 2. Goals & non-goals

### Goals (v1)
1. Onboarding that takes the user from an empty app to a working dictionary.
2. Import Yomitan dictionaries; data persists across sessions/reloads on Android and web (Firefox 120+, Chromium).
3. Exact word/phrase search, with Yomitan-style deinflection.
4. Phonetic search by hiragana, katakana, or romaji.
5. Kanji lookup by radical picker.
6. Search for words containing a given kanji.
7. Word results merged across dictionaries, each definition block labelled with its source dictionary.
8. Kanji results: the kanji in the query and in top word results appear as kanji cards.

### Non-goals (v1)
Handwriting input, camera/OCR, English→Japanese reverse search, history/favorites, Anki export, cross-device sync, iOS/desktop targets, Yomitan format v1/v2.

## 3. Content & licensing constraints

- No term, definition, or kanji dictionary data is bundled.
- **Bundled:** EDRDG KRADFILE/RADKFILE (CC BY-SA 4.0), attributed in About/Licenses. Needed because Yomitan kanji dictionaries contain no radical decomposition.
- Onboarding links to Yomitan's dictionary documentation page (not to dictionary files).
- Per-dictionary attribution (from each `index.json`) is shown in Dictionaries and About.
- **Deinflection rules:** Yomitan is GPL-3.0. Decision (confirmed 2026-10-15): port Yomitan's Japanese transform rules and license Jibiki GPL-3.0.

## 4. Users & key scenarios

- **Reading something, unknown conjugated word:** paste/type `食べなかった` → top result 食べる, with JMdict and 大辞林 definitions labelled, plus a kanji chip for 食.
- **Heard a word:** type `taberu` → box shows "Searching たべる" → results.
- **Saw a kanji on a sign:** open radical picker, tap 氵 and 每 → 海 appears → tap it → inserted in search box → kanji card + words.
- **Exploring a kanji:** search `海` → kanji card, word results, "Words containing 海" section.
- **Immersion:** switch profile toggle to 日本語 → only monolingual dictionaries shown.

## 5. Architecture

### 5.1 Targets
`androidTarget` and `wasmJs` (browser). Compose Multiplatform UI shared. Web requires WasmGC: Firefox 120+, Chrome/Edge 119+; Safari best-effort.

### 5.2 Modules

| Module | Responsibility | Depends on |
|---|---|---|
| `core:model` | Plain data types: `Term`, `KanjiEntry`, `Dictionary`, `Profile`, `StructuredContent` AST, metadata types | — |
| `core:japanese` | Romaji→kana, hira↔kata, script detection, NFKC normalization, deinflector | model |
| `core:yomitan` | Streaming parser for Yomitan zips: `index.json`, all bank types, structured-content → AST, media enumeration. No DB knowledge | model |
| `core:db` | SQLite schema + queries; `expect` driver (Android bundled SQLite; web sqlite-wasm with `opfs-sahpool` VFS in a Worker) | model |
| `core:radicals` | Bundled KRADFILE/RADKFILE; radical→kanji bitsets, kanji→components, radical stroke counts, display-glyph substitution map | — |
| `domain` | `ImportService`, `SearchService`, `RadicalSearchService`, `ProfileService`, `DictionaryService` | core:* |
| `app` | Compose screens, navigation, ViewModels, structured-content renderer | domain |
| `androidApp`, `webApp` | Entry points: file picker, storage-persistence request, worker bootstrap | app |

### 5.3 Platform seams (`expect`/`actual`)
- `DatabaseDriverFactory`
- `FilePicker` — returns a streaming byte source; multi-select supported
- `ZipReader` — `java.util.zip` on Android, `fflate` via JS interop on web
- `PersistentStorage` — `request()` / `estimate()`; web uses `navigator.storage.persist()`/`estimate()`, Android no-op/filesystem stats
- Media is stored as BLOBs in SQLite on both platforms (no separate file store).

### 5.4 Threading
- **Web:** a dedicated Web Worker owns SQLite and runs import + queries; UI thread only sends typed messages and renders. Web Locks ensure one tab owns the database.
- **Android:** same `domain` code on `Dispatchers.IO`.

### 5.5 Milestone 0 — storage spike (gate)
Prove from Kotlin/Wasm, in Firefox and Chrome:
- sqlite-wasm + `opfs-sahpool` opens in a Worker without COOP/COEP headers;
- ~200k rows insert in under 60 s;
- data survives reload and browser restart.

**Fallback if the spike fails:** keep `core:db`'s repository interface and implement it on IndexedDB for web.

## 6. Data model

### 6.1 Supported Yomitan input (format/version 3)

| File | Row shape | Use |
|---|---|---|
| `index.json` | title, revision, format, author, url, description, attribution, sourceLanguage, targetLanguage, frequencyMode… | Dictionary row |
| `term_bank_*.json` | expression, reading, definitionTags, rules, score, glossary[], sequence, termTags | Word results |
| `term_meta_bank_*.json` | expression, `freq`/`pitch`/`ipa`, data | Frequency ranking/badges, pitch display; IPA stored, not shown in v1 |
| `kanji_bank_*.json` | character, onyomi, kunyomi, tags, meanings[], stats{} | Kanji cards |
| `kanji_meta_bank_*.json` | character, `freq`, data | Kanji frequency |
| `tag_bank_*.json` | name, category, order, notes, score | Tag chips + notes |
| other files | images referenced by structured-content | `media` |

### 6.2 Schema (shared SQLite)

- `dictionary(id, title, revision, author, url, description, attribution, sourceLang, targetLang, kinds, termCount, kanjiCount, metaCount, mediaBytes, importedAt, status)` — `status ∈ {importing, ready, failed}`
- `term(id, dictId, expression, reading, readingKey, defTags, rules, score, sequence, termTags, glossaryJson)` — `readingKey` = reading (or expression if reading empty) normalized to hiragana. Indexes: `(expression)`, `(readingKey)`.
- `term_kanji(kanji, termId)` — one row per distinct kanji in `expression`. Index: `(kanji)`.
- `term_meta(dictId, expression, reading, mode, dataJson)` — Index: `(expression)`.
- `kanji(dictId, character, onyomi, kunyomi, tags, meaningsJson, statsJson)` — Index: `(character)`.
- `kanji_meta(dictId, character, mode, dataJson)` — Index: `(character)`.
- `tag(dictId, name, category, sortOrder, notes, score)`
- `media(dictId, path, mime, width, height, bytes)` — PK `(dictId, path)`.
- `profile(id, name, sortOrder)` — seeded: `English`, `日本語`. `All` is virtual (every enabled dictionary).
- `profile_dictionary(profileId, dictId, priority, enabled)`
- `settings(key, value)` — includes `primaryFrequencyDictId`, `romajiAutoConvert`, `theme`, `fontScale`, `onboardingDone`.

## 7. Feature design

### 7.1 Onboarding (F1)
Skippable; re-runnable from Settings.
1. **Welcome** — what Jibiki is.
2. **Bring your own dictionaries** — why nothing is bundled; what Yomitan format is; link to Yomitan's dictionaries docs page.
3. **Import** — multi-file picker; per-file progress; user may continue to the next step while import runs.
4. **Review profiles** — shows automatic English/日本語 assignment; user can move dictionaries or reorder.
5. **(Web only) Keep data safe** — explains browser eviction; calls `navigator.storage.persist()` (Firefox shows a prompt). If denied, Dictionaries screen shows a persistent warning chip with a retry action.

Empty state on Search (no ready dictionaries) shows a single "Import dictionaries" call to action.

### 7.2 Adding & managing dictionaries (F2)
**Import pipeline (`ImportService`):**
1. Open zip, parse `index.json`; reject non-zip, missing index, or format ≠ 3 before any writes.
2. If title+revision already exists → prompt *Replace* / *Skip*. Same title, different revision → prompt *Update* (replace) / *Keep both*.
3. Insert `dictionary` with `status=importing`.
4. Stream each bank file with an incremental JSON parser; insert in transactions of ~5,000 rows; emit `ImportProgress(dictTitle, currentFile, filesDone, filesTotal, rowsInserted)`.
5. Populate `term_kanji` during term insert; store media files.
6. Set `status=ready`, record counts and size.
7. **Profile auto-assignment:** term dictionaries with `targetLanguage` `en` (or, if missing, glossaries predominantly Latin script) → `English`; `ja` (or predominantly Japanese) → `日本語`. Kanji, frequency and pitch dictionaries → both. Appended at lowest priority.

**Crash safety:** at startup, delete all rows for any dictionary in `importing` or `failed` state (chunked), then delete the dictionary row. A reload mid-import never leaves partial data.

**Management screen:** list with title, revision, kinds, size, status, enabled toggle; import button; delete (chunked, with progress); attribution details; total storage used/available (`estimate()` on web).

**Persistence requirement:** on web, imported dictionaries must survive reload and browser restart in Firefox 120+ and Chromium when persistence is granted; best-effort otherwise.

### 7.3 Smart search box (F3, F4)
One input field; 150 ms debounce; each keystroke cancels the in-flight query.

**Pipeline (`SearchService`):**
1. **Normalize:** trim; NFKC (full-width ASCII → half-width, half-width kana → full-width).
2. **Detect script:** if the input contains Latin letters and `romajiAutoConvert` is on → convert romaji→hiragana (Hepburn + IME variants: `shi/si`, `chi/ti`, `tsu/tu`, `fu/hu`, `ji/zi`, `nn`/`n'`, `xtu`/`ltu` small kana, doubled consonants → っ). Show hint "Searching たべる". Trailing incomplete romaji (e.g. `tabe` + `r`) is converted up to the last complete syllable.
3. **Lookups** within the active profile's enabled dictionaries:
   - **Exact:** `expression = q` OR `readingKey = toHiragana(q)`.
   - **Deinflected:** deinflector yields `(baseForm, requiredRuleSet, reasonChain)`; match on expression/readingKey; keep only terms whose `rules` intersect `requiredRuleSet`. Card shows reason chain (e.g. "negative · past").
   - **Prefix:** `expression` or `readingKey` in `[q, q + '￿')`.
4. **Merge** into groups keyed by `(expression, reading)`.

**Ranking of word groups:**
1. Match type: exact > deinflected (fewer transforms first) > prefix.
2. Frequency: best rank from `primaryFrequencyDictId`, else best across frequency dictionaries in the profile; missing frequency ranks last.
3. Highest-priority dictionary present in the group.
4. Yomitan `score` (desc).

Sections page at 50 groups with lazy loading.

### 7.4 Word results (F7)
Each word card shows:
- Headword with furigana (reading over expression), pitch accent graph/downstep marker when pitch data exists, frequency badges (dictionary name + value).
- Definition blocks ordered by dictionary priority; each block has a **dictionary label** (title), its tags (with notes on hover/long-press), and the rendered glossary.
- Deinflection reason chain where applicable.

**Glossary rendering:** strings render as text; `{"type":"text"}` as text; `{"type":"image"}` from `media`; `{"type":"structured-content"}` rendered by a Compose renderer supporting Yomitan's element set (`span`, `div`, `ol`, `ul`, `li`, `table`/`thead`/`tbody`/`tr`/`td`/`th`, `ruby`/`rt`/`rp`, `br`, `a`, `img`, `details`/`summary`) and its `style`/`data` attributes that map cleanly (font weight/style/size, color, margins, list style, vertical alignment). Internal links (`?query=…`) run a new search. Unknown elements render their children as plain text. Missing media renders a placeholder box.

### 7.5 Kanji results (F8)
**Kanji section** on the results page: a horizontal strip of kanji chips for the distinct kanji in the (normalized) query, followed by kanji in the headwords of the top 3 word groups, deduplicated, in first-seen order. A chip shows the character plus a primary meaning from the highest-priority kanji dictionary. Chips appear even if no kanji dictionary is installed (character + radical components only).

**Kanji detail screen:**
- Large character; stroke count, grade, JLPT and other `stats` from each kanji dictionary.
- On'yomi / kun'yomi / meanings per kanji dictionary, each labelled with the dictionary name.
- Kanji frequency badges.
- Radical components (from KRADFILE), each tappable to open the radical picker preselected.
- "Words containing this kanji" list (F6).

### 7.6 Words containing a kanji (F6)
- If the query is a single kanji, results show a "Words containing 漢" section below Kanji.
- Every kanji detail screen has a full list.
- Query: join `term_kanji` → `term` filtered by profile; ranked by frequency then dictionary priority; paged by 50.

### 7.7 Radical picker (F5)
Opened by a button beside the search box; bottom sheet on narrow screens, side panel on wide web screens.
- Grid of RADKFILE radicals grouped by stroke-count headers.
- Radicals rendered via a substitution map from RADKFILE stand-in characters to proper display glyphs (e.g. 化 → ⺅).
- Selecting radicals intersects precomputed kanji bitsets (in-memory, loaded once); radicals that cannot co-occur with the current selection are greyed out and disabled.
- Candidate strip above the grid, sorted by stroke count (from installed kanji dictionary `stats.strokes`; else estimated by component count), with an optional stroke-count filter.
- Tapping a candidate inserts it at the cursor in the search box (enabling multi-kanji words) and runs the search; picker stays open on wide screens, closes on narrow screens.
- "Clear" resets selection.

### 7.8 Profiles
- Toggle in the search bar: `English` / `日本語` / `All`. Active profile persists.
- Profiles screen: per profile, drag to reorder dictionaries and toggle enabled; choose primary frequency dictionary.

### 7.9 Settings
Theme (system/light/dark), romaji auto-conversion, result font size, re-run onboarding, About/Licenses (Jibiki GPL-3.0, KRADFILE/RADKFILE EDRDG CC BY-SA 4.0, Yomitan deinflection rules, per-dictionary attributions).

## 8. Navigation & layout
Single navigation graph: Onboarding → Search (home). From Search: Kanji detail, Radical picker, and an overflow menu to Dictionaries, Profiles, Settings. Wide web screens (≥ 840 dp) use two panes: results list left, selected word/kanji detail right. Android back and browser back/forward map to the nav stack; the current query is reflected in the web URL (`#q=…`) so reloads restore the search.

## 9. Error handling

| Situation | Behavior |
|---|---|
| Not a zip / no `index.json` / format ≠ 3 | Rejected before writes; message names the problem |
| Malformed rows | Skipped; count reported after import ("12 rows skipped") |
| Storage quota exceeded | Import marked failed; rows cleaned up; message shows used/available storage |
| OPFS unavailable (e.g. Firefox private window) | In-memory database + banner "Data won't survive reload" |
| App open in another tab (lock held) | "Jibiki is open in another tab" screen with Retry |
| Import interrupted (reload/crash) | Partial dictionary removed at next start; Dictionaries screen notes it |
| Unknown structured-content element | Children rendered as plain text |
| Missing media | Placeholder box |
| Persistence denied (web) | Warning chip in Dictionaries with retry |

## 10. Testing & quality
- **Unit:** `core:japanese` (romaji table incl. edge cases, kana conversion, deinflection golden cases); `core:yomitan` (handmade fixture zips with invented entries — no real dictionary data in the repo); `core:radicals` (intersection, greying logic); `domain` search ranking against in-memory SQLite.
- **Web E2E:** Playwright on Firefox and Chromium — import fixture zip → search → reload → results still present.
- **Android:** instrumentation smoke test for import + search.
- **Performance budgets:**
  - Search p95 < 100 ms with JMdict + one large monolingual dictionary, mid-range Android device.
  - JMdict (English) import < 3 min on web (desktop), < 5 min on mid-range Android.
  - First search interactive < 2 s after app start with dictionaries installed.

## 11. Milestones
0. **Storage spike** (gate, §5.5).
1. **Foundation:** project scaffold, modules, schema, Yomitan parser, import with progress & crash safety, Dictionaries screen.
2. **Search core:** smart box, normalization, romaji, exact/prefix, merged word cards with dictionary labels (plain-text glossary first).
3. **Deinflection.**
4. **Kanji:** kanji section, kanji detail, words-containing, radical picker.
5. **Rich rendering:** structured-content renderer, media, tags, frequency & pitch display.
6. **Profiles, onboarding, settings, persistence prompt, error states, two-pane web layout.**
7. **Polish & perf** against budgets; Playwright + Android smoke tests.

## 12. Open questions
- Exact pitch-accent visual style (graph vs. downstep markers) — decide during milestone 5.
- Additional features the user has in mind beyond v1.
