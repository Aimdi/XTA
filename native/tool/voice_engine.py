#!/usr/bin/env python3
"""The voice engine XTA Native's downloaded voices read with, for CI.

  python3 -I native/tool/voice_engine.py desktop <out dir>
      The Linux build's engine, as XTA's Linux bundle carries it: pub.dev's
      sherpa_onnx_linux (the version VoiceEngines.VERSION names), its two
      libraries checked against VoiceEngines.LINUX_X64's pins, and the app's
      JNI shim (services/src/main/c/xta_sherpa.c) built beside them with cc
      against $JAVA_HOME's headers, needing no glibc newer than the engine's.
      The desktop build carries <out dir> under lib/app/resources/voice-engine
      when it is native/desktop/build/app-resources/linux-x64/voice-engine.

  python3 -I native/tool/voice_engine.py check <dir>
      Every Android archive in <dir> (scripts/voice_engine_archives.py makes
      them, as XTA's releases publish them) holds the libraries
      VoiceEngines.PACKAGES pins, so the app installs what is published.

The libraries come from the pub cache when one holds the package, else from
pub.dev's archive of it, checked against the repository's pubspec.lock.
"""

from pathlib import Path
import hashlib
import io
import os
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.request

NATIVE = Path(__file__).resolve().parents[1]
ROOT = NATIVE.parent
PINS = NATIVE / "services/src/main/kotlin/com/aimdi/xtanative/services/speech/VoiceEngine.kt"
SHIM = NATIVE / "services/src/main/c/xta_sherpa.c"
LOCK = ROOT / "pubspec.lock"
LIBRARIES = ["libonnxruntime.so", "libsherpa-onnx-c-api.so"]
NEWEST_GLIBC = (2, 17)


class Refused(Exception):
    pass


def read_pins(text):
    """The engine's version, and per package (Android's processor names, linux-x64) each library's (name, bytes, sha256)."""
    version = re.search(r'const val VERSION = "([^"]+)"', text)
    if version is None:
        raise Refused(f"{PINS.name} names no VERSION")
    if [name for name in re.findall(r'VoiceEngineLibrary\("([^"]+)"', text)] != LIBRARIES:
        raise Refused(f"{PINS.name} no longer pins {LIBRARIES} in that order")
    package = re.compile(
        r'VoiceEnginePackage\(\s*"([^"]+)",\s*[\d_]+,\s*libraries\(\s*([\d_]+)L to "([0-9a-f]{64})",\s*([\d_]+)L to "([0-9a-f]{64})",?\s*\)'
    )
    pins = {
        found[0]: list(zip(LIBRARIES, (int(found[1].replace("_", "")), int(found[3].replace("_", ""))), (found[2], found[4])))
        for found in package.findall(text)
    }
    if "linux-x64" not in pins or len(pins) < 2:
        raise Refused(f"{PINS.name} pins no engine")
    return version[1], pins


def sha256_of(data):
    return hashlib.sha256(data).hexdigest()


def checked(where, data, size, digest):
    if len(data) != size or sha256_of(data) != digest:
        raise Refused(f"{where} does not match its pin in {PINS.name}")
    return data


def locked_package(name):
    """(version, archive sha256) the repository's pubspec.lock records for the hosted package [name]."""
    block = re.search(rf"^  {re.escape(name)}:\n((?:    .*\n|      .*\n)+)", LOCK.read_text(encoding="utf-8"), re.M)
    version = block and re.search(r'^    version: "?([^"\s]+)"?', block[1], re.M)
    digest = block and re.search(r'^      sha256: "?([0-9a-f]{64})"?', block[1], re.M)
    if not version or not digest:
        raise Refused(f"pubspec.lock does not lock {name}")
    return version[1], digest[1]


