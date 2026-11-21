#!/usr/bin/env bash
# The plugin store's whole chain on a running emulator, after screens.sh,
# with the app installed from a build made with -PpluginCatalogue naming a
# catalogue the runner serves: the first plugin installed through the store
# (downloaded, checked, loaded), opened, pinned on Home and shown there from
# its timeline picker, then uninstalled; a screenshot of each step. Fails when the app
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

# The first plugin under the store's "Installed · n" heading: the name on its
# box, which a tap opens (a box has no Open button).
installed_plugin() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml 2> /dev/null | tr '>' '\n' | sed -n 's/.* text="\([^"][^"]*\)".*/\1/p' \
    | awk 'seen { print; exit } /^Installed/ { seen = 1 }' || true
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

# A swipe from low on the screen to high, to bring up what a sheet holds below.
swipe_up() {
  local size
  size=$(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
  adb shell input swipe $((${size% *} / 2)) $((${size#* } * 85 / 100)) $((${size% *} / 2)) $((${size#* } * 25 / 100)) 300
  sleep 1
}

# The account menu: no bar on Home has an avatar (as in XTA), so it is a row
# of Home's sheet, under the shown timeline's entries (the sheet is pulled up
# when that row is below it).
open_menu() {
  tap_node 'content-desc="Open navigation menu"' && return 0
  open_picker || fail "No account menu or sheet on Home"
  sleep 2
  tap_node 'text="Open navigation menu"' && return 0
  swipe_up
  tap_node 'text="Open navigation menu"' || fail "No account menu in Home's sheet"
}

# Home's sheet: Following's button, or a network's ("Bluesky, Switch plugin").
open_picker() {
  tap_node 'content-desc="Following"' && return 0
  tap_node ', Switch plugin"'
}

# The store, from Home's account menu.
open_store() {
  open_menu
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
wait_for 'text="Installed' 90 || { shot plugin-store-failed 0; fail "$name was not installed"; }
shot plugin-store-installed 2

# On Home's strip, from its row's menu (the lowest "More options"; the bar's is above it).
tap_node 'content-desc="More options"' last || fail "No menu on $name's row"
sleep 1
tap_node 'text="Pin next to For you"' || fail "$name cannot be pinned on Home"
sleep 1

# Its home tab, opened from the store by a tap on its box.
installed=$(installed_plugin)
[ -n "$installed" ] || fail "No box under Installed"
tap_node "text=\"$installed\"" || fail "No box for $installed"
shot plugin-home 15

# Home, where its timeline is now one of Home's, picked in the timeline picker.
# Mastodon, Bluesky and Threads share one timeline there while "Group
# microblogging services" is on, as it is at first, as in XTA.
timeline="text=\"$name\""
case "$name" in
  Mastodon | Bluesky | Threads) timeline='text="Mastodon &amp; Bluesky"' ;;
esac
back
back
open_picker || fail "No timeline picker on Home"
wait_for "$timeline" 15 || { shot plugin-strip-missing 0; fail "$name is not among Home's timelines"; }
tap_node "$timeline" || fail "No $name in Home's timeline picker"
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
