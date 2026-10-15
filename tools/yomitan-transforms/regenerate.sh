#!/usr/bin/env bash
# Regenerates core/japanese/.../JapaneseTransforms.kt from Yomitan's upstream rules.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
mkdir -p "$work/language/ja"
base=https://raw.githubusercontent.com/yomidevs/yomitan/master/ext/js/language
curl -sfL "$base/language-transforms.js" -o "$work/language/language-transforms.js"
curl -sfL "$base/ja/japanese-transforms.js" -o "$work/language/ja/japanese-transforms.js"
cp "$here/dump.mjs" "$work/dump.mjs"
node "$work/dump.mjs" > "$work/rules.json"
python3 "$here/gen_kotlin.py" "$work/rules.json" \
  "$here/../../core/japanese/src/commonMain/kotlin/dev/jibiki/japanese/JapaneseTransforms.kt"
