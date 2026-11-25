#!/usr/bin/env bash
# What the release APK's bytes are, written down for the next round of
# making it smaller (native/PERF.md): by kind of file, and the code by
# package, largest first.
#
#   bash native/tool/apk_report.sh <apk> <R8 mapping.txt or ""> <output folder>
#
# The folder gets size-by-kind.txt, largest-files.txt and, where the Android
# SDK's apkanalyzer is installed, dex-packages.txt (every package with the
# bytes of dex its classes take, names read back through the mapping) and
# dex-top.txt (the hundred largest). Nothing here fails a build: a report
# that cannot be made says so and is left out.
set -uo pipefail
apk=$1
mapping=${2:-}
out=$3
mkdir -p "$out"

echo "APK: $(stat -c %s "$apk") bytes" | tee "$out/size-by-kind.txt"
unzip -lv "$apk" | awk 'NF==8 && $1 ~ /^[0-9]+$/ { n=$8; k="other"
  if (n ~ /\.dex$/) k="code (dex)"; else if (n ~ /^assets\/composeResources/) k="screen strings and pictures"
  else if (n ~ /^assets\//) k="other assets"; else if (n == "resources.arsc") k="resource table"
  else if (n ~ /^res\//) k="android resources"; else if (n ~ /^lib\//) k="native code"
  else if (n ~ /^META-INF\//) k="META-INF"; else if (n ~ /^kotlin\// || n ~ /\.kotlin_builtins$/) k="kotlin builtins"
  size[k]+=$3; raw[k]+=$1; count[k]++ }
  END { for (k in size) printf "%10d bytes packed  %10d unpacked  %5d files  %s\n", size[k], raw[k], count[k], k }' | sort -n -r | tee -a "$out/size-by-kind.txt"

unzip -lv "$apk" | awk 'NF==8 && $1 ~ /^[0-9]+$/ { printf "%10d %10d %s\n", $3, $1, $8 }' | sort -n -r | head -60 > "$out/largest-files.txt"

analyzer=$(command -v apkanalyzer || true)
[ -n "$analyzer" ] || analyzer=$(ls "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/nonexistent}}"/cmdline-tools/*/bin/apkanalyzer 2>/dev/null | head -1)
if [ -z "$analyzer" ]; then
  echo "apk_report: no apkanalyzer on this machine, so no dex-packages.txt"
  exit 0
fi
args=(dex packages --defined-only)
[ -n "$mapping" ] && [ -f "$mapping" ] && args+=(--proguard-mappings "$mapping")
# P/C/M/F, state, defined methods, referenced methods, bytes, name: the packages, largest first.
if "$analyzer" "${args[@]}" "$apk" 2> "$out/apkanalyzer.log" | awk -F'\t' '$1 == "P" { printf "%10d bytes  %6d methods  %s\n", $5, $3, $6 }' | sort -n -r > "$out/dex-packages.txt" && [ -s "$out/dex-packages.txt" ]; then
  head -100 "$out/dex-packages.txt" > "$out/dex-top.txt"
  echo "The code by package, largest first (a package counts what is under it):"
  head -70 "$out/dex-top.txt"
else
  echo "apk_report: apkanalyzer gave no packages:"; head -5 "$out/apkanalyzer.log"
fi
exit 0
