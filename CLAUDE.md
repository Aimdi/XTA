# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**XTA** (formerly Quacker) is a privacy-focused Flutter/Dart **read-oriented
frontend** for X (formerly Twitter), forked from Quacker/Fritter. It is **not**
X itself and does not implement posting: no compose, reply, quote, repost, or
server-side like. Accounts are used to *fetch* content; subscriptions, saved
posts, and device likes stay local in SQLite. There are no external trackers;
the app talks to reverse-engineered X API endpoints.

## Build & Development Commands

Use `fvm flutter` instead of raw `flutter` to enforce the pinned SDK version (3.44.4).

```bash
# Install the pinned Flutter SDK and activate it for this project
fvm install
fvm use

# Generate launcher icon assets
python -mvenv .venv
bash -c '
  source ./.venv/bin/activate
  pip install -r requirements.txt
  python generate_icons.py
'

# Run all build steps through fvm so the pinned SDK is used
fvm flutter pub get
fvm dart run flutter_launcher_icons
fvm dart run dart_pubspec_licenses:generate
fvm dart run intl_utils:generate
fvm dart run flutter_iconpicker:generate_packs --packs material
fvm flutter build apk --debug
```

## Architecture

### State Management

All state is managed with **flutter_triple** `Store<T>` objects. Each feature has a `*_model.dart` that extends `Store` and uses `execute()` for async operations. UI widgets observe these stores via `ScopedBuilder` / `TripleBuilder`. **Do not use setState or ChangeNotifier** — use the Store pattern throughout.

### Feature-based Structure (`lib/`)

Each feature folder contains its screen(s) and its model:

| Folder | Description |
|---|---|
| `client/` | X API client wrappers (authenticated + unauthenticated) |
| `database/` | SQLite repository, entity classes, schema migrations |
| `home/` | Home screen with tab navigation |
| `profile/` | User profile view |
| `tweet/` | Tweet card rendering, threads, video playback |
| `search/` | Search for tweets and users |
| `trends/` | Trending topics |
| `subscriptions/` | Followed users management |
| `group/` | Subscription groups (custom feeds) |
| `saved/` | Offline saved tweets |
| `settings/` | App preferences |
| `utils/` | Shared helpers (downloads, caching, deep linking) |
| `generated/` | Auto-generated localization — do not edit manually |

### API Layer (`lib/client/`)

The X API is **reverse-engineered** — endpoints, tokens, and headers may change without notice. Always use safe null-coalescing access when parsing JSON responses:

```dart
// Good — safe against missing fields
final text = result["data"]?["text"] as String?;
final count = result["legacy"]?["favorite_count"] as int? ?? 0;

// Bad — will throw if field is absent
final text = result["data"]["text"] as String;
```

For new parsing code, prefer the `Json` extension type in `lib/utils/json.dart`
— `Json(result)['data']['legacy']['favorite_count'].integer ?? 0` cannot throw
at any step, so the deep path is the safe path (see `/parse-api`).

`use_build_context_synchronously` is a build **error** here, not a hint: after
an `await`, guard with `mounted`/`context.mounted` or capture what you need
(messenger, navigator, strings) before the await.

The layer is split by responsibility:

| File | Responsibility |
|---|---|
| `transport.dart` | `QuackerTwitterClient` — the HTTP client every request goes through, owning account rotation and health recording |
| `endpoints.dart` | Registry of GraphQL operations and their query ids; the only place a query id is written |
| `endpoint_overrides.dart` | Fetches the published `endpoints.json` so a rotated query id can be repaired without a release |
| `client.dart` | The `Twitter` API surface — one method per thing the app asks X for |
| `timeline_parser.dart` | `TimelineParser` — turns timeline JSON into tweet chains |
| `tweet_models.dart` | `TweetWithCard`, `TweetChain`, `TweetStatus`, `Follows` and their parsers |
| `errors.dart` | The failure types `ui/errors.dart` renders |

