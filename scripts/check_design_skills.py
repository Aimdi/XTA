#!/usr/bin/env python3
"""Validate complete, synchronized repository-local design skill installations."""
import ast
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
NAMES = ('impeccable', 'ui-ux-pro-max')
REQUIRED = {
    'impeccable': ('SKILL.md', 'UPSTREAM.md', 'UPSTREAM_SKILL.md', 'reference/critique.md', 'reference/distill.md', 'reference/layout.md', 'reference/operate.md', 'reference/craft-floor.md', 'reference/audit.native.md', 'scripts/impeccable', 'scripts/VERSION'),
    'ui-ux-pro-max': ('SKILL.md', 'UPSTREAM.md', 'UPSTREAM_SKILL.md', 'references/quick-reference.md', 'references/pro-rules.md', 'scripts/search.py', 'data/stacks/flutter.csv'),
}

def inventory(folder):
    result = {}
    for path in sorted(folder.rglob('*')):
        if '__pycache__' in path.parts or path.suffix == '.pyc':
            continue
        if path.is_symlink():
            raise ValueError(f'Unexpected installed symlink: {path}')
        if path.is_file():
            result[str(path.relative_to(folder))] = (hashlib.sha256(path.read_bytes()).hexdigest(), bool(path.stat().st_mode & 0o111))
    return result

def main():
    for name in NAMES:
        canonical = ROOT / '.agents' / 'skills' / name
        for relative in REQUIRED[name]:
            if not (canonical / relative).is_file():
                raise ValueError(f'Missing required resource: {name}/{relative}')
        if not list(canonical.glob('LICENSE*')):
            raise ValueError(f'Missing license: {name}')
        text = (canonical / 'SKILL.md').read_text(encoding='utf-8')
        if not re.search(r'^name: ' + re.escape(name) + r'$', text, re.M):
            raise ValueError(f'Wrong manifest name: {name}')
        if 'docs/xta-design-skills.md' not in text:
            raise ValueError(f'Missing XTA guardrails: {name}')
        expected = inventory(canonical)
        for host in ('.claude', '.grok'):
            if inventory(ROOT / host / 'skills' / name) != expected:
                raise ValueError(f'Design skill drift: {host}/{name}')
        for path in canonical.rglob('*.py'):
            ast.parse(path.read_text(encoding='utf-8'), filename=str(path))
        print(f'PASS {name}: complete, licensed, syntax-checked; {len(expected)} files identical across 3 hosts')
    subprocess.run(['sh', '-n', str(ROOT / '.agents/skills/impeccable/scripts/impeccable')], check=True)
    script = ROOT / '.agents/skills/ui-ux-pro-max/scripts/search.py'
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    output = subprocess.check_output([sys.executable, str(script), 'compact spacing', '--stack', 'flutter'], cwd=ROOT, env=env, text=True, timeout=30)
    if not output.strip() or 'flutter' not in output.lower():
        raise ValueError('Flutter search did not produce identifiable Flutter output')
    print(output)
    print('PASS local Flutter search; no Android runtime/build was run')

if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(f'Design skill validation failed: {error}')
