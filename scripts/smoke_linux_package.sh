#!/usr/bin/env bash
# Installs a Debian package of XTA on this machine, starts it on a virtual
# display, and fails unless its window opens and it keeps running. A second
# start must hand over to the first, leaving one process. Leaves a screenshot
# in <out-dir>. Needs root (or sudo) to install. See docs/desktop.md.
#
# Usage: scripts/smoke_linux_package.sh <package.deb> <out-dir>
set -euo pipefail

deb=$(realpath "${1:?package}")
out=${2:?output directory}
mkdir -p "$out"
sudo=$([[ $(id -u) == 0 ]] || echo sudo)

$sudo apt-get update -q >/dev/null
$sudo apt-get install -y -q "$deb" xvfb xdotool imagemagick dbus desktop-file-utils appstream >/dev/null
desktop-file-validate /usr/share/applications/com.aimdi.xta.desktop
# appstream knows the metadata's <developer> tag from 1.0 (Ubuntu 24.04).
if command -v appstreamcli >/dev/null && appstreamcli --version | grep -q ' 1\.'; then
  appstreamcli validate --no-net /usr/share/metainfo/com.aimdi.xta.metainfo.xml
fi

export HOME
HOME=$(mktemp -d)
export DISPLAY=:97 GDK_BACKEND=x11 LIBGL_ALWAYS_SOFTWARE=1
Xvfb "$DISPLAY" -screen 0 1600x1000x24 >/dev/null 2>&1 &
xvfb=$!
trap 'kill $xvfb 2>/dev/null || true; pkill -x xta 2>/dev/null || true' EXIT
sleep 2

check() {
  local log="$out/xta.log"
  xta >"$log" 2>&1 &
  local app=$!
  for _ in $(seq 1 60); do
    xdotool search --name '^XTA$' >/dev/null 2>&1 && break
    kill -0 "$app" 2>/dev/null || {
      echo "XTA exited before its window opened:" >&2
      cat "$log" >&2
      exit 1
    }
    sleep 1
  done
  xdotool search --name '^XTA$' >/dev/null 2>&1 || {
    echo "XTA's window did not open within a minute:" >&2
    cat "$log" >&2
    exit 1
  }
  sleep 10
  kill -0 "$app" 2>/dev/null || {
    echo "XTA stopped after opening its window:" >&2
    cat "$log" >&2
    exit 1
  }
  import -window root "$out/xta.png"
  xta >"$out/second.log" 2>&1 &
  sleep 8
  local running
  running=$(pgrep -x xta | wc -l)
  [[ $running == 1 ]] || {
    echo "A second start left $running copies running" >&2
    exit 1
  }
  echo "XTA $(dpkg-query -W -f '${Version}' xta) started, kept running, and handed a second start over to itself"
}

export -f check
export out
dbus-run-session -- bash -c check
