#!/usr/bin/env bash
# CI's check that videos play in the desktop app, as voice_screens.sh is the
# voices' on the emulator: a video served here (640x360 at 30 frames a second
# for four seconds, H.264 and AAC as X serves them, made with ffmpeg) is what
# XTA_NATIVE_VIDEO_CHECK names, and the app (ui/media's VideoCheck) plays it
# over its window in the viewer's player, then as a podcast plays it, a line
# in the log for each step; the last frame stays for the screenshot.
#
#   bash native/tool/video_check.sh <bin/xta-native> <out dir> [without]
#
# With "without", libmpv must be missing: the app has to say that videos go
# to the system's player, and stay up. Otherwise it needs libmpv: the video's
# frames have to be drawn and reach the screen, the video has to play to its
# end, and the podcast player has to play. A runner without a sound card hears
# nothing: mpv's null output keeps the time.
set -euo pipefail
app=${1:?the launcher, bin/xta-native}
out=${2:?a folder for the log and the screenshot}
mode=${3:-with}
mkdir -p "$out"
out=$(cd "$out" && pwd)
work=$(mktemp -d)
server=
trap '[ -z "$server" ] || kill "$server" 2> /dev/null || true; rm -rf "$work"' EXIT

# grep reads all of ldconfig's list: with -q it would stop early, and pipefail
# would take ldconfig's broken pipe for a failure.
if [ "$mode" = without ] && ldconfig -p | grep -E 'libmpv\.so\.[12] ' > /dev/null; then
  echo "libmpv is installed here: the check without it has nothing to show"
  exit 0
fi
log="$out/video-$mode.log"
fail() {
  echo "::error::$1"
  cat "$log"
  exit 1
}

ffmpeg -v error -f lavfi -i testsrc2=size=640x360:rate=30 -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 4 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -movflags +faststart "$work/check.mp4"
python3 -m http.server 8790 --bind 127.0.0.1 --directory "$work" > "$work/server.log" 2>&1 &
server=$!
for _ in $(seq 1 40); do
  curl -fs -o /dev/null http://127.0.0.1:8790/check.mp4 && break
  sleep 0.25
done

xvfb-run -a -s '-screen 0 1280x960x24' bash -c '
  env XTA_NATIVE_VIDEO_CHECK=http://127.0.0.1:8790/check.mp4 "XDG_DATA_HOME=$2/data" "XDG_CACHE_HOME=$2/cache" "$1" > "$3" 2>&1 &
  pid=$!
  for _ in $(seq 1 180); do
    grep -q "video check: done" "$3" && break
    kill -0 "$pid" 2> /dev/null || break
    sleep 1
  done
  sleep 2
  import -window root "$4"
  if kill -0 "$pid" 2> /dev/null; then echo up > "$3.state"; else echo down > "$3.state"; fi
  kill "$pid" 2> /dev/null || true
' _ "$app" "$work/home" "$log" "$out/video-$mode.png"
grep "XTA Native: video" "$log" || true

[ "$(cat "$log.state")" = up ] || fail "XTA Native did not stay up through the video check"
rm -f "$log.state"
grep -q "video check: done" "$log" || fail "The video check did not finish"
if [ "$mode" = without ]; then
  grep -q "video check: engine missing" "$log" || fail "Without libmpv the app should say that videos go to the system's player"
  echo "Without libmpv the app hands videos to the system's player, and stays up."
  exit 0
fi
grep -q "video check: engine libmpv" "$log" || fail "The app did not open libmpv"
grep -q "video check: first frame 640x360" "$log" || fail "No frame of the video was drawn"
frames=$(grep "video check: frames" "$log" | tail -1)
drawn=$(sed -n 's/.*frames \([0-9]*\) drawn.*/\1/p' <<< "$frames")
shown=$(sed -n 's/.* \([0-9]*\) shown.*/\1/p' <<< "$frames")
[ "${drawn:-0}" -ge 60 ] || fail "Too few of the video's frames were drawn: ${drawn:-0}"
[ "${shown:-0}" -ge 10 ] || fail "Too few of the video's frames reached the screen: ${shown:-0}"
grep -q "; completed$" <<< "$frames" || fail "The video did not play to its end"
heard=$(sed -n 's/.*podcast at \([0-9]*\) ms.*/\1/p' "$log" | tail -1)
[ "${heard:-0}" -ge 1000 ] || fail "The podcast player did not play"
echo "A video played in the app to its end ($drawn frames drawn, $shown on the screen), and as a podcast plays it ($heard ms in)."
