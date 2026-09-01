#!/usr/bin/env bash
# SessionStart: make sure generated code exists before an agent starts.
# Mirrors the first two steps of scripts/cloud_verify.sh. Always exits 0 —
# a missing fvm must not stop the session.
# dart_pubspec_licenses:generate is deliberately absent: it fails under
# Flutter 3.44.4 + FVM and its output is unused (see AGENTS.md gotchas).
set -u

cd "${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}" 2>/dev/null || exit 0

if ! command -v fvm >/dev/null 2>&1; then
  echo "session-start: fvm not on PATH, skipping pub get / intl_utils codegen"
  exit 0
fi

fvm flutter pub get || echo "session-start: 'fvm flutter pub get' failed (continuing)"
fvm dart run intl_utils:generate || echo "session-start: 'intl_utils:generate' failed (continuing)"

exit 0
