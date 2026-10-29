# Cloud testing for XTA

XTA is **Android-only**. Cursor Cloud VMs currently **do not expose `/dev/kvm`**, so a
usable Android emulator (hardware-accelerated) is not available. Software
emulation (`-accel off`) can start but is too slow/unstable for interactive UI
work.

## What works in Cloud today

| Check | Command |
|---|---|
| Lint | `fvm flutter analyze` |
| Unit / characterization tests | `fvm flutter test` |
| Live guest API smoke | `fvm flutter test test/live/guest_api_smoke_test.dart --dart-define=RUN_LIVE=true` |
| Live reads of every account-free source | `fvm flutter test test/live/sources_smoke_test.dart --dart-define=RUN_LIVE=true` (needs a network that reaches them; the daily `api-canary` runs it on GitHub) |
| X query id report | `fvm flutter test test/live/x_query_ids_test.dart --dart-define=RUN_LIVE=true` (reads GitHub's API; the daily `api-canary` runs it as "X query ids") |
| Debug APK | `fvm flutter build apk --debug` |
| One-shot verify | `bash scripts/cloud_verify.sh` |
| X screens against a stand-in for X | `fvm flutter test test/mock_x_test.dart`; the emulator flows run on GitHub (`emulator-qa`), see `docs/mock-x.md` |

## Live checks and the signing auto-fix

The daily `api-canary` workflow runs the live checks on GitHub and opens an
issue when a service fails. Its "X query ids" row lists, for every X
operation, the id each kind of install uses and how long ago X's web stopped
using it; it fails when installs cannot read the published `endpoints.json`
or when `main` publishes other ids than the default branch
(`docs/specs/x-query-id-watch.md`). When the X checks fail on X's request signing,
the `X signing auto-fix` workflow (`.github/workflows/x-signing-autofix.yml`)
asks Claude for a fix and opens it as a draft pull request (playbook:
`docs/fix-x-signing.md`). It needs, from the repository owner:

- A repository secret: `ANTHROPIC_API_KEY` (an Anthropic API key) or
  `CLAUDE_CODE_OAUTH_TOKEN` (from `claude setup-token`, for a Claude
  subscription). Without either, the workflow stops at its first step with a
  notice and changes nothing.
- **Settings → Actions → General → Workflow permissions → Allow GitHub Actions
  to create and approve pull requests**, so the workflow's own token can open
  the draft.

It tries at most once per failed canary, and not while a fix pull request is
open or was opened in the last 24 hours. A pull request opened with the
workflow's token starts no `pull_request` workflows, so the auto-fix starts
`verify` and `X bootstrap check` on its branch itself; their results show on
the pull request.

## Interactive app use

1. **Preferred:** pair a physical phone via wireless ADB  
   `bash scripts/adb_wireless_connect.sh <pair_host:port> <code> <connect_host:port>`  
   then `adb install -r build/app/outputs/flutter-apk/app-debug.apk`
2. **Not viable here:** local AVD without KVM (Cursor feature request: KVM for Cloud Agents)
3. **Desktop/Chrome `flutter devices`:** listed by Flutter but unusable — no `linux/` / `web/` platform folders and Android-only plugins

## Environment bootstrap

- Run `bash scripts/cloud_install.sh` on a fresh VM (the Cursor `.cursor/environment.json`
  hook that used to run it was removed).
- **Cold VMs:** `cloud_install.sh` bootstraps FVM (into `~/fvm`) and Android
  cmdline-tools (into `~/android-sdk`) when they are missing, then runs
  `fvm install` / `pub get` / codegen and applies the `platforms/android-37`
  hash quirk fix.
- **Warm snapshots (preferred):** after one successful cold setup, save a VM
  snapshot from the Cloud Agents dashboard so later agents only refresh deps.
