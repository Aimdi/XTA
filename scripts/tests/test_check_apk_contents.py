import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "check_apk_contents.py"
SPEC = importlib.util.spec_from_file_location("check_apk_contents", SCRIPT)
contents = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(contents)


def write_apk(directory, name, entries, compressed=()):
    with zipfile.ZipFile(Path(directory) / name, "w") as archive:
        for entry in entries:
            archive.writestr(entry, b"", zipfile.ZIP_DEFLATED if entry in compressed else zipfile.ZIP_STORED)


class CheckApkContentsTest(unittest.TestCase):
    def test_apks_without_unloaded_libraries_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            write_apk(directory, "xta.apk", ["lib/arm64-v8a/libapp.so", "lib/arm64-v8a/libsherpa-onnx-c-api.so"])
            self.assertEqual(contents.check(directory), [])

    def test_each_unloaded_library_is_reported_by_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            write_apk(directory, "a.apk", ["lib/arm64-v8a/libapp.so", "lib/x86_64/libsqlite3.so"])
            write_apk(directory, "b.apk", ["lib/armeabi-v7a/libsherpa-onnx-cxx-api.so"])
            self.assertEqual(
                contents.check(directory),
                ["a.apk carries libsqlite3.so", "b.apk carries libsherpa-onnx-cxx-api.so"],
            )

    def test_same_name_outside_the_native_folder_is_not_a_library(self):
        with tempfile.TemporaryDirectory() as directory:
            write_apk(directory, "xta.apk", ["assets/flutter_assets/libsqlite3.so"])
            self.assertEqual(contents.check(directory), [])

    def test_a_compressed_native_library_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            write_apk(
                directory,
                "xta.apk",
                ["lib/arm64-v8a/libapp.so", "lib/arm64-v8a/libflutter.so", "classes.dex"],
                compressed={"lib/arm64-v8a/libflutter.so", "classes.dex"},
            )
            self.assertEqual(contents.check(directory), ["xta.apk stores lib/arm64-v8a/libflutter.so compressed"])

    def test_empty_directory_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(contents.check(directory), ["No APKs found"])

    def test_published_reference_apk_is_caught(self):
        reference = os.environ.get("XTA_REFERENCE_APK")
        if not reference:
            self.skipTest("Set XTA_REFERENCE_APK to a published release APK")
        self.assertEqual(contents.unloaded_libraries(Path(reference)), ["libsherpa-onnx-cxx-api.so", "libsqlite3.so"])
        self.assertEqual(contents.compressed_libraries(Path(reference)), [])


if __name__ == "__main__":
    unittest.main()
