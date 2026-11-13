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

  python3 -I native/tool/voice_engine.py archives <out dir>
      The engine Android downloads with the first voice, one archive per
      processor (xta-voice-engine-sherpa-onnx-<version>-<abi>.tar.xz), which a
      prerelease publishes beside its APK: the two libraries of pub.dev's
      sherpa_onnx_android_* package for that processor, checked against
      VoiceEngines.PACKAGES' pins. The same bytes for the same libraries on
      the same xz.

  python3 -I native/tool/voice_engine.py check <dir>
      Every Android archive in <dir> holds the libraries VoiceEngines.PACKAGES
      pins, so the app installs what is published.

The libraries come from the pub cache when one holds the package, else from
pub.dev's archive of it, checked against PUB_ARCHIVES below.
"""

from pathlib import Path
import hashlib
import io
import lzma
import os
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.request

NATIVE = Path(__file__).resolve().parents[1]
PINS = NATIVE / "services/src/main/kotlin/com/aimdi/xtanative/services/speech/VoiceEngine.kt"
SHIM = NATIVE / "services/src/main/c/xta_sherpa.c"
LIBRARIES = ["libonnxruntime.so", "libsherpa-onnx-c-api.so"]
# The SHA-256 of pub.dev's archive of each package the engine comes from, by the
# version VoiceEngines.VERSION names: pub.dev never changes a published version.
# A new engine version adds its line here with the pins in VoiceEngine.kt.
PUB_ARCHIVES = {
    "sherpa_onnx_android_arm64-1.13.8": "29db5572735afb1bc29f0f95ab160f7fb38b4b3f926f48e9113457fd740264f4",
    "sherpa_onnx_android_armeabi-1.13.8": "fe81837bd12f67d136a456c6d2b39876caaba09edd77761cb527cb170dac2d2a",
    "sherpa_onnx_android_x86_64-1.13.8": "d20c06c0edc92286609f256d04c295cad510bbb270068c0c79877f4a10f00581",
    "sherpa_onnx_linux-1.13.8": "22fb91a1c50b7bc24d93d0b67c87cf2a3a38f7564d7f14343d704a1201848960",
}
# Android's processor names and the pub.dev package carrying each one's libraries.
ANDROID_PACKAGES = {
    "arm64-v8a": "sherpa_onnx_android_arm64",
    "armeabi-v7a": "sherpa_onnx_android_armeabi",
    "x86_64": "sherpa_onnx_android_x86_64",
}
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


def pub_libraries(package, version, folder):
    """LIBRARIES out of [folder] of the pub.dev package [package] [version], from the pub cache or pub.dev's archive."""
    name = f"{package}-{version}"
    cache = Path(os.environ.get("PUB_CACHE") or Path.home() / ".pub-cache") / "hosted/pub.dev" / name / folder
    if all((cache / library).is_file() for library in LIBRARIES):
        return {library: (cache / library).read_bytes() for library in LIBRARIES}
    digest = PUB_ARCHIVES.get(name)
    if digest is None:
        raise Refused(f"{Path(__file__).name} pins no archive of {name}: add it to PUB_ARCHIVES")
    url = f"https://pub.dev/api/archives/{name}.tar.gz"
    with urllib.request.urlopen(url, timeout=300) as response:
        data = response.read()
    if sha256_of(data) != digest:
        raise Refused(f"{url} does not match its pin in {Path(__file__).name}")
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        return {library: archive.extractfile(archive.getmember(f"{folder}/{library}")).read() for library in LIBRARIES}


def linux_libraries(version):
    """The Linux x64 libraries of sherpa_onnx_linux [version]."""
    return pub_libraries("sherpa_onnx_linux", version, "linux/x64")


def archive_name(version, abi):
    return f"xta-voice-engine-sherpa-onnx-{version}-{abi}.tar.xz"


def write_archive(path, libraries):
    """A tar.xz of [libraries] as (name, bytes), the same bytes for the same input on the same xz."""
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w", format=tarfile.USTAR_FORMAT) as archive:
        for name, data in libraries:
            info = tarfile.TarInfo(name)
            info.size, info.mode, info.mtime = len(data), 0o644, 0
            archive.addfile(info, io.BytesIO(data))
    path.write_bytes(lzma.compress(buffer.getvalue(), preset=9 | lzma.PRESET_EXTREME))


def archives(out):
    version, pins = read_pins(PINS.read_text(encoding="utf-8"))
    out.mkdir(parents=True, exist_ok=True)
    for abi in sorted(abi for abi in pins if abi != "linux-x64"):
        if abi not in ANDROID_PACKAGES:
            raise Refused(f"{Path(__file__).name} knows no pub.dev package for {abi}")
        found = pub_libraries(ANDROID_PACKAGES[abi], version, f"android/src/main/jniLibs/{abi}")
        path = out / archive_name(version, abi)
        write_archive(path, [(name, checked(f"{abi}/{name}", found[name], size, digest)) for name, size, digest in pins[abi]])
        print(f"{path.name}: {path.stat().st_size} bytes")


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
        path = directory / archive_name(version, abi)
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
        elif len(arguments) == 2 and arguments[0] == "archives":
            archives(Path(arguments[1]))
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
