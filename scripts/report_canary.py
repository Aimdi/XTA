"""Render service-specific live-test results without publishing credentials."""
import json
from pathlib import Path
import re
import sys


def redact(text):
    text = re.sub(r"(?im)(authorization|(?:set-)?cookie|x-guest-token|auth_token|ct0)([\"']?\s*[:=]\s*)[^\r\n]+", r"\1\2[redacted]", text)
    text = re.sub(r"(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=%-]+", r"\1 [redacted]", text)
    text = re.sub(r"https?://[^\s<>\"']+", lambda m: m[0].split('?')[0].split('#')[0], text)
    return text.replace('```', "'''")


def report(service, url, outcome, raw):
    if outcome not in ('success', 'failure'):
        return f"### {service} live checks\n\nThe probe did not run; inspect workflow setup. This is not evidence of a {service} outage.\n\n[Workflow]({url})\n"
    lines, names = [], {}
    for line in raw.splitlines():
        try:
            event = json.loads(line)
        except (ValueError, TypeError):
            if line.strip():
                lines.append(line)
            continue
        if not isinstance(event, dict):
            continue
        kind = event.get('type')
        if kind == 'testStart':
            test = event.get('test', {})
            names[test.get('id')] = test.get('name', '')
        elif kind == 'error':
            lines.extend([names.get(event.get('testID'), 'Test failure'), event.get('error', ''), event.get('stackTrace', '')])
        elif kind == 'print':
            lines.append(event.get('message', ''))
        elif kind == 'testDone' and event.get('result') != 'success':
            lines.append(f"{names.get(event.get('testID'), 'Test')}: {event.get('result')}")
    details = redact('\n'.join(str(line) for line in lines))[-16000:]
    status = 'passed' if outcome == 'success' else 'failed'
    return f"### {service} live checks {status}\n\n[Workflow]({url})\n\nA failed probe may reflect a service response, rate limit, or runner connectivity; inspect the details before diagnosing an API change.\n\n```text\n{details or 'No test diagnostics were emitted.'}\n```\n"


if __name__ == '__main__':
    service, url, outcome, source, target = sys.argv[1:]
    path = Path(source)
    Path(target).write_text(report(service, url, outcome, path.read_text(errors='replace') if path.exists() else ''), encoding='utf-8')
