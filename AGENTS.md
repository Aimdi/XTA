# AGENTS.md

General build/architecture/testing guidance for this repo lives in `CLAUDE.md`,
`README.md` (see "Build locally"), `.claude/skills/`, and `.grok/skills/`.
Read those first.

## Grok Build (grok-4.5) — project instructions

Grok Build auto-loads Claude Code assets (`CLAUDE.md`, `.claude/skills/`) **and**
this `AGENTS.md`, plus native skills under `.grok/skills/`. After changing
instruction or skill files, run `grok inspect` in the repo root and confirm they
appear.

### Hard rules (do not violate)

- **XTA is a read-oriented X frontend, not X itself.** It views timelines,
  profiles, search, and media via reverse-engineered APIs. It does **not**
  create posts on X. Never add compose / reply / quote / repost / like-on-X /
  DM / Spaces hosting / account settings write-back. Local-only actions
  (device likes, saved folders, subscriptions stored in SQLite) are fine and
  already exist — do not wire them to X write endpoints.
- Footer icons that look like X actions are **navigation / local** affordances
  (e.g. comment opens the conversation; repeat opens the quotes screen; heart
  is local-only). Do not "fix" them into real posting.
- **`lib/client/` and `lib/database/` are frozen** — never rewrite them as part
  of a UI/perf pass. Touch only to fix a live API break. DB schema changes only
  via `sqflite_migration_plan` migrations.
- **Do not big-bang rewrite.** Rewrite UI/feature folders incrementally
  (`tweet/` → `home/` → `profile/` → `search/` → rest).
- **Store pattern only.** Use `flutter_triple` `Store<T>` — never `setState` or
  `ChangeNotifier` for feature state.
- **No raw UI strings.** Every user-visible string goes through ARB / `L10n`
  (see `/translate`). Never hand-edit `lib/generated/`.
- **Null-safe API parsing.** Reverse-engineered X JSON is fragile — use `?[]`
  and `as Type?` (see `/parse-api`). Prefer `?["field"] ?? default`.
- **Never bump pinned deps** (`dart_twitter_api: 0.6.0`, the
  `dependency_overrides` block, Flutter **3.44.4** in `.fvmrc` / `pubspec.yaml`).
  They are load-bearing.
- Prefer pure functions; keep functions under ~30 lines (widget builders excepted).

### Permission / worktree workflow

- Default permission mode: **ask**. Keep **ask** for anything touching
  `lib/client/` or `lib/database/`. Use `always-approve` only inside isolated
  UI-module worktrees.
- One module per worktree (Grok has no built-in worktree flag):

  ```bash
  git worktree add ../xta-tweet rewrite/tweet
  cd ../xta-tweet && grok
  ```

- Module order for incremental UI rewrite:
  `tweet/` → `home/` → `profile/` → `search/` → `group/` → `saved/` → `settings/`
- Per module: Plan mode (`Shift+Tab` / `/plan`) → write a spec file → commit the
  spec → implement against it. Use `/context`, `/compact` between modules,
  `/memory` + `/flush` for durable decisions, `/rewind` to undo a bad direction,
  `/btw` for side questions.

### Enforced guardrails

Some of the rules above are machine-enforced for Claude Code via
`.claude/settings.json` (Grok ignores it — the prose still governs there):

- `permissions.deny` — Edit/Write on `lib/generated/**` and `lib/oss_licenses.dart`.
- `permissions.ask` — Edit/Write on `lib/client/**` and `lib/database/**`.
- `PreToolUse` → `.claude/hooks/guard-pinned-deps.sh` denies edits that change
  `dart_twitter_api`, a `dependency_overrides` entry, or the Flutter version in
  `pubspec.yaml` / `.fvmrc`. Other pubspec edits pass.
- `SessionStart` → `.claude/hooks/session-start.sh` runs `pub get` +
  `intl_utils:generate` (non-fatal, skipped without `fvm`).
- `PostToolUse` → `.claude/hooks/format-dart.sh` runs `fvm dart format` on an
  edited `.dart` file.

### Skills (slash commands)

| Command | Source | Purpose |
|---|---|---|
| `/parse-api` | `parse-api` | Safe X API JSON parsing |
| `/port-from-squawker` | `port-from-squawker` | Port upstream Squawker fixes |
| `/translate` | `translate` | ARB / UI string changes |