`client.dart` re-exports the models, errors and transport, so importing it alone
still brings everything into scope. It wraps `dart_twitter_api` and adds caching
via `FFCache`. `client_unauthenticated.dart` uses a hardcoded bearer token from
`constants.dart`; `client_regular_account.dart` uses stored OAuth credentials.

**When X changes something**, the file to open is `endpoints.dart` (a rotated
query id) or `timeline_parser.dart` / `tweet_models.dart` (a reshaped response)
— not the whole client.

**Account selection strategy.** `QuackerTwitterClient.fetch()` in `transport.dart` does not pick a random account — it asks `AccountSelector` (`account_selector.dart`, a pure/testable policy) for a *healthy* account, then retries on another account on error. Two distinct health signals:
- **Rate limit (`429`)** is **per-endpoint** (X rate-limits per endpoint, not per account). It is tracked **in memory** by `RateLimitTracker` (`rate_limit_tracker.dart`), keyed by `(accountId, uri.path)`, with the reset time from X's `x-rate-limit-reset` header (else `rateLimitFallback`). Not persisted — windows are short. The selector receives this via an injected `isRateLimited` predicate.
- **Not-found (`404`)** is **per-account** and **persisted** (auth likely broken): flagged after `notFoundThreshold` consecutive 404s, for `notFoundCooldown`. Helpers `recordNotFound` / `recordAccountSuccess` live in `accounts.dart`; cooldown constants in `constants.dart`.

`AccountSelector.pick()` prefers healthy accounts but **falls back to flagged ones**, so a real request is always attempted while any account exists — the flags only influence ordering, they never short-circuit. Errors therefore surface only from actual responses, each with a dedicated widget in `ui/errors.dart` (all built on the shared `ActionableErrorWidget`, offering add-account + retry):
- every tried account was rate-limited on the endpoint → `RateLimitedException` (⏳);
- every tried account returned 404 (likely broken auth) → `NoWorkingAccountException` (🤷);
- there is no account at all → an unauthenticated (guest) request is attempted first; `NoAccountAvailableException` (🔑) is thrown only if that guest request also fails.

Any other error response is surfaced as-is via `HttpException`. Retry simply re-runs `fetch()`, which always attempts a real request before surfacing any error.

### Database (`lib/database/`)

`repository.dart` is the single access point for SQLite (via `sqflite`). Schema changes must go through `sqflite_migration_plan` migrations — never alter the schema outside of a migration. Key entities: `Subscription`, `SubscriptionGroup`, `SavedTweet`, `Account` (carries account-health columns for the selection strategy: rate-limit / not-found timestamps stored as ISO-8601 TEXT, mapped to `DateTime?`).

### Navigation

Routes are defined as constants in `constants.dart` (`routeHome`, `routeProfile`, etc.) and registered in `main.dart`. Deep links from x.com URLs are parsed in `utils/urls.dart` into sealed `ProfileUriInfo` / `PostUriInfo` classes, then navigated in `main.dart`.

### Localization

Strings live in `lib/l10n/*.arb` files. The `L10n` class in `lib/generated/l10n.dart` is auto-generated — run `fvm dart run intl_utils:generate` after editing ARB files. Access via `L10n.of(context).someKey`.

### Coding Style

- Prefer functional patterns: immutable data, pure functions, `map`/`where`/`fold` over imperative loops. Avoid mutable state outside of Store objects.
- Always split responsibilities
- Avoid functions of more than 30 lines (except for some widget builders)
- NEVER insert raw strings in the code if they are displayed on the UI, always use translated strings in arb files
- Anytime when you are about to copy/paste code from somewhere, think about refactoring instead. Ask me first what to do in such cases.
- Go easy on comments. Avoid comments that are obvious or redundant, or that simply describe the code you're about to write.

## Hard Rules

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
- **Do not big-bang rewrite.** Rewrite UI/feature folders incrementally.
- **Never bump pinned deps** (`dart_twitter_api: 0.6.0`, the
  `dependency_overrides` block, Flutter **3.44.4** in `.fvmrc` / `pubspec.yaml`).
  They are load-bearing.

## Custom Skills

Skills live under `.claude/skills/`.