def linux_libraries(version):
    """The Linux x64 libraries of sherpa_onnx_linux [version], from the pub cache or pub.dev's archive."""
    package = f"sherpa_onnx_linux-{version}"
    cache = Path(os.environ.get("PUB_CACHE") or Path.home() / ".pub-cache") / "hosted/pub.dev" / package / "linux/x64"
    if all((cache / name).is_file() for name in LIBRARIES):
        return {name: (cache / name).read_bytes() for name in LIBRARIES}
    locked, digest = locked_package("sherpa_onnx_linux")
    if locked != version:
        raise Refused(f"pubspec.lock has sherpa_onnx_linux {locked}, the app pins {version}")
    url = f"https://pub.dev/api/archives/{package}.tar.gz"
    with urllib.request.urlopen(url, timeout=300) as response:
        data = response.read()
    if sha256_of(data) != digest:
        raise Refused(f"{url} does not match pubspec.lock")
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        return {name: archive.extractfile(archive.getmember(f"linux/x64/{name}")).read() for name in LIBRARIES}


def java_home():
    home = os.environ.get("JAVA_HOME")
    if not home:
        javac = shutil.which("javac")
        home = str(Path(javac).resolve().parents[1]) if javac else ""
    if not (Path(home) / "include/jni.h").is_file():
        raise Refused("no JDK with include/jni.h: set JAVA_HOME")
    return Path(home)


def build_shim(out):
    jdk = java_home()
    target = out / "libxta_sherpa.so"
    subprocess.run(
        ["cc", "-O2", "-fPIC", "-shared", "-Wall", "-Wextra", f"-I{jdk / 'include'}", f"-I{jdk / 'include/linux'}",
         "-o", str(target), str(SHIM), "-Wl,--no-as-needed", "-ldl"],
        check=True,
    )
    subprocess.run(["strip", "--strip-unneeded", str(target)], check=False)
    return target


def newest_glibc(library):
    """The newest glibc version [library] asks for, or None where objdump is missing."""
    if shutil.which("objdump") is None:
        return None
    symbols = subprocess.run(["objdump", "-T", str(library)], check=True, capture_output=True, text=True).stdout
    versions = [tuple(int(part) for part in found.split(".")) for found in re.findall(r"GLIBC_([0-9]+(?:\.[0-9]+)+)", symbols)]
    return max(versions, default=(0,))


def desktop(out):
    version, pins = read_pins(PINS.read_text(encoding="utf-8"))
    libraries = linux_libraries(version)
    out.mkdir(parents=True, exist_ok=True)
    for name, size, digest in pins["linux-x64"]:
        (out / name).write_bytes(checked(f"linux-x64/{name}", libraries[name], size, digest))
    shim = build_shim(out)
    needs = newest_glibc(shim)
    if needs is not None and needs > NEWEST_GLIBC:
        raise Refused(f"{shim.name} needs glibc {'.'.join(map(str, needs))}, newer than the engine's")
    for path in sorted(out.iterdir()):
        print(f"{path.name}: {path.stat().st_size} bytes")


def check(directory):
    version, pins = read_pins(PINS.read_text(encoding="utf-8"))
    for abi in sorted(abi for abi in pins if abi != "linux-x64"):
        path = directory / f"xta-voice-engine-sherpa-onnx-{version}-{abi}.tar.xz"
        if not path.is_file():
            raise Refused(f"{path.name} is missing")
        with tarfile.open(path, mode="r:xz") as archive:
            found = {Path(member.name).name: archive.extractfile(member).read() for member in archive.getmembers() if member.isfile()}
        for name, size, digest in pins[abi]:
            if name not in found:
                raise Refused(f"{path.name} lacks {name}")
            checked(f"{abi}/{name}", found[name], size, digest)
        print(f"{path.name}: matches the app's pins")


def main(arguments):
    try:
        if len(arguments) == 2 and arguments[0] == "desktop":
            desktop(Path(arguments[1]))
        elif len(arguments) == 2 and arguments[0] == "check":
            check(Path(arguments[1]))
        else:
            raise SystemExit(__doc__)
    except (Refused, subprocess.CalledProcessError) as refusal:
        print(f"::error::{refusal}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
