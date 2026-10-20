#!/usr/bin/env bash
# Build the release APK twice from a clean tree and compare the results.
#
# A reproducible build is what would let F-Droid ship this app without
# replacing the signature — see docs/reproducible-builds.md. Signatures always
# differ, so the comparison ignores META-INF/ and looks at everything else:
# the dex, the assets, the Flutter AOT snapshot, the resources.
#
# Usage: scripts/verify_reproducible.sh [--keep]
set -euo pipefail

KEEP=0
[[ "${1:-}" == "--keep" ]] && KEEP=1

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap '[[ $KEEP -eq 1 ]] || rm -rf "$WORK"' EXIT

FLUTTER="${FLUTTER:-fvm flutter}"
DART="${DART:-fvm dart}"

echo "==> toolchain"
$FLUTTER --version
java -version 2>&1 | head -1
echo

build_once() {
  local label="$1"
  local out="$WORK/$label"

  echo "==> build $label"
  (
    cd "$REPO_ROOT"
    $FLUTTER clean >/dev/null
    $FLUTTER pub get >/dev/null
    $DART run dart_pubspec_licenses:generate >/dev/null
    $DART run intl_utils:generate >/dev/null
    $DART run flutter_iconpicker:generate_packs --packs material >/dev/null
    # No --split-per-abi: one artefact keeps the comparison unambiguous.
    $FLUTTER build apk --release --no-tree-shake-icons --split-debug-info=build/app/outputs/symbols
  )

  mkdir -p "$out"
  cp "$REPO_ROOT"/build/app/outputs/apk/release/*.apk "$out/app.apk"
  ( cd "$out" && unzip -q -o app.apk -d contents && rm -rf contents/META-INF )
}

build_once first
build_once second

echo
echo "==> comparing"
if diff -qr "$WORK/first/contents" "$WORK/second/contents" > "$WORK/diff.txt" 2>&1; then
  echo "REPRODUCIBLE — the two builds are byte-identical outside META-INF/."
  exit 0
fi

echo "NOT REPRODUCIBLE — these entries differ:"
cat "$WORK/diff.txt"
echo
echo "Inspect a specific entry with:"
echo "  diff <(xxd $WORK/first/contents/<path>) <(xxd $WORK/second/contents/<path>) | head"
[[ $KEEP -eq 1 ]] && echo "Artefacts kept in $WORK"
exit 1
