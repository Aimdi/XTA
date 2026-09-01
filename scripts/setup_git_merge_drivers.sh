#!/usr/bin/env bash
# Registers the merge drivers .gitattributes refers to. Git stores these in
# .git/config, so every fresh clone has to run this once.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"

git config merge.arb.name "union of ARB localization keys"
git config merge.arb.driver \
  'python3 "$(git rev-parse --show-toplevel)/merge_arb_conflicts.py" %O %A %B %P'

echo "==> merge driver 'arb' registered for $root"
