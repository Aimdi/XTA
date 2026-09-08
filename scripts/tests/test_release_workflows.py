from pathlib import Path
import re
import subprocess
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[2]
PUBLISHERS = ("release.yml", "build-release.yml", "attach-release-apks.yml", "publish-apks.yml")


def workflow_steps(name):
    workflow = yaml.safe_load((ROOT / ".github/workflows" / name).read_text())
    return next(iter(workflow["jobs"].values()))["steps"]


class ReleaseWorkflowTest(unittest.TestCase):
    def test_each_publisher_resolves_tag_before_setup_and_pins_release_target(self):
        for name in PUBLISHERS:
            with self.subTest(workflow=name):
                steps = workflow_steps(name)
                tag_index = next(index for index, step in enumerate(steps) if step.get("id") == "tag")
                tag_step = steps[tag_index]
                self.assertIn('release-integrity.py" checkout ', tag_step["run"])
                self.assertIn('cp certificate-fingerprints.txt "$RUNNER_TEMP/xta-release-certificate.txt"', tag_step["run"])
                checkout = next(step for step in steps[:tag_index] if "actions/checkout@" in step.get("uses", ""))
                self.assertEqual(checkout["with"]["fetch-depth"], 0)
                for index, step in enumerate(steps):
                    if any(term in step.get("uses", "") for term in ("setup-java@", "flutter-action@")):
                        self.assertLess(tag_index, index)
                    if "action-gh-release@" in step.get("uses", ""):
                        self.assertEqual(step["with"]["target_commitish"], "${{ steps.tag.outputs.sha }}")
                        self.assertTrue(step["with"]["fail_on_unmatched_files"])
                        self.assertIn("release-build.json", step["with"]["files"])
                        self.assertIn("SHA256SUMS", step["with"]["files"])

    def test_every_release_upload_has_a_preceding_artifact_verification(self):
        for name in PUBLISHERS:
            with self.subTest(workflow=name):
                steps = workflow_steps(name)
                verify_index = next(index for index, step in enumerate(steps) if 'release-integrity.py" verify ' in step.get("run", ""))
                upload_index = next(index for index, step in enumerate(steps) if "action-gh-release@" in step.get("uses", ""))
                self.assertLess(verify_index, upload_index)
                self.assertIn('--certificate "$RUNNER_TEMP/xta-release-certificate.txt"', steps[verify_index]["run"])

    def test_builders_run_checks_before_building_with_the_recorded_stamp(self):
        for name in (*PUBLISHERS[:3], "ci.yml"):
            with self.subTest(workflow=name):
                steps = workflow_steps(name)
                build = next(step for step in steps if "flutter build apk" in step.get("run", ""))
                script = build["run"]
                self.assertLess(script.index("check-source"), script.index("flutter build apk"))
                for command in script.splitlines():
                    if "flutter build apk" in command:
                        self.assertIn('--dart-define=XTA_RELEASE_TAG="$RELEASE_TAG"', command)
                self.assertIn("RELEASE_TAG", build["env"])
                self.assertNotIn("dart_pubspec_licenses:generate", script)

    def test_publisher_checks_downloaded_run_identity(self):
        steps = workflow_steps("publish-apks.yml")
        verification = next(step["run"] for step in steps if 'release-integrity.py" verify ' in step.get("run", ""))
        self.assertIn('--run "$RUNNER_TEMP/xta-artifact-run.json"', verification)
        download = next(step for step in steps if "actions/download-artifact@" in step.get("uses", ""))
        self.assertEqual(download["with"]["run-id"], "${{ inputs.run_id }}")
        self.assertEqual(download["with"]["path"], "build/app/outputs/release")

    def test_attach_does_not_delete_all_published_apks(self):
        workflow = (ROOT / ".github/workflows/attach-release-apks.yml").read_text()
        self.assertNotIn("gh release delete-asset", workflow)
        self.assertIn("github.event.pull_request.head.repo.full_name == github.repository", workflow)

    def test_workflow_shell_syntax_and_event_input_handling(self):
        for name in (*PUBLISHERS, "ci.yml"):
            for step in workflow_steps(name):
                if "run" not in step:
                    continue
                with self.subTest(workflow=name, step=step.get("name")):
                    self.assertNotRegex(step["run"], r"\$\{\{\s*(?:inputs\.|github\.event\.|github\.head_ref)")
                    script = re.sub(r"\$\{\{.*?\}\}", "test-value", step["run"])
                    result = subprocess.run(["bash", "-n"], input=script, text=True, capture_output=True)
                    self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
