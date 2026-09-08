#!/usr/bin/env python3
"""Bind XTA release artifacts to their source, checks, and signing identity."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


CHECKS = (
    ("translations", ("python3", "scripts/validate_arb.py")),
    ("skill-sync", ("bash", "scripts/check_skill_sync.sh")),
    ("analysis", ("flutter", "analyze", "--no-fatal-infos")),
    ("tests", ("flutter", "test", "--reporter", "expanded")),
)
ABI_OFFSETS = {"x86_64": 1, "armeabi-v7a": 2, "arm64-v8a": 3}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def output(*command):
    return subprocess.check_output(command, text=True).strip()


def git(*args):
    return output("git", *args)


def tag_commit(tag):
    require(bool(tag), "A release tag is required")
    subprocess.run(["git", "check-ref-format", f"refs/tags/{tag}"], check=True)
    return git("rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}")


def source():
    return {"source_sha": git("rev-parse", "HEAD"), "source_tree": git("rev-parse", "HEAD^{tree}")}


def checkout(tag):
    sha = tag_commit(tag)
    subprocess.run(["git", "checkout", "--detach", sha], check=True)
    require(git("rev-parse", "HEAD") == sha, "Checkout does not match the requested tag")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
            stream.write(f"tag={tag}\nsha={sha}\n")
    print(f"Release {tag}: checked out {sha}")


def write_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def check_source(path):
    Path(path).unlink(missing_ok=True)
    report = source()
    report["checks"] = []
    for name, command in CHECKS:
        print(f"Checking {name}", flush=True)
        subprocess.run(command, check=True)
        report["checks"].append(name)
    require(source() == {key: report[key] for key in source()}, "Source changed during verification")
    write_json(path, report)


def android_tool(name):
    installed = shutil.which(name)
    if installed:
        return installed
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    require(bool(sdk), f"Android SDK is required to inspect APKs ({name})")
    candidates = list((Path(sdk) / "build-tools").glob(f"*/{name}"))
    require(bool(candidates), f"Cannot find Android build tool: {name}")
    return str(max(candidates, key=lambda path: tuple(int(n) for n in re.findall(r"\d+", path.parent.name))))


def parse_apk_details(badging, certificates):
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    require(package is not None, "Cannot read APK package/version metadata")
    native = re.search(r"^native-code: (.+)$", badging, re.M)
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", certificates, re.M)
    require(bool(fingerprints), "Cannot read APK signing certificate")
    return {
        "application_id": package[1],
        "version_code": int(package[2]),
        "version_name": package[3],
        "debuggable": bool(re.search(r"^application-debuggable(?:\s|$)", badging, re.M)),
        "abis": sorted(re.findall(r"'([^']+)'", native[1])) if native else [],
        "signer_sha256": sorted(fingerprint.lower() for fingerprint in fingerprints),
    }


def inspect_apk(path):
    return parse_apk_details(
        output(android_tool("aapt"), "dump", "badging", str(path)),
        output(android_tool("apksigner"), "verify", "--print-certs", str(path)),
    )


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def apk_paths(directory):
    paths = sorted(Path(directory).glob("*.apk"))
    require(bool(paths), "No APKs found")
    require(all(path.is_file() and not path.is_symlink() for path in paths), "APKs must be regular files")
    return paths


def record(directory, tag, checks_path):
    origin = source()
    if tag:
        require(tag_commit(tag) == origin["source_sha"], "Build source does not match its release tag")
    checks = json.loads(Path(checks_path).read_text(encoding="utf-8"))
    require(all(checks.get(key) == value for key, value in origin.items()), "Checks belong to different source")
    require(checks.get("checks") == [name for name, _ in CHECKS], "Source verification is incomplete")
    artifacts = []
    for path in apk_paths(directory):
        artifacts.append({"name": path.name, "sha256": digest(path), "size": path.stat().st_size, **inspect_apk(path)})
    manifest = {
        "schema": 1,
        **origin,
        "release_tag": tag,
        "repository": os.environ["GITHUB_REPOSITORY"],
        "run_id": int(os.environ["GITHUB_RUN_ID"]),
        "run_attempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
        "checks": checks["checks"],
        "apks": artifacts,
    }
    write_json(Path(directory) / "release-build.json", manifest)
    (Path(directory) / "SHA256SUMS").write_text(
        "".join(f"{item['sha256']}  {item['name']}\n" for item in artifacts), encoding="utf-8"
    )
    print(f"Recorded {len(artifacts)} APKs from {origin['source_sha']}")


def validate_run(run, repository, sha):
    require(run.get("repository", {}).get("full_name", "").lower() == repository.lower(), "Wrong workflow repository")
    require(run.get("head_repository", {}).get("full_name", "").lower() == repository.lower(), "Foreign workflow source")
    require(run.get("path") == ".github/workflows/ci.yml", "Artifacts must come from ci.yml")
    require(run.get("event") in ("push", "workflow_dispatch"), "Unsupported artifact workflow event")
    require(run.get("status") == "completed" and run.get("conclusion") == "success", "Artifact workflow did not succeed")
    require(run.get("head_sha") == sha, "Artifact workflow source does not match the release tag")


def validate_manifest(manifest, tag, origin, repository, fingerprint, version, run=None):
    require(manifest.get("schema") == 1, "Unsupported or missing release manifest")
    require(manifest.get("release_tag") == tag, "APK build was not stamped with the requested release tag")
    require(manifest.get("repository", "").lower() == repository.lower(), "Wrong artifact repository")
    require(all(manifest.get(key) == value for key, value in origin.items()), "Artifacts belong to different source")
    require(manifest.get("checks") == [name for name, _ in CHECKS], "Artifacts lack complete source verification")
    if run is not None:
        validate_run(run, repository, origin["source_sha"])
        require(manifest.get("run_id") == run.get("id"), "Manifest belongs to a different workflow run")
        require(manifest.get("run_attempt") == run.get("run_attempt"), "Manifest belongs to a different run attempt")
    apks = manifest.get("apks", [])
    expected_names = {f"xta-{tag}.apk", *(f"xta-{tag}_{abi}.apk" for abi in ABI_OFFSETS)}
    require(len(apks) == 4 and {apk.get("name") for apk in apks} == expected_names, "Release must contain all four named APK variants")
    for apk in apks:
        require(apk.get("application_id") == "com.aimdi.xta" and apk.get("debuggable") is False, "APK is not the release application")
        require(apk.get("signer_sha256") == [fingerprint], "APK does not use the published release signing certificate")
        abi = next((abi for abi in ABI_OFFSETS if apk["name"] == f"xta-{tag}_{abi}.apk"), None)
        require(apk.get("abis") == (sorted(ABI_OFFSETS) if abi is None else [abi]), "APK architecture does not match its filename")
        require(apk.get("version_name") == version[0], "APK version name does not match the tagged pubspec")
        require(apk.get("version_code") == version[1] + ABI_OFFSETS.get(abi, 0), "APK version code does not match the tagged pubspec")


def verify(directory, tag, run_path=None, certificate_path="certificate-fingerprints.txt"):
    require(tag_commit(tag) == git("rev-parse", "HEAD"), "Verification must run on the tagged source")
    manifest = json.loads((Path(directory) / "release-build.json").read_text(encoding="utf-8"))
    certificate = re.search(r"^SHA256:\s*([0-9A-Fa-f:]+)\s*$", Path(certificate_path).read_text(), re.M)
    require(certificate is not None, "Missing published SHA256 signing fingerprint")
    fingerprint = certificate[1].replace(":", "").lower()
    require(len(fingerprint) == 64, "Invalid published signing fingerprint")
    version = re.search(r"^version:\s*([^\s+]+)\+(\d+)\s*$", Path("pubspec.yaml").read_text(), re.M)
    require(version is not None, "Cannot read tagged app version")
    run = json.loads(Path(run_path).read_text()) if run_path else None
    validate_manifest(manifest, tag, source(), os.environ["GITHUB_REPOSITORY"], fingerprint, (version[1], int(version[2])), run)
    paths = apk_paths(directory)
    require({path.name for path in paths} == {apk["name"] for apk in manifest["apks"]}, "APK file set does not match the manifest")
    for path in paths:
        apk = next(apk for apk in manifest["apks"] if apk["name"] == path.name)
        require(path.stat().st_size == apk.get("size") and digest(path) == apk.get("sha256"), f"APK checksum mismatch: {path.name}")
        actual = inspect_apk(path)
        require(all(apk.get(key) == value for key, value in actual.items()), f"APK metadata mismatch: {path.name}")
    expected_checksums = "".join(f"{apk['sha256']}  {apk['name']}\n" for apk in manifest["apks"])
    require((Path(directory) / "SHA256SUMS").read_text() == expected_checksums, "SHA256SUMS does not match the manifest")
    print(f"Verified release {tag}: source, checks, all APK variants and signing certificate match")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("checkout").add_argument("tag")
    commands.add_parser("check-source").add_argument("report")
    for name in ("record", "verify"):
        command = commands.add_parser(name)
        command.add_argument("directory")
        command.add_argument("--tag", required=True)
        if name == "record":
            command.add_argument("--checks", required=True)
        else:
            command.add_argument("--run")
            command.add_argument("--certificate", default="certificate-fingerprints.txt")
    args = parser.parse_args()
    if args.command == "checkout":
        checkout(args.tag)
    elif args.command == "check-source":
        check_source(args.report)
    elif args.command == "record":
        record(args.directory, args.tag, args.checks)
    else:
        verify(args.directory, args.tag, args.run, args.certificate)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f"Release verification failed: {error}", file=sys.stderr)
        sys.exit(1)
