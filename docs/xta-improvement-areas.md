# XTA improvement areas

Multi-agent audit (2026-07-30) of where **XTA** (Aimdi/XTA — read-only QuaX
fork) could improve, and what it still needs. Sources: codebase + specs,
upstream [Teskann/QuaX](https://github.com/Teskann/QuaX) issues, recent XTA
releases (`aimdi71`–`aimdi72`), and `docs/grok-rewrite-plan.md`.

**Product hard rule (unchanged):** XTA is a **read-oriented** X frontend. Do
not add compose / reply / quote / repost / like-on-X / DMs / Spaces hosting.
Local likes, saves, and subscriptions stay on-device.

---

## Executive summary

XTA already ships a strong reader (groups, zen mode, advanced search, plugins,
X-look theme, measurable scroll wins in aimdi72). The highest-value remaining
work is not more chrome — it is:

1. **Feed reliability at scale** (rate limits, large subscription sets, refresh)
2. **API survival tooling** (query-id / txn-id breaks still look like “404”)
3. **Finishing planned product polish** (unread groups, Discover IA, video UX)
4. **Engineering gates** (transport characterization, device perf baselines)
5. **Docs hygiene** (stale specs, QuaX-gamma links on older branches)

---

## P0 — User pain (upstream signal + XTA fit)

Clustered from the **29 open** Teskann/QuaX issues. Aimdi/XTA currently has
**0 issues** of its own — treat upstream as the user backlog.

| Priority | Need | Upstream | Why XTA should care |
|---|---|---|---|
| P0 | Large subscription sets break Following / groups | [#165](https://github.com/Teskann/QuaX/issues/165) | Power users of this fork hit this first |
| P0 | Subscribe/unsubscribe immediately rate-limits | [#170](https://github.com/Teskann/QuaX/issues/170) | Multi-account health helps; UX still painful |
| P0 | Feed gap / “continue search” when paging ends | [#27](https://github.com/Teskann/QuaX/issues/27) (12 comments) | Fork already has gap-filling; verify edge cases |
| P0 | Duplicate posts in feeds | [#169](https://github.com/Teskann/QuaX/issues/169) | Dedup exists for groups — close remaining holes |
| P0 | Pull-to-refresh broken when default tab is “For you” | [#168](https://github.com/Teskann/QuaX/issues/168) | Home-tab product bug |
| P1 | Remember last read position (Following / For You) | [#113](https://github.com/Teskann/QuaX/issues/113) | Groups have caught-up; home tabs still don’t |
| P1 | Video fullscreen cannot exit (must kill app) | [#116](https://github.com/Teskann/QuaX/issues/116) | Hard bug; aligns with recent video work |
| P1 | Progressive video buffering / less scroll download | [#162](https://github.com/Teskann/QuaX/issues/162) | aimdi71 reclaimed off-screen players; data use remains |
| P2 | Universal filter defaults for all groups | [#158](https://github.com/Teskann/QuaX/issues/158) | Fork has feed defaults — confirm it fully closes this |
| P2 | Share sheet / SAF / TalkBack / copy whole post | [#84](https://github.com/Teskann/QuaX/issues/84), [#111](https://github.com/Teskann/QuaX/issues/111), [#125](https://github.com/Teskann/QuaX/issues/125), [#138](https://github.com/Teskann/QuaX/issues/138) | Smaller Android platform asks |
| Deferred | Notifications | [#146](https://github.com/Teskann/QuaX/issues/146), [#121](https://github.com/Teskann/QuaX/issues/121) | Tried and removed on this fork — only revisit with a reliable design |
| Out of scope | Communities, Spaces, posting/interactions, Material-expressive redesign | [#93](https://github.com/Teskann/QuaX/issues/93), [#55](https://github.com/Teskann/QuaX/issues/55), [#139](https://github.com/Teskann/QuaX/issues/139) | Product / ROI constraints |

---

## P0 — API / reliability (when X breaks)

`lib/client/` and `lib/database/` stay frozen for UI rewrites — but **live API
breaks** still need narrow patches here.

| Severity | Gap | Where |
|---|---|---|
| S1 | Query-id rotation → HTTP 404 until repaired; published `endpoints.json` is currently empty | `endpoints.dart`, `endpoint_overrides.dart`, `endpoints.json` |
| S1 | `x-client-transaction-id` scrape is a single point of total failure (same 404 symptom) | `x_client_transaction_id/*`, `headers.dart` |
| S2 | Hardcoded GraphQL `features` / field toggles can empty timelines without rotating ids | `client.dart` |
| S2 | Remaining non-null-safe parser paths can wipe a whole page | `timeline_parser.dart` (`getCursor`, `_createTweetsGraphql`) |
| S3 | Transport stops after two 404s; guest path lacks txn-id | `transport.dart`, `client_unauthenticated.dart` |
| S4 | Login cookie/HTML capture + `HttpException` → raw stack in UI | `login_webview.dart`, `ui/errors.dart` |

**Already strong:** account selector, rate-limit tracker, endpoint registry,
daily `api-canary.yml`, actionable account/rate-limit/endpoint-refused widgets,
parser resilience tests, guest smoke.

**Highest leverage without a rewrite:** keep canary + `endpoints.json` green;
harden remaining unsafe parser chains; characterize `QuackerTwitterClient.fetch`
with mocked HTTP; expand fixtures to Search / Home / TweetDetail; map more HTTP
failures to actionable UI (not stack dumps).

---

## P1 — Product / UX polish

| Opportunity | Status | Direction |
|---|---|---|
| Search vs Trends navigation confusion | Open | Bottom tab labeled Search but is Trends + push-to-search; empty trends state is blank |
| Groups unread Badge from `feed_read_position` | Spec’d, deferred | Highest-value leftover from `groups-grid` / `groups-mark` |
| Tweet leftover rewrite | Spec’d open | Header extract; move `TweetContextState` out of `profile.dart`; null-safe `_card.dart` |
| Profile rough edges | TODOs in code | Safe scroll-to-top, stable header measure, skeletons |
| Rich cards open externally / fail silently | TODOs in `_card.dart` | In-app viewers or graceful unsupported UI; null-safe bindings |
| Local-only heart/bookmark discoverability | Soft | Persistent “on device” cue without adding posting |
| Accessibility beyond text scale | Partial | Semantics on tiles/tabs/media; TalkBack empty states |
| Phase 2 rewrite after `tweet/` | Not started | Spec then implement: home → profile → search → group → saved → settings |
| Plugin depth | Partial | Reddit comments shipped (spec stale); Substack interleaving shipped; true sealed `FeedItem` union optional |
| Theme docs drift | Docs | X-look shipped; Fairy Forest / Pitch Black retired — README/specs may still mention them on older branches |

---

## P1 — Engineering needs

| Gap | Risk |
|---|---|
| No end-to-end test of `QuackerTwitterClient.fetch` | Highest characterization hole after Phase 1 gate |
| Phase 1b device perf baselines still TBD | Cannot honestly claim scroll/cold-start wins without phone rows in `docs/perf-baseline.md` |
| Zero tests for `search/` and `trends/` | Silent empty tabs on API drift |
| `setState` / `ChangeNotifier` still widespread vs Store-only rule | Rewrite friction |
| `dart_pubspec_licenses:generate` broken under FVM 3.44.4 but still in some scripts | Footgun (output unused) |
| Reproducible-build script not yet run | F-Droid / distribution blocker remains |
| CI: `ci.yml` builds APK without tests; `verify.yml` is the real gate | Keep PR verify green; consider aligning push builds |
| L10n: all 28 locales have keys, but many drop ICU placeholders | Incomplete translated copy at runtime |
| Specs outdated | `reddit-plugin.md`, `substack-in-groups.md` understate what shipped |

**Do not:** bump Flutter **3.44.4** or `dart_twitter_api: 0.6.0`; big-bang rewrite
`lib/client/` or `lib/database/`; re-add notifications without a new design.

---

## Recommended next work (ordered)

1. **Feed scale & rate-limit UX** — reproduce #165 / #170 / #168; harden paging, refresh, and subscription churn paths.
2. **Transport characterization tests** — mock HTTP for 429 / mixed 404 / guest / `EndpointRefused` / success clearing flags.
3. **Groups unread dots** — cheap product win on the shipped grid.
4. **Finish tweet.md leftovers** — unlock cleaner home/profile rewrites.
5. **Video fullscreen exit (#116)** — hard bug; validate on device with aimdi71+ player reclaim.
6. **Home last-read position (#113)** — extend caught-up beyond groups.
7. **Fill Phase 1b perf baselines** on a mid-range handset.
8. **Refresh stale specs + README links** so planning does not re-litigate shipped work.
9. **Discover IA** — unify Search / Trends empty states and entry points.
10. **Authenticated fixtures** for SearchTimeline / HomeTimeline / TweetDetail.

---

## What XTA does *not* need

- Posting parity with official X (compose, reply-to-X, quote, repost, like-on-X)
- Communities / Spaces hosting
- A full Material 3 expressive redesign unless explicitly scoped
- Rewriting the client/database layers as part of UI work
- Re-adding notifications until reliability and privacy story are clear
- Dependency bumps of the pinned Flutter / `dart_twitter_api` stack

---

## Audit method

Four parallel explore agents covered:

1. UI/UX + specs backlog (`lib/{home,tweet,profile,search,…}`, `docs/specs/`)
2. Client/API reliability (`lib/client/`, canary, errors)
3. Tests / tech debt / CI (`test/`, workflows, migrations note)
4. Upstream issues + branch/release/spec reality check (`gh` + changelog)

Re-run this audit after a major API break or after Phase 2 `home/` lands.
