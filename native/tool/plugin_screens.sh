#!/usr/bin/env bash
# The plugin store's whole chain on a running emulator, after screens.sh,
# with the app installed from a build made with -PpluginCatalogue naming a
# catalogue the runner serves: the first plugin installed through the store
# (downloaded, checked, loaded), opened, pinned on Home's strip and shown
# there, then uninstalled; a screenshot of each step. Fails when the app
# crashed, or the plugin was not installed, loaded and uninstalled.
#
#   bash native/tool/plugin_screens.sh <output dir> [plugin name in the store, Bluesky]
set -euo pipefail
out=$1
name=${2:-Bluesky}
pkg=com.aimdi.xtanative
mkdir -p "$out"

shot() {
  sleep "$2"
  adb exec-out screencap -p > "$out/$1.png"
  echo "shot $1"
}

# The bounds "left top right bottom" of the nodes holding $1 (text="…" or
# content-desc="…"), the topmost first.
node_boxes() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml 2> /dev/null | tr '>' '\n' | grep -F -- "$1" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' \
    | sort -n -k2,2 -k4,4 || true
}

# Taps the middle of the topmost node holding $1, or of the lowest when $2 is "last".
tap_node() {
  local box left top right bottom
  if [ "${2:-}" = last ]; then box=$(node_boxes "$1" | tail -1); else box=$(node_boxes "$1" | head -1); fi
  if [ -z "$box" ]; then
    echo "Nothing on screen holds $1"
    return 1
  fi
  read -r left top right bottom <<< "$box"
  adb shell input tap $(((left + right) / 2)) $(((top + bottom) / 2))
}

# Waits up to $2 seconds for something on screen to hold $1.
wait_for() {
  for _ in $(seq 1 "$2"); do
    [ -n "$(node_boxes "$1")" ] && return 0
    sleep 1
  done
  echo "Nothing held $1 after $2 s"
  return 1
}

# Why it failed, in the job's own log: the app's plugin lines (why a plugin
# was not started), a crash, and what the screen says (a row's status line).
fail() {
  echo "$1"
  adb logcat -d > "$out/plugin-logcat.txt" || true
  grep -F "XTA Native: plugin" "$out/plugin-logcat.txt" || true
  grep -A 30 -E "FATAL EXCEPTION|E AndroidRuntime" "$out/plugin-logcat.txt" | head -60 || true
  echo "On screen:"
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml 2> /dev/null | tr '>' '\n' | sed -n 's/.* text="\([^"][^"]*\)".*/  \1/p' | head -40 || true
  exit 1
}

# The store, from Home's account menu.
open_store() {
  tap_node 'content-desc="Open navigation menu"' || fail "No account menu on Home"
  sleep 2
  tap_node 'text="Plugin store"' || fail "No plugin store in the account menu"
  sleep 3
}

back() {
  adb shell input keyevent KEYCODE_BACK
  sleep 2
}

adb logcat -c
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/.MainActivity" > /dev/null
sleep 8

# The store reads the runner's catalogue.
open_store
wait_for "text=\"$name\"" 30 || { shot plugin-store-empty 0; fail "The store does not offer $name"; }
shot plugin-store 2

# Install: downloaded, checked against the catalogue and the app's key, loaded.
tap_node 'text="Install"' || fail "No Install for $name"
shot plugin-store-installing 1
wait_for 'text="Open"' 90 || { shot plugin-store-failed 0; fail "$name was not installed"; }
shot plugin-store-installed 2

# On Home's strip, from its row's menu (the lowest "More options"; the bar's is above it).
tap_node 'content-desc="More options"' last || fail "No menu on $name's row"
sleep 1
tap_node 'text="Pin next to For you"' || fail "$name cannot be pinned on Home"
sleep 1

# Its home tab, opened from the store.
tap_node 'text="Open"' || fail "No Open for $name"
shot plugin-home 15

# Home, where its tab now sits on the strip.
back
back
wait_for "text=\"$name\"" 15 || { shot plugin-strip-missing 0; fail "$name is not on Home's strip"; }
tap_node "text=\"$name\"" || fail "No $name tab on Home's strip"
shot plugin-strip 12

# Uninstalled from the store, after its confirmation.
open_store
tap_node 'content-desc="More options"' last || fail "No menu on $name's row"
sleep 1
tap_node 'text="Uninstall"' || fail "No Uninstall for $name"
sleep 2
shot plugin-uninstall-confirm 0
tap_node 'text="Uninstall"' last || fail "No confirmation to uninstall $name"
wait_for 'text="Install"' 30 || { shot plugin-uninstall-failed 0; fail "$name was not uninstalled"; }
shot plugin-uninstalled 2

adb logcat -d > "$out/plugin-logcat.txt" || true
grep "XTA Native: plugin" "$out/plugin-logcat.txt" || true
if grep -q "FATAL EXCEPTION" "$out/plugin-logcat.txt"; then
  echo "XTA Native crashed:"
  grep -A 40 "FATAL EXCEPTION" "$out/plugin-logcat.txt" | head -80
  exit 1
fi
grep -q "XTA Native: plugin [a-z0-9_-]* .* loaded" "$out/plugin-logcat.txt" || fail "No plugin was loaded"
grep -q "XTA Native: plugin [a-z0-9_-]* uninstalled" "$out/plugin-logcat.txt" || fail "No plugin was uninstalled"
echo "The plugin was installed, loaded, opened, shown on Home and uninstalled; no crash."
