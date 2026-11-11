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
  echo "$label, installed: cold start $(cold_starts "$pkg" "$activity")" | tee -a "$out"
  adb shell cmd package compile -m speed-profile -f "$pkg" > /dev/null
  echo "$label, compiled: cold start $(cold_starts "$pkg" "$activity")" | tee -a "$out"
  adb shell am force-stop "$pkg"
  adb shell am start -W -n "$activity" > /dev/null
  sleep 10
  local pss
  pss=$(adb shell dumpsys meminfo "$pkg" | tr -d '\r' | sed -n 's/.*TOTAL PSS: *\([0-9][0-9]*\).*/\1/p' | head -1)
  [ -n "$pss" ] || pss=$(adb shell dumpsys meminfo "$pkg" | tr -d '\r' | awk '$1 == "TOTAL" { print $2; exit }')
  echo "$label, memory after start: ${pss:-?} kB PSS" | tee -a "$out"
  echo "$label, APK: $(stat -c %s "$apk") bytes" | tee -a "$out"
  adb shell am force-stop "$pkg"
}

: > "$out"
measure "XTA Native" com.aimdi.xtanative "$native_apk"
if [ -f "$xta_apk" ]; then
  measure "XTA" com.aimdi.xta "$xta_apk"
else
  echo "XTA: no APK to compare with" | tee -a "$out"
fi
