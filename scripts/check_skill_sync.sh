#!/usr/bin/env bash
# Skills live only in .claude/skills/ (the Grok and Codex mirrors were removed),
# so there is no second tree to keep in sync. The name stays because CI and
# every release build (scripts/release_integrity.py) run this script. It now
# fails when a skill would not load: a missing SKILL.md, or frontmatter whose
# name does not match the skill's folder or that has no description.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SKILLS_DIR=".claude/skills"
problems=()

[[ -d "$SKILLS_DIR" ]] || { echo "ERROR: missing skill tree: $SKILLS_DIR" >&2; exit 1; }

for dir in "$SKILLS_DIR"/*/; do
  name="$(basename "$dir")"
  manifest="$dir/SKILL.md"
  if [[ ! -f "$manifest" ]]; then
    problems+=("  $name: no SKILL.md")
    continue
  fi
  [[ "$(head -n 1 "$manifest")" == "---" ]] || problems+=("  $name: SKILL.md does not start with frontmatter")
  frontmatter="$(awk 'NR == 1 { next } /^---$/ { exit } { print }' "$manifest")"
  grep -qx "name: $name" <<<"$frontmatter" || problems+=("  $name: frontmatter name is not \"$name\"")
  grep -q '^description: ' <<<"$frontmatter" || problems+=("  $name: frontmatter has no description")
done

if [[ ${#problems[@]} -gt 0 ]]; then
  echo "ERROR: $SKILLS_DIR has skills that would not load:" >&2
  printf '%s\n' "${problems[@]}" >&2
  exit 1
fi
echo "OK: $(find "$SKILLS_DIR" -mindepth 2 -maxdepth 2 -name SKILL.md | wc -l) skills in $SKILLS_DIR"
