#!/usr/bin/env bash
# PreToolUse (Edit|Write) guard for AGENTS.md "Never bump pinned deps".
# Denies only when a load-bearing pin actually changes:
#   - dart_twitter_api in pubspec.yaml
#   - the dependency_overrides block in pubspec.yaml
#   - the Flutter version in pubspec.yaml (environment:) and .fvmrc
# Any missing tool / unparseable payload allows the edit through.
set -u

TMP=$(mktemp -d 2>/dev/null) || exit 0
trap 'rm -rf "$TMP"' EXIT

payload=$(cat)
command -v jq >/dev/null 2>&1 || exit 0

deny() {
  jq -n --arg r "$1" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
  exit 0
}

jq_get() { printf '%s' "$payload" | jq -r "$1" 2>/dev/null; }

file=$(jq_get '.tool_input.file_path // empty')
[ -n "$file" ] || exit 0
[ -f "$file" ] || exit 0

case "$(basename "$file")" in
  pubspec.yaml | .fvmrc) ;;
  *) exit 0 ;;
esac

# NEW = text being introduced, OLD = text being replaced, baseline = file on disk.
if [ "$(jq_get '.tool_name // empty')" = "Write" ]; then
  jq_get '.tool_input.content // empty' >"$TMP/new"
  cp "$file" "$TMP/old"
else
  jq_get '[(.tool_input.old_string // empty)] + [((.tool_input.edits // [])[].old_string)] | join("\n")' >"$TMP/old"
  jq_get '[(.tool_input.new_string // empty)] + [((.tool_input.edits // [])[].new_string)] | join("\n")' >"$TMP/new"
fi

trim() { sed 's/[[:space:]]*$//; s/^[[:space:]]*//' "$1" | sed '/^$/d'; }
trim "$TMP/old" >"$TMP/old.t"
trim "$TMP/new" >"$TMP/new.t"

# .fvmrc holds a single value, so compare the value rather than the line —
# reformatting the JSON must stay allowed.
if [ "$(basename "$file")" = ".fvmrc" ]; then
  fvm_version() { grep -oE '"flutter"[[:space:]]*:[[:space:]]*"[^"]*"' "$1" | sed 's/.*"\([^"]*\)"$/\1/' | head -n1; }
  pinned=$(fvm_version "$file")
  [ -n "$pinned" ] || exit 0
  proposed=$(fvm_version "$TMP/new")
  if [ -n "$proposed" ] && [ "$proposed" != "$pinned" ]; then
    deny "Blocked: this edit changes the pinned Flutter version in .fvmrc ($pinned -> $proposed). AGENTS.md marks it load-bearing. Ask the user before touching it."
  fi
  if [ -z "$proposed" ] && [ -n "$(fvm_version "$TMP/old")" ]; then
    deny "Blocked: this edit removes the pinned Flutter version ($pinned) from .fvmrc. AGENTS.md marks it load-bearing. Ask the user before touching it."
  fi
  # An Edit can match the bare version alone, with no "flutter" key in either
  # side — then fvm_version finds nothing and the checks above see nothing.
  if grep -Fq -- "$pinned" "$TMP/old" && ! grep -Fq -- "$pinned" "$TMP/new"; then
    deny "Blocked: this edit replaces the pinned Flutter version ($pinned) in .fvmrc. AGENTS.md marks it load-bearing. Ask the user before touching it."
  fi
  exit 0
fi

# pubspec.yaml: every non-comment line of the pinned regions, trimmed.
pinned_lines() {
  awk '
    /^dependency_overrides:/ { blk = 1; print; next }
    blk && /^[^[:space:]#]/ { blk = 0 }
    blk && NF && $0 !~ /^[[:space:]]*#/ { print }
    /^[[:space:]]*dart_twitter_api:/ { print }
    /^[[:space:]]*flutter:[[:space:]]*[0-9]/ { print }
  ' "$file" | sed 's/[[:space:]]*$//; s/^[[:space:]]*//' | sed '/^$/d'
}

pinned_lines >"$TMP/pins"
[ -s "$TMP/pins" ] || exit 0

# 1. A pinned line that is being removed or rewritten.
while IFS= read -r pin; do
  grep -Fxq -- "$pin" "$TMP/old.t" || continue
  grep -Fxq -- "$pin" "$TMP/new.t" && continue
  deny "Blocked: this edit changes the load-bearing pin \"$pin\" in $(basename "$file"). AGENTS.md forbids bumping pinned deps (dart_twitter_api, dependency_overrides, Flutter version). Ask the user before touching it."
done <"$TMP/pins"

# 2. A pinned key introduced with a different value without the old line being
#    replaced. Only for keys that occur once in the file — a key such as `intl`
#    also appears outside dependency_overrides and must not false-positive.
trim "$file" >"$TMP/base.t"
key_re() { printf '^%s[[:space:]]*:' "$(printf '%s' "$1" | sed 's/[][\.*^$/]/\\&/g')"; }
while IFS= read -r pin; do
  key=${pin%%:*}
  [ -n "$key" ] || continue
  re=$(key_re "$key")
  [ "$(grep -Ec "$re" "$TMP/base.t")" = "1" ] || continue
  grep -Eq "$re" "$TMP/new.t" || continue
  grep -Fxq -- "$pin" "$TMP/new.t" && continue
  grep -Eq "$re" "$TMP/old.t" && continue
  deny "Blocked: this edit introduces a different value for the load-bearing pin \"$pin\" in $(basename "$file"). AGENTS.md forbids bumping pinned deps (dart_twitter_api, dependency_overrides, Flutter version). Ask the user before touching it."
done <"$TMP/pins"

exit 0
