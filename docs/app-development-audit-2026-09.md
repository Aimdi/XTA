# XTA development audit — 8 September 2026

Audited source: `fb5eb74309390c211e3885096006bf076f6c25cb`, the default
`claude/main` branch and published `aimdi128`. This applies the 42-area app
development checklist to this repository. It is a source/release review, not a
claim that every screen, live service, or physical-device journey was tested.

## Changes in this pass

| Finding | Evidence at the audited commit | Change |
|---|---|---|
| Manual release can build the wrong branch | `release.yml` validates `inputs.tag`, then builds the original checkout | Resolve the full tag to a commit and detach there before setup/build |
| A release branch can be selected instead of its same-name tag | `build-release.yml` and `attach-release-apks.yml` use unqualified refs; both `aimdi128` refs exist | Resolve `refs/tags/<tag>^{commit}` explicitly |
| Event commit is mistaken for checkout commit | `build-release.yml` passes `github.sha` after checking out another ref | Pass the resolved commit to publication |
| Arbitrary CI APKs can be published under a requested tag | `publish-apks.yml` only downloads an artifact by run ID | Require a successful same-repository `ci.yml` run for the tagged SHA and matching artifact manifest |
| APK identity and actual certificate are unchecked | CI permits a debug-signing fallback | Inspect each APK with `aapt` and `apksigner`; check app ID, version, ABI, certificate, size and SHA256 |
| Published signing instructions name the wrong certificate | The verified aimdi128 arm64 APK has SHA256 `b4706dc61aebaa6c40663455e615592d3db8bcfc77467c6d31ae222875cb0e34`; the file contained `5e391aaa…` | Correct the public fingerprints; no key change or rotation |
| Build success does not require app verification | Release build jobs omit analysis and app tests | Run translations, skill sync, analysis and tests before APK generation |
| Existing release assets are deleted in bulk before replacement | Attach workflow calls `gh release delete-asset` for every APK | Remove the blanket deletion; upload verified named replacements |

Builds now include `release-build.json` and `SHA256SUMS`. The manifest records
the source commit/tree, build's release-tag argument, run/attempt, completed
checks and APK identities. The publisher compares recorded identities with
fresh inspection of the downloaded files. This is traceability within the
trusted workflow, not an independently signed attestation or proof of
reproducibility. It does not decode the Dart release-tag constant from AOT.

## Checklist mapped to XTA

| Checklist areas | Current evidence | Remaining work |
|---|---|---|
| 1–4: purpose, research, requirements, planning | `AGENTS.md`, `CLAUDE.md` and feature specs establish the read-oriented product and incremental scope | Keep a current prioritized backlog; user research and operating budget were not assessed |
| 5: existing-project baseline | Default branch and latest release both resolve to aimdi128 | Record the exact base for each future change; distinguish pending work from released behavior |
| 6–8: stack, setup, version control | FVM/SDK pins, dependency lockfile, build docs, CI and module rules exist | Remove remaining unused license-codegen calls from local setup/verification paths in a separate tooling cleanup; do not bump pins |
| 9–10: architecture and navigation | Feature folders, Store models, shared reader components and route handling exist | Review concrete lifecycle/navigation failures incrementally; no architectural rewrite is justified by this review |
| 11–13: prototypes, visual consistency, screen states | Home/plugin/reader specs and layout/journey tests exist | Compare delivered screens with acceptance criteria on a device, including empty/error/loading states |
| 14–16: adaptation, accessibility, localization | Adaptive/layout and semantics tests; 29 ARB files including English | TalkBack, keyboard navigation and system-inset device coverage remain necessary; ARB validation reports dropped interpolation values in existing translations |
| 17–18: complete features and lifecycle | Reading restoration and playback policies have tests; PR #261 extends article, downloads and offline behavior | Review and device-test #261 before treating those additions as released |
| 19 and 31: storage, migrations, backup/recovery | Migration tests and `backup_coverage_test.dart` compare backup sections with actual schema tables | Exercise upgrade and restore on an installed app with realistic saved content and account choices; keep frozen DB rules |
| 20–22: networking, services, accounts | Account selection, rate-limit, plugin client, guest and network tests exist | Diagnose open canary issue #253 by failing endpoint/test; it is not proof of an X-wide outage |
| 23–25: security, privacy, licensing | Android backup disabled, WebDAV enforces HTTPS, preference-secret export tests, opt-in crash reporter | Perform a focused outbound-data/redaction review across crash reports, translation/AI, integrations and backup choices; no full security or legal compliance assessment was performed |
| 26–28: Android integration, media, plugins | Android manifest integration, media/pool tests and extensive plugin modules | Real file-picker, native playback, interruption and permission tests; coordinate media/download changes with #261 |
| 29–30: payments and abuse controls | XTA does not implement posting or a hosted social network | Payment/subscription/backend moderation work is conditional, not a reason to add unrelated infrastructure |
| 32: performance | `docs/perf-baseline.md` still has device measurements marked TBD | Measure cold start, frame times, memory, battery and network usage on a representative phone before claiming improvements |
| 33–35: automated/manual/beta testing | Substantial app suite; dedicated Home/Mastodon/reading review workflows and fixtures | Add a reusable Android build gate for ordinary app PRs; record a small device beta matrix and upgrade results |
| 36–39: automation, signing, artifacts, distribution | Release/source/artifact weaknesses listed above | This pass implements guards; Play-specific submission work is conditional on choosing that channel |
| 40: launch and monitoring | GitHub release/Obtainium distribution, diagnostics and endpoint canary | Improve canary failure reports and define release/hotfix acceptance; avoid introducing telemetry by default |
| 41: documentation and support | README, build/signing docs and older audit/spec files exist | README lists only six plugins; July audit still says major UI work has not started, although aimdi127/128 shipped it |
| 42: maintenance | Pinned builds, canary, backup tests and a reproducibility script exist | Reproducibility remains unproven; measure a controlled rebuild against published APKs and maintain dependency/API compatibility |

