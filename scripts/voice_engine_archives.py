#!/usr/bin/env python3
"""Make, or check, the voice engine archives a release publishes beside its APKs.

The engine that runs downloaded voices (onnxruntime and sherpa-onnx's C API) is
left out of the APKs and downloaded with the first voice
(docs/specs/downloadable-voice-engine.md). Each archive holds the two libraries
for one processor exactly as the pub.dev package pubspec.lock names ships them.
The app pins their size and SHA-256 in lib/speech/voice_engine.dart; this
script refuses to write, or to pass, an archive whose libraries differ.

The libraries come from the pub cache when `flutter pub get` filled it, else
from pub.dev's archive of the package, checked against pubspec.lock.

Usage:
  scripts/voice_engine_archives.py <output directory>
  scripts/voice_engine_archives.py --check <directory with archives>
"""

from pathlib import Path
import hashlib
import io
import lzma
import os
import re
import sys
import tarfile
import tempfile
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "lib/speech/voice_engine.dart"
LOCK = ROOT / "pubspec.lock"

# Android's processor names and the pub.dev package carrying each one's libraries.
PACKAGES = {
    "arm64-v8a": "sherpa_onnx_android_arm64",
    "armeabi-v7a": "sherpa_onnx_android_armeabi",
    "x86_64": "sherpa_onnx_android_x86_64",
}


class Refused(Exception):
    pass


def read_pins(text):
    """The engine version and, per processor, each library's (name, bytes, sha256)."""
    version = re.search(r"const voiceEngineVersion = '([^']+)';", text)
    if version is None:
        raise Refused("lib/speech/voice_engine.dart names no voiceEngineVersion")
    library = re.compile(r"\(\s*name: '([^']+)',\s*bytes: (\d+),\s*sha256: '([0-9a-f]{64})',?\s*\)")
    pins = {}
    for block in text.split("VoiceEnginePackage(")[1:]:
        abi = re.search(r"abi: '([^']+)'", block)
        if abi is None:
            continue
        pins[abi[1]] = [(name, int(size), digest) for name, size, digest in library.findall(block)]
    if not pins or not all(pins.values()):
        raise Refused("lib/speech/voice_engine.dart pins no libraries")
    return version[1], pins


def locked_package(text, name):
    """(version, archive sha256) pubspec.lock records for the hosted package [name]."""
    block = re.search(rf"^  {re.escape(name)}:\n((?:    .*\n|      .*\n)+)", text, re.M)
    if block is None:
        raise Refused(f"pubspec.lock does not lock {name}")
    version = re.search(r'^    version: "?([^"\s]+)"?', block[1], re.M)
    digest = re.search(r'^      sha256: "?([0-9a-f]{64})"?', block[1], re.M)
    if version is None or digest is None:
        raise Refused(f"pubspec.lock gives no version or sha256 for {name}")
    return version[1], digest[1]


def sha256_of(data):
    return hashlib.sha256(data).hexdigest()


def pub_cache():
    return Path(os.environ.get("PUB_CACHE") or Path.home() / ".pub-cache")


def cached_library(package, version, abi, name):
    path = pub_cache() / "hosted/pub.dev" / f"{package}-{version}" / "android/src/main/jniLibs" / abi / name
    return path.read_bytes() if path.is_file() else None


def downloaded_libraries(package, version, archive_sha256, abi, names):
    """The libraries out of pub.dev's archive of [package], which must match pubspec.lock."""
    url = f"https://pub.dev/api/archives/{package}-{version}.tar.gz"
    with urllib.request.urlopen(url, timeout=120) as response:
        data = response.read()
    if sha256_of(data) != archive_sha256:
        raise Refused(f"{url} does not match pubspec.lock")
    found = {}
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        for name in names:
            member = archive.getmember(f"android/src/main/jniLibs/{abi}/{name}")
            found[name] = archive.extractfile(member).read()
    return found


def checked(name, data, size, digest):
    if len(data) != size or sha256_of(data) != digest:
        raise Refused(f"{name} does not match the pin in lib/speech/voice_engine.dart")
    return data


def libraries_for(abi, version, pins, lock_text):
    package = PACKAGES[abi]
    locked_version, archive_sha256 = locked_package(lock_text, package)
    if locked_version != version:
        raise Refused(f"pubspec.lock has {package} {locked_version}, the app pins {version}")
    names = [name for name, _, _ in pins]
    cached = {name: cached_library(package, version, abi, name) for name in names}
    if any(data is None for data in cached.values()):
        cached = downloaded_libraries(package, version, archive_sha256, abi, names)
    return [(name, checked(f"{abi}/{name}", cached[name], size, digest)) for name, size, digest in pins]


def archive_name(version, abi):
    return f"xta-voice-engine-sherpa-onnx-{version}-{abi}.tar.xz"


def write_archive(path, libraries):
    """A tar.xz of [libraries], the same bytes for the same input on the same xz."""
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w", format=tarfile.USTAR_FORMAT) as archive:
        for name, data in libraries:
            info = tarfile.TarInfo(name)
            info.size, info.mode, info.mtime = len(data), 0o644, 0
            archive.addfile(info, io.BytesIO(data))
    path.write_bytes(lzma.compress(buffer.getvalue(), preset=9 | lzma.PRESET_EXTREME))


def read_archive(path):
    with tarfile.open(path, mode="r:xz") as archive:
        return {member.name: archive.extractfile(member).read() for member in archive.getmembers() if member.isfile()}


def make(directory):
    version, pins = read_pins(PINS.read_text(encoding="utf-8"))
    lock_text = LOCK.read_text(encoding="utf-8")
    directory.mkdir(parents=True, exist_ok=True)
    for abi in sorted(pins):
        path = directory / archive_name(version, abi)
        write_archive(path, libraries_for(abi, version, pins[abi], lock_text))
        print(f"{path.name}: {path.stat().st_size} bytes")


def check(directory):
    version, pins = read_pins(PINS.read_text(encoding="utf-8"))
    for abi in sorted(pins):
        path = directory / archive_name(version, abi)
        if not path.is_file():
            raise Refused(f"{path.name} is missing")
        found = read_archive(path)
        for name, size, digest in pins[abi]:
            if name not in found:
                raise Refused(f"{path.name} lacks {name}")
            checked(f"{abi}/{name}", found[name], size, digest)
        print(f"{path.name}: matches the pins")


def main(arguments):
    try:
        if len(arguments) == 2 and arguments[0] == "--check":
            check(Path(arguments[1]))
        elif len(arguments) == 1 and not arguments[0].startswith("-"):
            make(Path(arguments[0]))
        else:
            raise SystemExit(__doc__)
    except Refused as refusal:
        print(f"::error::{refusal}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
