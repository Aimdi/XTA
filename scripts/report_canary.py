"""Render service-specific live-test results without publishing credentials."""
import json
from pathlib import Path
import re
import sys


_INFRASTRUCTURE_MARKERS = (
    "this run proves nothing",
    "probe did not run",
    "building native assets failed",
    "hash of downloaded file",
    "failed to compile",
    "couldn't find ondemand file index",
    "could not find ondemand file index",
)


def redact(text):
    text = re.sub(
        r"(?im)(authorization|(?:set-)?cookie|x-guest-token|auth_token|ct0)([\"']?\s*[:=]\s*)[^\r\n]+",
        r"\1\2[redacted]",
        text,
    )
    text = re.sub(
        r"(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=%-]+",
        r"\1 [redacted]",
        text,
    )
    text = re.sub(
        r"https?://[^\s<>\"']+",
        lambda m: m[0].split("?")[0].split("#")[0],
        text,
    )
    return text.replace("~~~", "'''").replace(String.fromCharCode(96) * 3, "'''")


def classify(outcome, raw):
    if outcome == "success":
        return "success"
    if outcome != "failure":
        return "infrastructure"
    lower = raw.lower()
    if any(marker in lower for marker in _INFRASTRUCTURE_MARKERS):
        return "infrastructure"
    return "service"


def _details(raw):
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
        kind = event.get("type")
        if kind == "testStart":
            test = event.get("test", {})
            names[test.get("id")] = test.get("name", "")
        elif kind == "error":
            lines.extend(
                [
                    names.get(event.get("testID"), "Test failure"),
                    event.get("error", ""),
                    event.get("stackTrace", ""),
                ]
            )
        elif kind == "print":
            lines.append(event.get("message", ""))
        elif kind == "testDone" and event.get("result") != "success":
            lines.append(
                f"{names.get(event.get('testID'), 'Test')}: {event.get('result')}"
            )
    return redact("\n".join(str(line) for line in lines))[-16000:]


def report(service, url, outcome, raw):
    classification = classify(outcome, raw)
    if outcome not in ("success", "failure"):
        return (
            f"### {service} live checks\n\n"
            f"The probe did not run; inspect workflow setup. "
            f"This is not evidence of a {service} outage.\n\n"
            f"[Workflow]({url})\n"
        )

    details = _details(raw)
    status = "passed" if outcome == "success" else "failed"
    if classification == "infrastructure":
        guidance = (
            "The probe infrastructure failed before it could establish a "
            f"{service} service/API failure. Fix or retry the probe before "
            "diagnosing the service."
        )
    else:
        guidance = (
            "A failed probe reached the service and may reflect an API change, "
            "rate limit, authentication change, or service response. Inspect "
            "the details before changing the app."
        )
    return (
        f"### {service} live checks {status}\n\n"
        f"[Workflow]({url})\n\n"
        f"{guidance}\n\n"
        f"Classification: **{classification}**\n\n"
        f"~~~text\n"
        f"{details or 'No test diagnostics were emitted.'}\n"
        f"~~~\n"
    )


if __name__ == "__main__":
    service, url, outcome, source, target = sys.argv[1:]
    path = Path(source)
    raw = path.read_text(errors="replace") if path.exists() else ""
    Path(target).write_text(
        report(service, url, outcome, raw),
        encoding="utf-8",
    )
    print(classify(outcome, raw))
