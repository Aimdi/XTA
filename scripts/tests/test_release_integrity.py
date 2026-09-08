import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "release_integrity.py"
SPEC = importlib.util.spec_from_file_location("release_integrity", SCRIPT)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
FINGERPRINT = "a" * 64


def manifest_fixture():
    variants = [(None, sorted(release.ABI_OFFSETS)), *((abi, [abi]) for abi in release.ABI_OFFSETS)]
    return {
        "schema": 1,
        "source_sha": "b" * 40,
        "source_tree": "c" * 40,
        "release_tag": "aimdi128",
        "repository": "Aimdi/XTA",
        "run_id": 123,
        "run_attempt": 2,
        "checks": [name for name, _ in release.CHECKS],
        "apks": [
            {
                "name": f"xta-aimdi128{'_' + abi if abi else ''}.apk",
                "application_id": "com.aimdi.xta",
                "version_code": 400001090 + release.ABI_OFFSETS.get(abi, 0),
                "version_name": "4.12.0",
                "debuggable": False,
                "abis": abis,
                "signer_sha256": [FINGERPRINT],
            }
            for abi, abis in variants
        ],
    }


def run_fixture():
    return {
        "id": 123,
        "run_attempt": 2,
        "repository": {"full_name": "Aimdi/XTA"},
        "head_repository": {"full_name": "Aimdi/XTA"},
        "path": ".github/workflows/ci.yml",
        "event": "workflow_dispatch",
        "status": "completed",
        "conclusion": "success",
        "head_sha": "b" * 40,
    }


class ManifestTest(unittest.TestCase):
    def validate(self, manifest, run=None):
        release.validate_manifest(
            manifest, "aimdi128", {"source_sha": "b" * 40, "source_tree": "c" * 40},
            "Aimdi/XTA", FINGERPRINT, ("4.12.0", 400001090), run,
        )

    def test_complete_release_and_successful_run_are_accepted(self):
        self.validate(manifest_fixture(), run_fixture())

    def test_untagged_stale_unverified_or_foreign_artifacts_are_rejected(self):
        for key, value in (
            ("schema", 0), ("release_tag", ""), ("release_tag", "aimdi127"),
            ("source_sha", "d" * 40), ("source_tree", "e" * 40),
            ("repository", "someone/XTA"), ("checks", ["analysis"]),
            ("run_id", 124), ("run_attempt", 1),
        ):
            with self.subTest(key=key, value=value):
                manifest = manifest_fixture()
                manifest[key] = value
                with self.assertRaises(ValueError):
                    self.validate(manifest, run_fixture())

    def test_failed_wrong_or_foreign_workflow_runs_are_rejected(self):
        for key, value in (
            ("conclusion", "failure"), ("status", "in_progress"),
            ("event", "pull_request"), ("path", ".github/workflows/release.yml"),
            ("head_sha", "d" * 40), ("repository", {"full_name": "someone/XTA"}),
            ("head_repository", {"full_name": "someone/XTA"}),
        ):
            with self.subTest(key=key):
                run = run_fixture()
                run[key] = value
                with self.assertRaises(ValueError):
                    self.validate(manifest_fixture(), run)

    def test_wrong_identity_debug_signing_architecture_and_versions_are_rejected(self):
        for key, value in (
            ("application_id", "com.aimdi.xta.debug"), ("debuggable", True),
            ("signer_sha256", ["d" * 64]), ("signer_sha256", []),
            ("abis", ["arm64-v8a"]), ("version_name", "4.11.0"),
            ("version_code", 400001086), ("name", "old-release.apk"),
        ):
            with self.subTest(key=key):
                manifest = manifest_fixture()
                manifest["apks"][0][key] = value
                with self.assertRaises(ValueError):
                    self.validate(manifest)

    def test_missing_or_duplicate_variants_are_rejected(self):
        for apks in (manifest_fixture()["apks"][:3], [manifest_fixture()["apks"][0]] * 4):
            manifest = manifest_fixture()
            manifest["apks"] = apks
            with self.assertRaises(ValueError):
                self.validate(manifest)


