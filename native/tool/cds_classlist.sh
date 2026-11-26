#!/usr/bin/env bash
# Records the classes a start of the desktop app loads, into the app's
# lib/app/xta-native.classlist, from which packaging/linux/xta-native-launch
# makes each user's class-data sharing archive (a start then reads them from
# one mapped file: about half the time and processor to Home's first posts).
# The app runs against the stand-in for X (tool/mock-x) until Home has shown
# its posts, under a virtual display of its own (xvfb-run), or on $DISPLAY
# with --display, with data, cache and temporary files in a folder of its own.
#
#   bash native/tool/cds_classlist.sh <app dir> <stand-in url> [--display]
#
# A list that falls behind the code only costs speed: classes it names that
# are gone are skipped, and classes it lacks load as without an archive.
set -euo pipefail
app=${1:?the app folder createDistributable made}
mock=${2:?the stand-in for X, as http://127.0.0.1:8787}
on_display=${3:-}
seconds=${CDS_SECONDS:-25}
[[ -x "$app/bin/xta-native" && -d "$app/lib/app" ]] || {
  echo "error: $app is not the desktop app (expected bin/xta-native and lib/app)" >&2
  exit 1
}
app=$(cd "$app" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/tmp"
list="$app/lib/app/xta-native.classlist"

run='
  env JAVA_TOOL_OPTIONS="-XX:DumpLoadedClassList=$2/classlist -Djava.io.tmpdir=$2/tmp" XTA_NATIVE_MOCK_X="$3" \
    "XDG_DATA_HOME=$2/data" "XDG_CONFIG_HOME=$2/config" "XDG_CACHE_HOME=$2/cache" "$1/bin/xta-native" > "$2/app.log" 2>&1 &
  pid=$!
  sleep "$4"
  kill -0 "$pid" 2> /dev/null || { echo "XTA Native ended before its classes were listed:"; cat "$2/app.log"; exit 1; }
  kill "$pid"
  wait "$pid" || true
'
if [[ "$on_display" == --display ]]; then
  bash -c "$run" _ "$app" "$work" "$mock" "$seconds"
else
  xvfb-run -a -s '-screen 0 1280x960x24' bash -c "$run" _ "$app" "$work" "$mock" "$seconds"
fi
cp "$work/classlist" "$list"
classes=$(grep -cvE '^(#|@)' "$list" || true)
[ "$classes" -gt 3000 ] || { echo "error: only $classes classes listed; the app did not get to Home" >&2; cat "$work/app.log" >&2; exit 1; }
echo "Listed the $classes classes a start loads in $list"
