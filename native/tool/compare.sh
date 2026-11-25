#!/usr/bin/env bash
# Cold start, memory and size of XTA Native and XTA on the running emulator.
#
#   bash native/tool/compare.sh <XTA Native apk> <XTA apk> <output file>
#
# Where <XTA apk> is not a file (no XTA release to fetch one from), XTA Native
# is measured alone.
#
# Each app is opened once to set itself up, then started cold five times as
# installed and five times after Android has compiled it the way a day of use
# does (speed-profile); the median of each five is reported. XTA Native opens
# on its feed (the stand-in build, with posts); XTA opens with no account,
# which is the lighter start of the two.
set -euo pipefail
native_apk=$1
xta_apk=$2
out=$3

component() {
  adb shell cmd package resolve-activity --brief "$1" | tail -1 | tr -d '\r'
}

cold_starts() {
  local pkg=$1 activity=$2 times=()
  for _ in 1 2 3 4 5; do
    adb shell am force-stop "$pkg"
    sleep 2
    times+=("$(adb shell am start -W -n "$activity" | sed -n 's/^TotalTime: *//p' | tr -d '\r')")
    sleep 4
  done
  printf '%s\n' "${times[@]}" | sort -n | sed -n '3p' | tr -d '\n'
  printf ' ms (%s)' "${times[*]}"
}

measure() {
  local label=$1 pkg=$2 apk=$3
  adb install -r "$apk" > /dev/null
  local activity
  activity=$(component "$pkg")
  adb shell am start -W -n "$activity" > /dev/null
  sleep 10
  adb shell am force-stop "$pkg"
  adb logcat -c > /dev/null 2>&1 || true
  echo "$label, installed: cold start $(cold_starts "$pkg" "$activity")" | tee -a "$out"
  # Where each of those starts' time went, as the app marks it (StartMarks): the steps before its first frame.
  adb logcat -d 2> /dev/null | tr -d '\r' | sed -n 's/.*XTA Native: start: /'"$label"', start steps: /p' | tail -5 | tee -a "$out" || true
  adb shell cmd package compile -m speed-profile -f "$pkg" > /dev/null
  echo "$label, compiled: cold start $(cold_starts "$pkg" "$activity")" | tee -a "$out"
  # How Android has compiled it by now, and the start with every method compiled ahead of time: the most
  # a fuller profile could give (native/PERF.md).
  adb shell dumpsys package dexopt 2> /dev/null | tr -d '\r' | grep -A2 "\[$pkg\]" | grep -o "status=[a-z-]*\|reason=[a-z-]*" | sort -u | tr '\n' ' ' | sed "s/^/$label, compiled as: /; s/ \$//" | tee -a "$out" || true
  echo | tee -a "$out" > /dev/null
  adb shell cmd package compile -m speed -f "$pkg" > /dev/null || true
  adb logcat -c > /dev/null 2>&1 || true
  echo "$label, all compiled: cold start $(cold_starts "$pkg" "$activity" || true)" | tee -a "$out"
  adb logcat -d 2> /dev/null | tr -d '\r' | sed -n 's/.*XTA Native: start: /'"$label"', all compiled, start steps: /p' | tail -3 | tee -a "$out" || true
  adb shell am force-stop "$pkg"
  adb shell am start -W -n "$activity" > /dev/null
  sleep 10
  local pss
  pss=$(adb shell dumpsys meminfo "$pkg" | tr -d '\r' | sed -n 's/.*TOTAL PSS: *\([0-9][0-9]*\).*/\1/p' | head -1)
  [ -n "$pss" ] || pss=$(adb shell dumpsys meminfo "$pkg" | tr -d '\r' | awk '$1 == "TOTAL" { print $2; exit }')
  echo "$label, memory after start: ${pss:-?} kB PSS" | tee -a "$out"
  # Where that memory is (Java heap, native heap, code, graphics…), for the next round of making it less (native/PERF.md).
  adb shell dumpsys meminfo "$pkg" | tr -d '\r' | sed -n '/App Summary/,/TOTAL/p' | awk 'NF >= 2 && /:/ { gsub(/^ +/, ""); print "'"$label"', memory: " $0 }' | tee -a "$out" || true
  echo "$label, APK: $(stat -c %s "$apk") bytes" | tee -a "$out"
  adb shell am force-stop "$pkg"
}

: > "$out"
# How fast this runner's emulator is today: the phone's own Settings, started cold the same way. A start
# of the app reads against it, since one runner is a fifth slower than the next (native/PERF.md).
settings=$(component com.android.settings || true)
case "$settings" in
  */*) echo "Reference, Android's Settings: cold start $(cold_starts com.android.settings "$settings" || true)" | tee -a "$out" || true ;;
  *) echo "Reference: Android's Settings has no activity to start here" | tee -a "$out" ;;
esac
measure "XTA Native" com.aimdi.xtanative "$native_apk"
if [ -f "$xta_apk" ]; then
  measure "XTA" com.aimdi.xta "$xta_apk"
else
  echo "XTA: no APK to compare with" | tee -a "$out"
fi