class ApkDetailsTest(unittest.TestCase):
    def test_android_tool_output_is_parsed(self):
        details = release.parse_apk_details(
            "package: name='com.aimdi.xta' versionCode='400001093' versionName='4.12.0' platformBuildVersionCode='37'\n"
            "native-code: 'arm64-v8a'\n",
            f"Signer #1 certificate SHA-256 digest: {FINGERPRINT}\n",
        )
        self.assertEqual(details, {
            "application_id": "com.aimdi.xta", "version_code": 400001093,
            "version_name": "4.12.0", "debuggable": False,
            "abis": ["arm64-v8a"], "signer_sha256": [FINGERPRINT],
        })

    def test_missing_package_or_signer_is_rejected(self):
        with self.assertRaises(ValueError):
            release.parse_apk_details("", "")
        with self.assertRaises(ValueError):
            release.parse_apk_details("package: name='x' versionCode='1' versionName='1'", "")


class RepositoryTest(unittest.TestCase):
    def setUp(self):
        self.original_cwd = Path.cwd()
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.addCleanup(os.chdir, self.original_cwd)
        os.chdir(self.temp.name)
        self.git("init", "-q")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "test@example.invalid")
        Path("pubspec.yaml").write_text("version: 4.12.0+400001090\n")
        Path("certificate-fingerprints.txt").write_text(f"SHA256: {FINGERPRINT}\n")
        self.git("add", ".")
        self.git("commit", "-qm", "tagged source")
        self.tagged_sha = self.git("rev-parse", "HEAD")
        self.git("tag", "aimdi128")

    def git(self, *args):
        return subprocess.check_output(["git", *args], text=True, stderr=subprocess.PIPE).strip()

    def cli(self, *args, **kwargs):
        return subprocess.run(["python3", str(SCRIPT), *args], text=True, capture_output=True, **kwargs)

    def test_manual_release_and_same_name_branch_use_the_tagged_commit(self):
        Path("new-branch-feature.txt").write_text("Not in the requested release\n")
        self.git("add", ".")
        self.git("commit", "-qm", "newer branch source")
        self.git("branch", "aimdi128")
        with patch.dict(os.environ, {"GITHUB_OUTPUT": str(Path.cwd() / "outputs")}):
            result = self.cli("checkout", "aimdi128")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.git("rev-parse", "HEAD"), self.tagged_sha)
        self.assertFalse(Path("new-branch-feature.txt").exists())
        self.assertEqual(Path("outputs").read_text(), f"tag=aimdi128\nsha={self.tagged_sha}\n")

    def test_annotated_tag_is_peeled_to_its_commit(self):
        self.git("tag", "-am", "annotated", "annotated-release")
        self.assertEqual(release.tag_commit("annotated-release"), self.tagged_sha)

    def test_missing_branch_only_and_invalid_tags_fail_without_changing_head(self):
        self.git("branch", "branch-only")
        for tag in ("missing", "branch-only", "", "aimdi128\nsha=wrong", "../aimdi128", "--help"):
            with self.subTest(tag=tag):
                self.assertNotEqual(self.cli("checkout", "--", tag).returncode, 0)
                self.assertEqual(self.git("rev-parse", "HEAD"), self.tagged_sha)

    def test_failed_source_checks_remove_stale_success_report(self):
        report = Path("checks.json")
        report.write_text('{"checks": ["tests"]}')
        with patch.object(release, "CHECKS", (("tests", ("python3", "-c", "raise SystemExit(1)")),)):
            with self.assertRaises(subprocess.CalledProcessError):
                release.check_source(report)
        self.assertFalse(report.exists())

    def prepare_artifacts(self):
        directory = Path("artifacts")
        directory.mkdir()
        for apk in manifest_fixture()["apks"]:
            (directory / apk["name"]).write_bytes(apk["name"].encode())
        checks = release.source()
        checks["checks"] = [name for name, _ in release.CHECKS]
        release.write_json("checks.json", checks)
        details = {apk["name"]: {key: value for key, value in apk.items() if key != "name"} for apk in manifest_fixture()["apks"]}
        self.addCleanup(patch.stopall)
        patch.object(release, "inspect_apk", side_effect=lambda path: copy.deepcopy(details[path.name])).start()
        patch.dict(os.environ, {"GITHUB_REPOSITORY": "Aimdi/XTA", "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "2"}).start()
        release.record(directory, "aimdi128", "checks.json")
        return directory

    def test_record_and_verify_round_trip(self):
        directory = self.prepare_artifacts()
        release.verify(directory, "aimdi128")
        manifest = json.loads((directory / "release-build.json").read_text())
        self.assertEqual(manifest["source_sha"], self.tagged_sha)
        self.assertEqual(len(manifest["apks"]), 4)

    def test_modified_apk_is_rejected(self):
        directory = self.prepare_artifacts()
        (directory / "xta-aimdi128.apk").write_bytes(b"replaced")
        with self.assertRaisesRegex(ValueError, "checksum"):
            release.verify(directory, "aimdi128")

    def test_extra_apk_is_rejected(self):
        directory = self.prepare_artifacts()
        (directory / "extra.apk").write_bytes(b"extra")
        with self.assertRaisesRegex(ValueError, "file set"):
            release.verify(directory, "aimdi128")

    def test_apk_metadata_is_reinspected_not_trusted_from_manifest(self):
        directory = self.prepare_artifacts()
        with patch.object(release, "inspect_apk", return_value={"signer_sha256": ["d" * 64]}):
            with self.assertRaisesRegex(ValueError, "metadata mismatch"):
                release.verify(directory, "aimdi128")

    def test_modified_checksum_file_is_rejected(self):
        directory = self.prepare_artifacts()
        (directory / "SHA256SUMS").write_text("incorrect\n")
        with self.assertRaisesRegex(ValueError, "SHA256SUMS"):
            release.verify(directory, "aimdi128")

    def test_report_from_different_source_is_rejected(self):
        directory = self.prepare_artifacts()
        report = json.loads(Path("checks.json").read_text())
        report["source_sha"] = "f" * 40
        release.write_json("checks.json", report)
        with self.assertRaisesRegex(ValueError, "different source"):
            release.record(directory, "aimdi128", "checks.json")

    def test_corrected_certificate_can_be_supplied_for_a_historical_tag(self):
        directory = self.prepare_artifacts()
        Path("corrected-certificate.txt").write_text(f"SHA256: {FINGERPRINT}\n")
        Path("certificate-fingerprints.txt").write_text("SHA256: " + "f" * 64 + "\n")
        with self.assertRaisesRegex(ValueError, "signing certificate"):
            release.verify(directory, "aimdi128")
        release.verify(directory, "aimdi128", certificate_path="corrected-certificate.txt")


@unittest.skipUnless(os.environ.get("XTA_REFERENCE_APK"), "Set XTA_REFERENCE_APK to inspect the published APK")
class PublishedApkTest(unittest.TestCase):
    def test_android_tools_verify_the_published_aimdi128_identity(self):
        path = Path(os.environ["XTA_REFERENCE_APK"])
        details = release.inspect_apk(path)
        self.assertEqual(details["application_id"], "com.aimdi.xta")
        self.assertEqual(details["version_code"], 400001093)
        self.assertEqual(details["version_name"], "4.12.0")
        self.assertEqual(details["abis"], ["arm64-v8a"])
        self.assertFalse(details["debuggable"])
        certificate_file = SCRIPT.parent.parent / "certificate-fingerprints.txt"
        fingerprint = certificate_file.read_text().split("SHA256:")[1].strip().replace(":", "").lower()
        self.assertEqual(details["signer_sha256"], [fingerprint])


if __name__ == "__main__":
    unittest.main()