## Next work, in priority order

1. Review this release-tooling PR and #261 independently. This change neither
   merges #261 nor stamps or publishes a new app version.
2. Add an Android debug-build check for ordinary application PRs. The current
   dedicated review jobs depend on specific branch names, while generic
   `verify.yml` covers analysis/tests but does not build an APK.
3. Run a device acceptance pass: upgrade from aimdi128, settings persistence,
   login recovery, Home scroll/navigation, media interruptions, SAF access,
   backup/restore, TalkBack and large text. Capture failures and screenshots.
4. Diagnose [canary issue #253](https://github.com/Aimdi/XTA/issues/253).
   Its body mixes guest X/Instagram/TikTok progress, so inspect the actual
   failing test and full logs before changing a frozen X client file.
5. Correct the localized values reported by `scripts/validate_arb.py`, with
   the repository translation skill and runtime copy review. The current
   validator passes with warnings; that is not complete translation quality.
6. Review privacy boundaries and diagnostics redaction using synthetic secrets
   and realistic failure messages. Existing backup-secret tests are useful
   evidence, but do not cover every outgoing surface.
7. Fill the physical-device performance baseline and run the reproducibility
   experiment. Source inspection alone establishes neither result.
8. Refresh README/plugin inventory and mark historical plans as historical so
   future agents do not repeat shipped redesigns.

## Verification of this change

- 27 local regression/integration tests pass, including real temporary Git
  repositories, wrong/foreign/failed workflow runs, modified artifacts,
  signing/version/ABI mismatches, workflow wiring and shell syntax.
- The published `xta-aimdi128_arm64-v8a.apk` was downloaded directly from the
  GitHub release and verified using Android Build Tools 35 `apksigner`.
  Signature scheme v2 verifies; app ID is `com.aimdi.xta`, version is
  `4.12.0`, version code `400001093`, ABI `arm64-v8a`.
- ARB validation passes with existing dropped-placeholder warnings; skill-tree
  sync and `git diff --check` pass.
- The `Release integrity` PR workflow repeats the tests, including inspection
  of that published APK. Generic `verify` separately runs app analysis/tests.
- No new APK, live-account test, physical-device session, performance
  measurement, reproducible rebuild, or release publication was performed.

The formatting check's shallow-history issue already has a fix in
[PR #261](https://github.com/Aimdi/XTA/pull/261); it is intentionally not
duplicated here. Application code, locale values, version metadata, SDK/dependency
pins and protected `lib/client/` / `lib/database/` paths are unchanged.
