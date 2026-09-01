#!/usr/bin/env python3
"""Merges conflicted ARB files by taking the union of their keys.

Every feature branch adds its own strings to all 29 locale files, so git sees a
conflict in each one even though the changes never overlap. Keys added by only
one side are kept, keys deleted by one side stay deleted, and a key both sides
changed to a different value is left as a real conflict for a human.

Two entry points:

  merge driver  merge_arb_conflicts.py %O %A %B %P
                git passes the ancestor, our and their versions as temp files;
                the result goes into %A. Registered by
                scripts/setup_git_merge_drivers.sh and wired up by .gitattributes.

  manual        merge_arb_conflicts.py
                reads the conflicting sides of every unmerged ARB out of the
                index (:1 ancestor, :2 ours, :3 theirs) and stages the result.

Output keeps the repo's ARB shape: keys sorted, each followed by its "@key"
metadata, so a merged file needs no l10n.py pass to stay canonical.
"""

import json
import subprocess
import sys
from pathlib import Path

MISSING = object()


def read(path: Path) -> str:
    try:
        return path.read_text(encoding='utf-8')
    except OSError:
        return ''


def parse(text):
    """The ARB as a dict, or None if this side is absent or not valid JSON."""
    if not text or not text.strip():
        return None
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return None
    return data if isinstance(data, dict) else None


def content(data):
    return {k: v for k, v in data.items() if not k.startswith('@')}


def resolve(base, ours, theirs):
    """Three-way merge of the content keys; returns (merged, conflicting keys)."""
    merged, conflicts = {}, []

    for key in sorted(set(ours) | set(theirs)):
        b, o, t = base.get(key, MISSING), ours.get(key, MISSING), theirs.get(key, MISSING)
        if o == t or t == b:
            value = o
        elif o == b:
            value = t
        else:
            conflicts.append(key)
            value = t
        if value is not MISSING:
            merged[key] = value

    return merged, conflicts


def with_metadata(merged, ours, theirs):
    """Re-emit each surviving key followed by its "@key" metadata, sorted."""
    out = {}
    for key in sorted(merged):
        out[key] = merged[key]
        meta = f'@{key}'
        if meta in theirs:
            out[meta] = theirs[meta]
        elif meta in ours:
            out[meta] = ours[meta]
    return out


def merge(base, ours, theirs):
    """Merged ARB text and the conflicting keys, or (None, []) if unmergeable."""
    if ours is None or theirs is None:
        return None, []

    merged, conflicts = resolve(content(base or {}), content(ours), content(theirs))
    result = with_metadata(merged, ours, theirs)
    return json.dumps(result, ensure_ascii=False, indent=2) + '\n', conflicts


def report(path, conflicts):
    keys = ', '.join(conflicts[:5]) + ('…' if len(conflicts) > 5 else '')
    print(f'{path}: {len(conflicts)} key(s) changed on both sides: {keys}', file=sys.stderr)


def run_as_driver(argv) -> int:
    ancestor, current, other = (Path(p) for p in argv[:3])
    path = argv[3] if len(argv) > 3 else str(current)

    text, conflicts = merge(
        parse(read(ancestor)),
        parse(read(current)),
        parse(read(other)),
    )
    if text is None:
        print(f'{path}: not valid JSON on both sides, leaving the conflict', file=sys.stderr)
        return 1

    current.write_text(text, encoding='utf-8')
    if conflicts:
        report(path, conflicts)
        return 1
    return 0


def stage(number: int, path: str):
    result = subprocess.run(['git', 'show', f':{number}:{path}'], capture_output=True, text=True)
    return parse(result.stdout) if result.returncode == 0 else None


def run_on_index() -> int:
    conflicted = subprocess.run(
        ['git', 'diff', '--name-only', '--diff-filter=U'], capture_output=True, text=True, check=True
    ).stdout.split()

    arbs = [p for p in conflicted if p.startswith('lib/l10n/') and p.endswith('.arb')]
    if not arbs:
        print('no conflicted ARB files')
        return 0

    unresolved = 0
    for path in arbs:
        text, conflicts = merge(stage(1, path), stage(2, path), stage(3, path))
        if text is None:
            print(f'skipping {path}: missing a side', file=sys.stderr)
            unresolved += 1
            continue

        Path(path).write_text(text, encoding='utf-8')
        if conflicts:
            report(path, conflicts)
            unresolved += 1
            continue
        subprocess.run(['git', 'add', path], check=True)

    print(f'merged {len(arbs) - unresolved} of {len(arbs)} ARB files')
    return 1 if unresolved else 0


def main(argv) -> int:
    return run_as_driver(argv) if len(argv) >= 3 else run_on_index()


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
