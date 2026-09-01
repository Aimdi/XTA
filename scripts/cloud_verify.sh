#!/usr/bin/env bash
# Cloud-friendly verification for XTA (Android-only Flutter app).
# Works without an emulator: analyze + unit tests + optional guest API smoke + debug APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

RUN_LIVE="${RUN_LIVE:-0}"
RUN_APK="${RUN_APK:-1}"

echo "==> fvm flutter pub get"
fvm flutter pub get

echo "==> intl_utils:generate"
fvm dart run intl_utils:generate

echo "==> flutter analyze"
fvm flutter analyze

echo "==> flutter test"
fvm flutter test

if [[ "$RUN_LIVE" == "1" ]]; then
  echo "==> live guest API smoke (x.com)"
  fvm flutter test test/live/guest_api_smoke_test.dart --name .
fi

if [[ "$RUN_APK" == "1" ]]; then
  echo "==> flutter build apk --debug"
  fvm flutter build apk --debug
  echo "APK: build/app/outputs/flutter-apk/app-debug.apk"
fi

echo "OK: cloud verification finished"
