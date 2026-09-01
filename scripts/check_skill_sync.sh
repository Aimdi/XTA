#!/usr/bin/env bash
# CLAUDE.md tells us skills live under both .claude/skills/ (Claude Code) and
# .grok/skills/ (Grok Build) and must be kept in sync. The two trees are
# hand-mirrored, so an edit to one side silently makes the two agents follow
# different rules on the same repo. This check turns that drift into a failure.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

CLAUDE_DIR=".claude/skills"
GROK_DIR=".grok/skills"

list_files() {
  # Relative paths only, so the two trees compare directly.
  if [[ -d "$1" ]]; then
    (cd "$1" && find . -type f | sed 's|^\./||' | LC_ALL=C sort)
  fi
}

fail() {
  echo "ERROR: $CLAUDE_DIR and $GROK_DIR are out of sync." >&2
  printf '%s\n' "$@" >&2
  echo >&2
  echo "Mirror the change to both trees (see CLAUDE.md, 'Custom Skills')." >&2
  exit 1
}

for dir in "$CLAUDE_DIR" "$GROK_DIR"; do
  [[ -d "$dir" ]] || fail "  missing skill tree: $dir"
done

problems=()

while IFS= read -r rel; do
  [[ -n "$rel" ]] || continue
  problems+=("  only in $CLAUDE_DIR: $rel")
done < <(LC_ALL=C comm -23 <(list_files "$CLAUDE_DIR") <(list_files "$GROK_DIR"))

while IFS= read -r rel; do
  [[ -n "$rel" ]] || continue
  problems+=("  only in $GROK_DIR: $rel")
done < <(LC_ALL=C comm -13 <(list_files "$CLAUDE_DIR") <(list_files "$GROK_DIR"))

while IFS= read -r rel; do
  [[ -n "$rel" ]] || continue
  if ! cmp -s "$CLAUDE_DIR/$rel" "$GROK_DIR/$rel"; then
    problems+=("  content differs: $CLAUDE_DIR/$rel vs $GROK_DIR/$rel")
  fi
done < <(LC_ALL=C comm -12 <(list_files "$CLAUDE_DIR") <(list_files "$GROK_DIR"))

if [[ ${#problems[@]} -gt 0 ]]; then
  fail "${problems[@]}"
fi