- `/parse-api` — guidance for safely parsing reverse-engineered X API responses
- `/port-from-squawker` — port a bug fix or feature from the Squawker codebase
- `/translate` — user asked anything about translation, or you tried to add/remove/edit a text that appears in the UI

## Enforced Guardrails

`.claude/settings.json` machine-enforces part of the hard rules above:

- `permissions.deny` — Edit/Write on `lib/generated/**` and `lib/oss_licenses.dart`.
- `permissions.ask` — Edit/Write on `lib/client/**` and `lib/database/**`.
- `PreToolUse` → `.claude/hooks/guard-pinned-deps.sh` denies edits that change
  `dart_twitter_api`, a `dependency_overrides` entry, or the Flutter version in
  `pubspec.yaml` / `.fvmrc`. Other pubspec edits pass.
- `SessionStart` → `.claude/hooks/session-start.sh` runs `pub get` +
  `intl_utils:generate` (non-fatal, skipped without `fvm`).
- `PostToolUse` → `.claude/hooks/format-dart.sh` runs `fvm dart format` on an
  edited `.dart` file.

## Verifying Changes

This is an **Android-only** Flutter app (only `android/` exists — no `web/`,
`linux/`, etc.).

- One-shot: `bash scripts/cloud_verify.sh` (analyze + tests + debug APK).
- Lint: `fvm flutter analyze`.
- Tests: `fvm flutter test` (pure-Dart unit tests under `test/`, using in-memory
  sqflite).
- Live guest API (optional):
  `fvm flutter test test/live/guest_api_smoke_test.dart --dart-define=RUN_LIVE=true`
- Build: `fvm flutter build apk --debug` → `build/app/outputs/flutter-apk/app-debug.apk`.
- Details: `docs/cloud-testing.md`.

Interactive UI testing needs a real device via wireless ADB (there is no
`/dev/kvm` for an emulator in cloud VMs):
`bash scripts/adb_wireless_connect.sh <pair_host:port> <code> <connect_host:port>`
then `adb install -r build/app/outputs/flutter-apk/app-debug.apk`.

### Non-obvious gotchas

- **`compileSdk 37` platform fix.** `android/app/build.gradle` uses `compileSdkVersion 37`,
  but `sdkmanager` only ships `platforms;android-37.0` (its `AndroidVersion.ApiLevel=37.0`),
  which this project's AGP resolves as hash `android-37` and fails to find. Fix:
  `cp -r ~/android-sdk/platforms/android-37.0 ~/android-sdk/platforms/android-37`
  then edit `source.properties` to `AndroidVersion.ApiLevel=37`. Gradle prints a harmless
  "inconsistent location" warning for `android-37`; ignore it.
- **`dart run dart_pubspec_licenses:generate` fails** under Flutter 3.44.4 + FVM
  (`PathNotFoundException: .../3.44.4/version` — the SDK dropped the legacy `version`
  file). Its output `lib/oss_licenses.dart` is **not imported** (the app uses Flutter's
  built-in `showLicensePage`), so this step is safe to skip.
- **Generated code is gitignored** (`lib/generated`, `lib/oss_licenses.dart`,
  `assets/icon-*.png`). `intl_utils:generate` and
  `flutter_iconpicker:generate_packs --packs material` must run before analyzing or
  testing. A full APK build also needs the launcher icons:
  `.venv/bin/python generate_icons.py` then `fvm dart run flutter_launcher_icons`.

## Installed design skills

For UI layout, compactness, placement, and visual hierarchy work, read
`docs/xta-design-skills.md` first. The installed `impeccable` and `ui-ux-pro-max`
skills live under `.claude/skills/`; `python3 scripts/check_design_skills.py`
verifies them.

Use Impeccable's critique/distill/layout workflow and UI UX Pro Max's Flutter
stack guidance to refine existing XTA surfaces. Preserve features, true-black
support, native conventions and accessible touch targets. These skills do not
override any existing hard rule above, authorize a redesign, or authorize a
merge/release. No automatic hooks are installed.
