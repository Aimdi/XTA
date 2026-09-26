#!/usr/bin/env python3
"""Vendor the two explicitly pinned design skills from local upstream checkouts.

Usage: python3 scripts/vendor_design_skills.py /path/to/impeccable /path/to/uipro
No network, package installation, hooks, or upstream installer execution.
"""
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PINS = (
    ('impeccable', 'pbakaus/impeccable', '9d715cc4f5564a990ca8345abfdd5df6dc9b41c8', '.agents/skills/impeccable'),
    ('ui-ux-pro-max', 'nextlevelbuilder/ui-ux-pro-max-skill', 'dcc40ff5133ef78276117db0cc34e7b83cc8aeba', '.claude/skills/ui-ux-pro-max'),
)
HOSTS = ('.agents', '.claude', '.grok')
PREFACE = '''
## XTA project adaptation (read first)

Read AGENTS.md, CLAUDE.md and docs/xta-design-skills.md from the repository root.
They override generic upstream design advice. This is Android-only Flutter:
refine existing layouts, preserve features and true-black support, and retain
48 x 48 logical-pixel touch targets. Use Flutter guidance, not a web rewrite.
For this repo-local installation run examples from the repository root; resolve
`.agents/skills/...` against that root when your working directory differs.
Do not change approval settings, install hooks, or modify frozen API/DB code.

---
'''
SECTION = '''

## Installed design skills

For UI layout, compactness, placement, and visual hierarchy work, read
`docs/xta-design-skills.md` first. The installed `impeccable` and `ui-ux-pro-max`
skills are available under `.agents/skills/` (Codex), `.claude/skills/` (Claude
Code), and `.grok/skills/` (Grok Build). Keep the three copies of these two skills
identical; `python3 scripts/check_design_skills.py` verifies them, in addition
to the existing `bash scripts/check_skill_sync.sh` guardrail.

Use Impeccable's critique/distill/layout workflow and UI UX Pro Max's Flutter
stack guidance to refine existing XTA surfaces. Preserve features, true-black
support, native conventions and accessible touch targets. These skills do not
override any existing hard rule above, authorize a redesign, or authorize a
merge/release. No automatic hooks are installed.
'''

def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    planned = []
    for pin, argument in zip(PINS, sys.argv[1:]):
        name, repo, commit, relative = pin
        checkout = Path(argument).resolve()
        actual = subprocess.check_output(['git', '-C', str(checkout), 'rev-parse', 'HEAD'], text=True).strip()
        if actual != commit:
            raise SystemExit(f'{repo}: expected {commit}, found {actual}')
        source = checkout / relative
        if not (source / 'SKILL.md').is_file():
            raise SystemExit(f'Missing upstream skill: {source}')
        # No symlink may import a path outside the pinned upstream checkout.
        for path in source.rglob('*'):
            if path.is_symlink() and not path.resolve().is_relative_to(checkout):
                raise SystemExit(f'Unsafe external symlink: {path}')
        for host in HOSTS:
            target = ROOT / host / 'skills' / name
            if target.exists() or target.is_symlink():
                raise SystemExit(f'Refusing to overwrite existing skill: {target}')
        licenses = [p for p in checkout.iterdir() if p.is_file() and (p.name.startswith('LICENSE') or p.name.startswith('NOTICE'))]
        if not licenses:
            raise SystemExit(f'No upstream license found: {repo}')
        planned.append((pin, source, licenses))
    for pin, source, licenses in planned:
        name, repo, commit, relative = pin
        canonical = ROOT / '.agents' / 'skills' / name
        shutil.copytree(source, canonical, symlinks=False, ignore=shutil.ignore_patterns('__pycache__', '*.pyc', '.DS_Store'))
        original = (canonical / 'SKILL.md').read_text(encoding='utf-8')
        if not original.startswith('---\n') or '\n---\n' not in original[4:]:
            raise SystemExit(f'Invalid manifest: {name}')
        boundary = original.index('\n---\n', 4) + 5
        adapted = original[:boundary] + PREFACE + original[boundary:]
        if name == 'ui-ux-pro-max':
            adapted = adapted.replace('${CLAUDE_PLUGIN_ROOT}/.claude/skills/ui-ux-pro-max/', '.agents/skills/ui-ux-pro-max/')
        (canonical / 'SKILL.md').write_text(adapted, encoding='utf-8')
        (canonical / 'UPSTREAM_SKILL.md').write_text(original, encoding='utf-8')
        (canonical / 'UPSTREAM.md').write_text(f'# Upstream provenance\n\nRepository: https://github.com/{repo}\nCommit: {commit}\nSource: {relative}\n\nVendored skill files, references and helpers. UPSTREAM_SKILL.md is the original\nmanifest. SKILL.md adds the XTA brief and, for UI UX Pro Max, adapts plugin-root\ncommand examples to the repository-local .agents path. No runtime hooks installed.\n', encoding='utf-8')
        for license_file in licenses:
            shutil.copy2(license_file, canonical / license_file.name)
        for host in HOSTS[1:]:
            shutil.copytree(canonical, ROOT / host / 'skills' / name)
        print(f'Installed {name} at {commit}: {sum(p.is_file() for p in canonical.rglob("*"))} files per host')
    for filename in ('AGENTS.md', 'CLAUDE.md'):
        path = ROOT / filename
        text = path.read_text(encoding='utf-8')
        if '## Installed design skills' not in text:
            path.write_text(text.rstrip() + SECTION, encoding='utf-8')

if __name__ == '__main__':
    main()
