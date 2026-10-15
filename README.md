# Jibiki 字引

A Japanese dictionary for **Android and the web** (Kotlin Multiplatform + Compose Multiplatform).
It ships **no dictionary data**: you import [Yomitan](https://yomitan.wiki/dictionaries/)-format
(v3) dictionaries — bilingual (JA→EN), monolingual (JA→JA), kanji, frequency and pitch-accent.

Features: exact / reading (kana or romaji) / conjugated-form search, kanji lookup by radicals,
words containing a kanji, results merged across dictionaries and labelled by source, persistent storage
(SQLite; OPFS on the web, including Firefox).

## Build & run

Requires JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`) and, for Android, an SDK at `local.properties` (`sdk.dir=...`).

```bash
./gradlew jvmTest                                   # all unit tests (core + domain)
./gradlew :androidApp:assembleDebug                 # Android APK
./gradlew :webApp:wasmJsBrowserDistribution         # web bundle -> webApp/build/dist/wasmJs/productionExecutable
python3 -m http.server 8766 --directory webApp/build/dist/wasmJs/productionExecutable
```

Web needs WasmGC (Firefox 120+, Chrome 119+). If Gradle says the yarn lock changed, run `./gradlew kotlinWasmUpgradeYarnLock`.

CI (`.github/workflows/ci.yml`) runs the unit tests and builds the APK and web bundle on every PR.

## Docs
- [AGENTS.md](AGENTS.md) — architecture, conventions and gotchas for contributors/agents
- [docs/superpowers/specs/2026-10-15-jibiki-design.md](docs/superpowers/specs/2026-10-15-jibiki-design.md) — PRD/design
- [docs/superpowers/plans/2026-10-15-jibiki-v1.md](docs/superpowers/plans/2026-10-15-jibiki-v1.md) — implementation plan + status/deviations

## License
GPL-3.0-or-later (deinflection rules are ported from Yomitan). Bundled data: EDRDG RADKFILE (CC BY-SA 4.0), Noto Sans JP (OFL) — see `LICENSES/` and the in-app Licenses screen.
