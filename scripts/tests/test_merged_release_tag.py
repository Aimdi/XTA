"""Exercise the release workflow's tag step against real disposable git repos."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = yaml.safe_load((ROOT / ".github/workflows/build-release.yml").read_text())
JOB = WORKFLOW["jobs"]["build-release"]
TAG_STEP = next(step for step in JOB["steps"] if step.get("id") == "merged_release")


class MergedReleaseTagTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "checkout"
        self.remote = self.root / "remote.git"
        self.repo.mkdir()
        self.git("init", "--bare", str(self.remote))
        self.git("init", "-b", "claude/main")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "release-test@example.invalid")
        self.git("commit", "--allow-empty", "-m", "Earlier source")
        self.old_sha = self.git("rev-parse", "HEAD")
        self.git("commit", "--allow-empty", "-m", "Merged release source")
        self.sha = self.git("rev-parse", "HEAD")
        self.git("remote", "add", "origin", str(self.remote))

    def git(self, *args):
        return subprocess.check_output(
            ["git", *args], cwd=self.repo, text=True, stderr=subprocess.DEVNULL
        ).strip()

    def run_tag(self, branch="release/aimdi129", sha=None):
        return subprocess.run(
            ["bash", "-e", "-c", TAG_STEP["run"]], cwd=self.repo, text=True,
            capture_output=True, env={
                **os.environ,
                "RELEASE_BRANCH": branch,
                "RELEASE_SHA": self.sha if sha is None else sha,
                "GITHUB_OUTPUT": str(self.root / "output"),
            },
        )

    def test_creates_remote_tag_at_exact_merged_commit(self):
        result = self.run_tag()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        remote_tag = self.git("ls-remote", "--tags", "origin", "refs/tags/aimdi129")
        self.assertEqual(remote_tag, f"{self.sha}\trefs/tags/aimdi129")
        self.assertEqual((self.root / "output").read_text(), "tag=aimdi129\n")

    def test_repeating_same_release_is_safe(self):
        self.assertEqual(self.run_tag().returncode, 0)
        self.assertEqual(self.run_tag().returncode, 0)
        self.assertEqual(self.git("rev-parse", "refs/tags/aimdi129"), self.sha)

    def test_does_not_move_existing_release_tag(self):
        self.git("tag", "aimdi129", self.old_sha)
        self.git("push", "origin", "refs/tags/aimdi129")
        result = self.run_tag()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("will not be moved", result.stdout)
        self.assertEqual(self.git("rev-parse", "refs/tags/aimdi129"), self.old_sha)
        self.assertTrue(self.git("ls-remote", "--tags", "origin").startswith(self.old_sha))

    def test_remote_tag_race_is_rejected_without_overwriting(self):
        self.git("tag", "aimdi129", self.old_sha)
        self.git("push", "origin", "refs/tags/aimdi129")
        self.git("tag", "--delete", "aimdi129")
        self.assertNotEqual(self.run_tag().returncode, 0)
        self.assertTrue(self.git("ls-remote", "--tags", "origin").startswith(self.old_sha))

    def test_rejects_wrong_or_missing_source_sha(self):
        for sha in (self.old_sha, "", "not-a-commit"):
            with self.subTest(sha=sha):
                self.assertNotEqual(self.run_tag(sha=sha).returncode, 0)
        self.assertEqual(self.git("ls-remote", "--tags", "origin"), "")

    def test_rejects_nonrelease_or_malformed_branches(self):
        for branch in ("feature/aimdi129", "release/aimdi0", "release/aimdi0129",
                       "release/aimdi129-extra", "release/aimdi129\ntag=aimdi128"):
            with self.subTest(branch=branch):
                self.assertNotEqual(self.run_tag(branch=branch).returncode, 0)
        self.assertEqual(self.git("ls-remote", "--tags", "origin"), "")

    def test_merge_trigger_is_limited_to_trusted_release_prs(self):
        # PyYAML's YAML 1.1 parser treats the unquoted GitHub 'on' key as True.
        events = WORKFLOW.get("on", WORKFLOW.get(True))
        self.assertEqual(events["pull_request"], {"types": ["closed"], "branches": ["claude/main"]})
        for guard in (
            "github.event.pull_request.merged == true",
            "github.event.pull_request.head.repo.full_name == github.repository",
            "github.event.pull_request.base.ref == 'claude/main'",
            "startsWith(github.event.pull_request.head.ref, 'release/aimdi')",
        ):
            self.assertIn(guard, JOB["if"])
        checkout = next(step for step in JOB["steps"] if "actions/checkout@" in step.get("uses", ""))
        self.assertIn("github.event.pull_request.merge_commit_sha", checkout["with"]["ref"])
        resolve = next(step for step in JOB["steps"] if step.get("id") == "tag")
        self.assertIn("steps.merged_release.outputs.tag", resolve["env"]["RELEASE_TAG"])


if __name__ == "__main__":
    unittest.main()
