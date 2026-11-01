import hashlib
import importlib.util
import io
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "voice_engine_archives.py"
SPEC = importlib.util.spec_from_file_location("voice_engine_archives", SCRIPT)
engine = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(engine)

LIBRARIES = [("libonnxruntime.so", b"onnx" * 100), ("libsherpa-onnx-c-api.so", b"sherpa" * 50)]


def pins_for(libraries):
    return [(name, len(data), hashlib.sha256(data).hexdigest()) for name, data in libraries]


class ReadPinsTest(unittest.TestCase):
    def test_reads_the_apps_pins_for_every_processor(self):
        version, pins = engine.read_pins(engine.PINS.read_text(encoding="utf-8"))
        self.assertEqual(version, "1.13.8")
        self.assertEqual(sorted(pins), ["arm64-v8a", "armeabi-v7a", "x86_64"])
        for abi, libraries in pins.items():
            with self.subTest(abi=abi):
                self.assertEqual([name for name, _, _ in libraries], ["libonnxruntime.so", "libsherpa-onnx-c-api.so"])
                self.assertTrue(all(len(digest) == 64 for _, _, digest in libraries))

    def test_every_pinned_processor_has_a_locked_package_of_that_version(self):
        version, pins = engine.read_pins(engine.PINS.read_text(encoding="utf-8"))
        lock = engine.LOCK.read_text(encoding="utf-8")
        for abi in pins:
            with self.subTest(abi=abi):
                locked, digest = engine.locked_package(lock, engine.PACKAGES[abi])
                self.assertEqual(locked, version)
                self.assertEqual(len(digest), 64)

    def test_source_without_pins_is_refused(self):
        with self.assertRaises(engine.Refused):
            engine.read_pins("const somethingElse = 1;")


class ArchiveTest(unittest.TestCase):
    def test_the_same_libraries_make_the_same_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            first, second = Path(directory) / "a.tar.xz", Path(directory) / "b.tar.xz"
            engine.write_archive(first, LIBRARIES)
            engine.write_archive(second, LIBRARIES)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(engine.read_archive(first), dict(LIBRARIES))
            with tarfile.open(first, mode="r:xz") as archive:
                self.assertEqual(
                    [(member.name, member.mtime, member.uid) for member in archive.getmembers()],
                    [("libonnxruntime.so", 0, 0), ("libsherpa-onnx-c-api.so", 0, 0)],
                )

    def test_a_library_that_differs_from_its_pin_is_refused(self):
        (name, size, digest), _ = pins_for(LIBRARIES)
        self.assertEqual(engine.checked(name, LIBRARIES[0][1], size, digest), LIBRARIES[0][1])
        with self.assertRaises(engine.Refused):
            engine.checked(name, b"tampered", size, digest)

    def test_libraries_come_from_the_pub_cache_and_must_match_the_pins(self):
        version, pins = engine.read_pins(engine.PINS.read_text(encoding="utf-8"))
        lock = engine.LOCK.read_text(encoding="utf-8")
        with tempfile.TemporaryDirectory() as cache:
            folder = Path(cache, "hosted/pub.dev", f"sherpa_onnx_android_arm64-{version}", "android/src/main/jniLibs/arm64-v8a")
            folder.mkdir(parents=True)
            for name, data in LIBRARIES:
                (folder / name).write_bytes(data)
            with mock.patch.dict(os.environ, {"PUB_CACHE": cache}):
                with self.assertRaises(engine.Refused):
                    engine.libraries_for("arm64-v8a", version, pins["arm64-v8a"], lock)
                found = engine.libraries_for("arm64-v8a", version, pins_for(LIBRARIES), lock)
        self.assertEqual(found, LIBRARIES)

    def test_check_refuses_a_missing_or_altered_archive(self):
        version, pins = engine.read_pins(engine.PINS.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(engine.Refused):
                engine.check(Path(directory))
            for abi in pins:
                engine.write_archive(Path(directory) / engine.archive_name(version, abi), LIBRARIES)
            with self.assertRaises(engine.Refused):
                engine.check(Path(directory))


if __name__ == "__main__":
    unittest.main()
