#!/usr/bin/env bash
# Opens XTA Native's screens on a running emulator, against XTA's stand-in for X,
# and keeps a screenshot of each. Fails when the app crashed.
#
#   bash native/tool/screens.sh <apk built with -PmockX> <output dir>
set -euo pipefail
apk=$1
out=$2
pkg=com.aimdi.xtanative
mkdir -p "$out"

shot() {
  sleep "$2"
  adb exec-out screencap -p > "$out/$1.png"
  echo "shot $1"
}

open_link() {
  adb shell am start -W -a android.intent.action.VIEW -d "$1" -n "$pkg/.MainActivity" > /dev/null
}

adb install -r "$apk"
adb logcat -c

for mode in no yes; do
  adb shell cmd uimode night "$mode"
  adb shell am force-stop "$pkg"
  adb shell am start -W -n "$pkg/.MainActivity" > /dev/null
  shot "feed-night-$mode" 12
done

# The first post of the feed, opened by a tap: it is drawn from what the feed showed.
size=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
width=${size% *}
height=${size#* }
adb shell input tap $((width / 2)) $((height * 30 / 100))
shot post 6
adb shell input keyevent KEYCODE_BACK
sleep 2

open_link "https://x.com/quax_tests"
shot profile 8
open_link "https://x.com/search?q=kotlin"
shot search 8
adb shell am start -W -n "$pkg/.MainActivity" > /dev/null

adb logcat -d > "$out/logcat.txt" || true
if grep -q "FATAL EXCEPTION" "$out/logcat.txt"; then
  echo "XTA Native crashed:"
  grep -A 40 "FATAL EXCEPTION" "$out/logcat.txt" | head -80
  exit 1
fi
echo "No crash."
