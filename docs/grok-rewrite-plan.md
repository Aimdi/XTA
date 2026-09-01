# Grok Build 4.5 — XTA incremental rewrite plan

Durable plan for rewriting XTA's **UI/feature layer** with Grok Build
(grok-4.5). This is **not** a big-bang rewrite.

## Context (July 2026)

On **2026-07-20**, X shipped a from-scratch Android app rewrite (Kotlin + Jetpack
Compose). That client rewrite does **not** directly break XTA: this app is a
separate Flutter client talking to X's reverse-engineered backend API. The real
compatibility risk is server-side (GraphQL `doc_id` rotation, transaction-ID
schemes, rate limits) — not the new Android UI.

## Non-goals

- Do **not** rebuild `lib/client/` or `lib/database/` as part of a UI rewrite.
- Do **not** chase visual parity with X's new Android app unless explicitly scoped.
- Do **not** plan around Musk open-sourcing X — no timeline.
- Do **not** turn XTA into a full X clone. It is a **read-oriented frontend**:
  browse feeds, profiles, search, and media. Never add compose / reply-to-X /
  quote-post / repost / like-on-X / DMs / Spaces hosting. Local-only features
  (SQLite subscriptions, saved folders, on-device likes) are intentional and
  must not be rewired to X write APIs. UI rewrite means clearer *viewer*
  chrome — not posting parity with the official app.

## Phase 0 — Tooling port (done in this change set)

1. Confirm Grok discovers instructions: `CLAUDE.md`, `AGENTS.md`, `.claude/skills/`,
   `.grok/skills/` via `grok inspect`.
2. Native Grok skills mirrored under `.grok/skills/` (`parse-api`,
   `port-from-squawker`, `translate`) so they appear as slash commands.
3. Worktree hygiene documented in `AGENTS.md` (manual `git worktree`, one module
   per session).
4. Reproducible debug APK gate: full `fvm` build chain before any rewrite.

## Phase 1 — Characterization tests (lock behavior first)

**Status: core gate met** (see `docs/specs/phase1-characterization.md`).

Coverage now includes:

| File | Covers |
|---|---|
| `test/clean_url_test.dart` | URL tracking-param stripping |
| `test/list_url_test.dart` | List deep links |
| `test/feed_read_position_test.dart` | Feed dedup / caught-up |
| `test/account_selector_test.dart` | Healthy vs 404 cooldown / 429 injection |
| `test/rate_limit_tracker_test.dart` | Per-(account, endpoint) windows |
| `test/migration_test.dart` | `buildMigrationPlan()` v22 → current |
| `test/client_parser_test.dart` | Live UserByScreenName / tweet fixtures |

Live fixtures live under `test/fixtures/`. Optional authenticated timeline
fixtures can land later without blocking Phase 2.

## Phase 1b — Perf baseline (before more UI work)

Record cold start, scroll jank, and APK size in `docs/perf-baseline.md`.
Device rows are TBD in Cloud VMs (no emulator / phone); fill on a mid-range
handset with `flutter run --profile --trace-startup` and DevTools.

## Phase 2 — Incremental UI/feature rewrite (+ performance)

One feature folder per worktree / Grok session. Order:

1. `tweet/` (most reused) — PR-1 chrome/L10n + PR-2 footer extract
   (`docs/specs/tweet.md`) + **perf pass** (`docs/specs/tweet-perf.md`)
2. `home/`
3. `profile/`
4. `search/`
5. `group/`
6. `saved/`
7. `settings/`

Perf techniques (priority): `const`, image `cacheWidth`, `RepaintBoundary` on
media, `ListView.builder` (no reckless `cacheExtent` with live GIF players),
parsing out of `build()`, small `ScopedBuilder`s, skeleton loaders.

Workflow per module:

1. Plan mode → write a module spec under `docs/specs/<module>.md` → commit.
2. Implement against the spec; keep `flutter_triple` Stores and ARB discipline.
3. `/compact` between modules; `/memory` + `/flush` for durable decisions.

## Phase 3 — X-look theme (switchable)

`ThemeExtension` token layer + Settings presets (Light / Dim / Lights-Out).
Spec: `docs/specs/x-look-theme.md`. Existing themes remain. Inter font (never
bundle Chirp). No hardcoded colors in widgets — token lookups only.

## Phase 4 — Compatibility / verify

After any change near `client/` **or** a UI/perf module:

1. `fvm flutter analyze` + `fvm flutter test` + `fvm flutter build apk --debug`.
2. Install on a real device, log in with a real X account (no pure-guest product
   mode), exercise timeline / profile / search / media / 429–404 retry paths.
3. Re-run Phase 1b traces; require fewer dropped frames, cold start not worse,
   APK not materially bigger — else revert the module worktree.
4. Prefer upstream fixes via `/port-from-squawker` (`Teskann/QuaX`,
   `j-fbriere/squawker`) when X breaks auth or GraphQL docs.

## Plan-changing benchmarks

| Trigger | Response |
|---|---|
| X open-sources official clients / endpoints | Re-evaluate a real client-layer rewrite |
| Client-side request-signing change breaks XTA | Drop UI work; API-parity spike in `lib/client/` first |
| Dependency-override stack collapses on Flutter bump | Dedicated dependency modernization phase |

## ToS note

Automating account access with a third-party X client may violate X's ToS and risk
account action. Treat live verification carefully.