All three exist in both `.grok/skills/` and `.claude/skills/`, byte-identical.
`scripts/check_skill_sync.sh` fails CI if the two trees drift apart.

If names collide, use the qualified form (e.g. `/local:parse-api`).

### Rewrite plan

See `docs/grok-rewrite-plan.md` for phases, characterization-test targets, and
compatibility checkpoints. Do not start Phase 2 UI rewrites until Phase 0–1
gates pass (clean debug APK + characterization coverage for selector / rate
limits / migrations / client parsers).

## Cursor Cloud specific instructions

This is an **Android-only** Flutter app (only `android/` exists — no `web/`,
`linux/`, etc.). The toolchain is pinned to Flutter **3.44.4** via FVM, so always
invoke Flutter as `fvm flutter` / `fvm dart` (see `CLAUDE.md` / `README.md`).

The Cloud VM snapshot already has: FVM + the pinned Flutter SDK (`~/fvm`), the
Android SDK (`~/android-sdk`, exported via `ANDROID_HOME`/`ANDROID_SDK_ROOT` in
`~/.bashrc`), Java 21, and a Python venv at `.venv` for icon generation. `fvm` is
symlinked into `/usr/local/bin`. The startup update script runs `fvm install`,
`fvm flutter pub get`, and the two pure-Dart codegen steps below.

### Verifying the environment (what works here)
- One-shot: `bash scripts/cloud_verify.sh` (analyze + tests + debug APK).
- Lint: `fvm flutter analyze` (expect ~44 `info` lints, no errors).
- Tests: `fvm flutter test` (pure-Dart unit tests under `test/`; use in-memory
  sqflite — these exercise core deep-link parsing and feed dedup/caught-up logic).
- Live guest API (optional):
  `fvm flutter test test/live/guest_api_smoke_test.dart --dart-define=RUN_LIVE=true`
- Build: `fvm flutter build apk --debug` → `build/app/outputs/flutter-apk/app-debug.apk`.
- Details: `docs/cloud-testing.md`.

### Running the app UI is NOT possible in this VM without a phone
There is no `/dev/kvm`, so a usable Android emulator is not available (software
emulation can start but Package Manager / boot stay broken). No physical device is
attached by default, and the app is Android-only (the `linux`/`chrome` devices
`flutter` lists are unusable — no platform folders and Android-only plugins).

Interactive UI testing needs a real device via wireless ADB:
`bash scripts/adb_wireless_connect.sh <pair_host:port> <code> <connect_host:port>`
then `adb install -r build/app/outputs/flutter-apk/app-debug.apk`.

The reverse-engineered X client *can* be exercised headlessly without the UI: the
guest path in `lib/client/client_unauthenticated.dart` is pure Dart (`http`), so
`test/live/guest_api_smoke_test.dart` hits live x.com guest auth + a profile fetch.
x.com egress works here.

### Non-obvious gotchas
- **`compileSdk 37` platform fix.** `android/app/build.gradle` uses `compileSdkVersion 37`,
  but `sdkmanager` only ships `platforms;android-37.0` (its `AndroidVersion.ApiLevel=37.0`),
  which this project's AGP resolves as hash `android-37` and fails to find. The snapshot
  contains a fixed copy at `~/android-sdk/platforms/android-37` with `AndroidVersion.ApiLevel=37`.
  If a fresh SDK ever lacks it: `cp -r ~/android-sdk/platforms/android-37.0 ~/android-sdk/platforms/android-37`
  then edit `source.properties` to `AndroidVersion.ApiLevel=37`. Gradle prints a harmless
  "inconsistent location" warning for `android-37`; ignore it.
- **`dart run dart_pubspec_licenses:generate` fails** under Flutter 3.44.4 + FVM
  (`PathNotFoundException: .../3.44.4/version` — the SDK dropped the legacy `version`
  file). Its output `lib/oss_licenses.dart` is **not imported** (the app uses Flutter's
  built-in `showLicensePage`), so this step is safe to skip. It is intentionally left
  out of the update script.
- **Generated code is gitignored** (`lib/generated`, `lib/oss_licenses.dart`,
  `assets/icon-*.png`). `intl_utils:generate` (localization, imported everywhere) and
  `flutter_iconpicker:generate_packs --packs material` run in the update script. A full
  APK build also needs the launcher icons: `.venv/bin/python generate_icons.py` then
  `fvm dart run flutter_launcher_icons` (icon resources persist in the snapshot, so this
  is only needed when icons change).
