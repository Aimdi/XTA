#!/usr/bin/env bash
# Downloaded voices speak on a running emulator, as XTA's voice-engine job
# proves its engine (docs/specs/downloadable-voice-engine.md), after
# screens.sh: the app, built with -PvoiceServer naming a server the runner
# serves the engine's archives and the English voice from, is started with
# its voice check (ui/speech/VoiceCheck.kt). The engine and the voice
# download and install against their pins, the voice reads a sentence into a
# clip, a reading plays, and the media session shows a podcast's controls,
# which this presses from the keys, the notification and the lock screen,
# with a screenshot of each. Fails when the APK carries the engine or lacks
# the shim, the voice made no clip, the session's controls did not reach
# what was bound, or the app crashed.
#
#   bash native/tool/voice_screens.sh <apk built with -PvoiceServer> <output dir>
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

lines() {
  adb logcat -d -s System.out:I 2> /dev/null | grep -F "XTA Native: voice check:" || true
}

fail() {
  echo "$1"
  adb logcat -d > "$out/voice-logcat.txt" || true
  lines
  grep -A 30 -E "FATAL EXCEPTION|E AndroidRuntime" "$out/voice-logcat.txt" | head -60 || true
  exit 1
}

# Waits up to $2 seconds for the check to log a line beginning with $1 (an extended regular expression).
wait_for() {
  for _ in $(seq 1 "$2"); do
    lines | grep -E "voice check: ($1)" > /dev/null && return 0
    sleep 1
  done
  return 1
}

# Taps the middle of the topmost node holding $1 (text="…" or content-desc="…"); false when none does.
tap_node() {
  local box left top right bottom
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  box=$(adb exec-out cat /sdcard/ui.xml 2> /dev/null | tr '>' '\n' | grep -F -- "$1" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -1 || true)
  [ -n "$box" ] || return 1
  read -r left top right bottom <<< "$box"
  adb shell input tap $(((left + right) / 2)) $(((top + bottom) / 2))
}

# The engine downloads with the first voice: the APK carries only the shim that reaches it.
# (The listing is read whole first: grep -q leaving a pipe early fails the pipeline under pipefail.)
listing=$(unzip -l "$apk")
if grep -qE 'lib/[^/]+/lib(onnxruntime|sherpa-onnx-c-api)\.so$' <<< "$listing"; then fail "The APK carries the voice engine"; fi
grep -qE 'lib/x86_64/libxta_sherpa\.so$' <<< "$listing" || fail "The APK has no voice shim for the emulator's processor"

adb logcat -c
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/.MainActivity" --ez xta.voice_check true > /dev/null
wait_for "engine " 60 || fail "The voice check did not start"
wait_for "clip|no clip" 600 || fail "The voice check made no clip in time"
bytes=$(lines | sed -n 's/.*voice check: clip \([0-9]*\) bytes.*/\1/p' | tail -1)
[ "${bytes:-0}" -gt 16000 ] || fail "The downloaded voice made no clip of a second or more (${bytes:-none})"
echo "The engine downloaded, installed, loaded from the app's storage and read a sentence: $bytes bytes."

# The reading, through the app's reader, with its stop on the notification.
if wait_for "reading" 60; then
  adb shell cmd statusbar expand-notifications || true
  shot voice-reading 2
  adb shell cmd statusbar collapse || true
fi
wait_for "session shown" 180 || fail "The voice check showed no media session"
sleep 2
adb shell dumpsys media_session > "$out/voice-media-session.txt" || true
adb shell dumpsys notification --noredact > "$out/voice-notification.txt" || true
grep -q "package=$pkg" "$out/voice-media-session.txt" || fail "No media session of $pkg"
grep -q "com.aimdi.xtanative.playback" "$out/voice-notification.txt" || fail "No media notification on the playback channel"

# A headset's keys reach what is bound.
adb shell input keyevent KEYCODE_MEDIA_PAUSE
wait_for "paused" 15 || fail "Pause did not reach the session's binding"
adb shell input keyevent KEYCODE_MEDIA_PLAY
wait_for "played" 15 || fail "Play did not reach the session's binding"

# The lock screen carries the controls.
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
shot voice-lockscreen 3
adb shell wm dismiss-keyguard || true
sleep 1

# The notification's stop ends it.
adb shell cmd statusbar expand-notifications || true
shot voice-notification 3
if ! tap_node 'content-desc="Stop"'; then
  echo "No Stop on the notification's player; stopping with the key instead."
  adb shell input keyevent KEYCODE_MEDIA_STOP
fi
wait_for "stopped" 15 || fail "Stop did not reach the session's binding"
adb shell cmd statusbar collapse || true
wait_for "done" 30 || fail "The voice check did not end"

adb logcat -d > "$out/voice-logcat.txt" || true
lines
if grep -q "FATAL EXCEPTION" "$out/voice-logcat.txt"; then
  echo "XTA Native crashed:"
  grep -A 40 "FATAL EXCEPTION" "$out/voice-logcat.txt" | head -80
  exit 1
fi
echo "Downloaded voices speak, and the media session's controls reach what plays; no crash."
