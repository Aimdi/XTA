#!/usr/bin/env python3
"""Fail when a release APK carries a native library nothing on Android loads.

android/app/build.gradle leaves these out of the APK; this catches a plugin
update or a Gradle change that brings one back.

Usage: scripts/check_apk_contents.py <directory with APKs>
"""

from pathlib import Path
import sys
import zipfile


UNLOADED_LIBRARIES = {"libsqlite3.so", "libsherpa-onnx-cxx-api.so"}


def unloaded_libraries(apk):
    with zipfile.ZipFile(apk) as archive:
        native = {Path(name).name for name in archive.namelist() if name.startswith("lib/")}
    return sorted(native & UNLOADED_LIBRARIES)


def check(directory):
    apks = sorted(Path(directory).glob("*.apk"))
    if not apks:
        return ["No APKs found"]
    return [f"{apk.name} carries {', '.join(found)}" for apk in apks if (found := unloaded_libraries(apk))]


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    failures = check(sys.argv[1])
    for failure in failures:
        print(f"::error::{failure}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
