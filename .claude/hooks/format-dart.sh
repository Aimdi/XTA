#!/usr/bin/env bash
# PostToolUse (Edit|Write): format the touched file when it is Dart. No-op otherwise.
# Deliberately single-file and no `flutter analyze` — this runs on every edit.
set -u

command -v jq >/dev/null 2>&1 || exit 0

file=$(jq -r '.tool_response.filePath // .tool_input.file_path // empty' 2>/dev/null)
case "$file" in
  *.dart) ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

case "$file" in
  */lib/generated/* | */lib/oss_licenses.dart) exit 0 ;;
esac

command -v fvm >/dev/null 2>&1 || exit 0
fvm dart format "$file" >/dev/null 2>&1 || true

exit 0
