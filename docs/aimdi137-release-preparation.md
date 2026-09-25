# aimdi137 release preparation

## Integrated source

Prepared branch: `codex/aimdi137-integration`.
Release branch: `release/aimdi137`.

- X reader completeness through `33f5fe4`.
- Mastodon reader upgrade through `92570a5`.
- Bluesky reader upgrade through `d499ede`.
- Substack reader upgrade through `fbf377c`.
- Public PR #287, adaptive Plugin Store, merged as `6fe1248`.
- Public PR #288, Antenna management, merged as `18f2b81`.
- Public PR #289, bounded RSS cover decoding, merged as `71755c7`.

All seven scopes are consolidated locally without conflicts. The three public
PRs are also merged into GitHub `claude/main`; the local integration reconciles
that public history. PR #240 was reviewed against current Home source and is
superseded by the existing Home primitives, tests and subsequent improvements.
It was not merged or closed.

## APK preparation

| Item | Prepared value |
| --- | --- |
| Next release tag | `aimdi137` (not created) |
| App version | `4.12.0` |
| Base version code | `400001126` |
| x86_64 / ARMv7 / arm64 codes | `400001127` / `400001128` / `400001129` |
| APK names | `xta-aimdi137.apk` and `xta-aimdi137_<ABI>.apk` |
| Application ID | `com.aimdi.xta` |
| Signing identity | Existing certificate in `certificate-fingerprints.txt` |

The base code exceeds every aimdi136 ABI variant. Release notes cover the
combined upgrade. The unused license generator was removed from verification
because it expects an SDK file absent from the pinned FVM layout; localization
and icon generation remain in the workflow.

No frozen `lib/client/` or `lib/database/` code, dependency pins, Flutter version,
or signing configuration changed. Only the app build number changed in
`pubspec.yaml`.

## Validation

- Combined Flutter suite: **2,997 passed**, five opt-in live tests skipped,
  zero failures (2m31s), pinned Flutter 3.44.4 / Dart 3.12.2.
- Static analysis: **zero errors, zero warnings**; 136 existing info notices.
- All **51 new Dart files** relative to current public main pass formatting.
- Release tooling: **33 passed**, one reference-APK test skipped because no
  reference APK was supplied.
- ARB/skill synchronization gates already passed for the same unchanged files
  in the feature validation; added public PRs introduce no locale/skill changes.
- `git diff --check` passes.

Android compilation and signed APK inspection have not run on the integrated
reader tree. No Android SDK/device is available locally. Native WebView, device
accessibility and authenticated live-account checks remain outstanding.

## Publication authorization and release

The user explicitly authorized public source publication, merge and signed
APK publication on 2026-09-25. This resolves the earlier automatic-review block
on exporting the local source to the public `Aimdi/XTA` repository.

The verified integration is carried by `release/aimdi137`. Its PR targets
`claude/main`; merging it invokes the existing release workflow, which creates
the tag at the exact merge commit and builds all four APK variants.

The release workflow must pass its source checks, Android build and
signature/provenance checks before publishing. It embeds
`XTA_RELEASE_TAG=aimdi137`, preserves the stable signing identity, and publishes
`release-build.json` and `SHA256SUMS` alongside the APKs.

Physical-device WebView/accessibility and authenticated live-account checks
remain outside the headless validation performed here.
