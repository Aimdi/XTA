#!/usr/bin/env python3
"""Fail when a release APK carries a native library nothing on Android loads,
or stores one compressed.

android/app/build.gradle leaves the unloaded ones out of the APK; this catches
a plugin update or a Gradle change that brings one back. A compressed library
is unpacked at install, so it makes the installed app bigger than the APK.

Usage: scripts/check_apk_contents.py <directory with APKs>
"""

from pathlib import Path
import sys
import zipfile


UNLOADED_LIBRARIES = {"libsqlite3.so", "libsherpa-onnx-cxx-api.so"}


def native_libraries(archive):
    return [info for info in archive.infolist() if info.filename.startswith("lib/") and info.filename.endswith(".so")]


def unloaded_libraries(apk):
    with zipfile.ZipFile(apk) as archive:
        native = {Path(info.filename).name for info in native_libraries(archive)}
    return sorted(native & UNLOADED_LIBRARIES)


def compressed_libraries(apk):
    with zipfile.ZipFile(apk) as archive:
        return sorted(info.filename for info in native_libraries(archive) if info.compress_type != zipfile.ZIP_STORED)


def problems(apk):
    found = []
    if unloaded := unloaded_libraries(apk):
        found.append(f"{apk.name} carries {', '.join(unloaded)}")
    if compressed := compressed_libraries(apk):
        found.append(f"{apk.name} stores {', '.join(compressed)} compressed")
    return found


def check(directory):
    apks = sorted(Path(directory).glob("*.apk"))
    if not apks:
        return ["No APKs found"]
    return [problem for apk in apks for problem in problems(apk)]


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    failures = check(sys.argv[1])
    for failure in failures:
        print(f"::error::{failure}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
