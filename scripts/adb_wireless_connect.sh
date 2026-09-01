#!/usr/bin/env bash
# Connect a physical Android phone over Wi‑Fi so Cloud agents can install/run XTA.
# On the phone: Developer options → Wireless debugging → Pair device with pairing code.
set -euo pipefail

PAIR_HOST_PORT="${1:-}"
PAIR_CODE="${2:-}"
CONNECT_HOST_PORT="${3:-}"

if [[ -z "$PAIR_HOST_PORT" || -z "$PAIR_CODE" || -z "$CONNECT_HOST_PORT" ]]; then
  cat <<'EOF'
Usage:
  scripts/adb_wireless_connect.sh <pair_host:port> <pairing_code> <connect_host:port>

Example (from Wireless debugging screen):
  scripts/adb_wireless_connect.sh 192.168.1.20:37123 123456 192.168.1.20:5555

Notes:
  - Cursor Cloud VMs have no /dev/kvm, so the local Android emulator is not usable for UI.
  - A real phone on the same reachable network (or via a tunnel) is the way to interactively
    run the app from this environment.
EOF
  exit 2
fi

adb pair "$PAIR_HOST_PORT" "$PAIR_CODE"
adb connect "$CONNECT_HOST_PORT"
adb devices -l
echo "Install with: adb install -r build/app/outputs/flutter-apk/app-debug.apk"
echo "Launch with:  adb shell am start -n fr.l3m2e.xta/.MainActivity || adb shell monkey -p \$(adb shell pm list packages | rg -o 'package:.*xta' | head -1 | cut -d: -f2) 1"
