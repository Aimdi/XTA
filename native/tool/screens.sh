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

# The bounds "left top right bottom" of the topmost thing on screen whose
# accessibility node holds $1 (a text, or text="..." for an exact one).
node_box() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml 2> /dev/null | tr '>' '\n' | grep -F -- "$1" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' \
    | sort -n -k2,2 -k4,4 | head -1 || true
}

# Taps what holds $1: its middle, or a text line 44dp into it when it is a
# whole post row. False when nothing on screen holds it.
tap_node() {
  local box left top right bottom reach half
  box=$(node_box "$1")
  if [ -z "$box" ]; then
    echo "Nothing on screen holds $1"
    return 1
  fi
  read -r left top right bottom <<< "$box"
  reach=$((density * 44 / 160))
  half=$(((bottom - top) / 2))
  adb shell input tap $(((left + right) / 2)) $((top + (half < reach ? half : reach)))
}

# The emulator's connection comes up some seconds after it reports booted.
wait_for_network() {
  local state
  for _ in $(seq 1 30); do
    state=$(adb shell dumpsys connectivity 2> /dev/null | grep -m1 "Active default network" || true)
    case "$state" in
      *"network: "[0-9]*) return 0 ;;
    esac
    sleep 1
  done
  echo "The emulator has no network yet; going on."
}

adb install -r "$apk"
adb logcat -c
wait_for_network

for mode in no yes; do
  adb shell cmd uimode night "$mode"
  adb shell am force-stop "$pkg"
  adb shell am start -W -n "$pkg/.MainActivity" > /dev/null
  shot "feed-night-$mode" 12
done

size=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
width=${size% *}
height=${size#* }
density=$(adb shell wm density | sed -n 's/.*: \([0-9]*\).*/\1/p' | tail -1)

# The first post of the feed, opened by a tap on its text: it is drawn at once
# from what the feed showed.
if tap_node "Quoting tweet with limited replies"; then
  shot post 6
  adb shell input keyevent KEYCODE_BACK
  sleep 2
fi

# Home's timelines by a swipe: X after Following, then back to Following.
adb shell input swipe $((width * 80 / 100)) $((height / 2)) $((width * 15 / 100)) $((height / 2)) 250
shot for-you 8
adb shell input swipe $((width * 20 / 100)) $((height / 2)) $((width * 85 / 100)) $((height / 2)) 250
shot following-again 4

open_link "https://x.com/quax_tests"
shot profile 8
# The stand-in's session follows it: Following opens what that means here.
if tap_node 'text="Following"'; then
  shot follow-sheet 3
  adb shell input keyevent KEYCODE_BACK
  sleep 1
fi
# Under the header: the tab row, held under the bar, and Posts with the pinned post first.
adb shell input swipe $((width / 2)) $((height * 75 / 100)) $((width / 2)) $((height * 25 / 100)) 400
shot profile-posts 4
if tap_node 'text="Media"'; then
  shot profile-media 6
fi
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
