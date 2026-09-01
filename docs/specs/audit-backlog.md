# Audit backlog

What six audits of this codebase turned up, what has been done, and what has
not. Anchors are `file.dart:line` at the time of writing — re-grep rather than
trusting them after a refactor.

The findings were made by reading. The code that came out of them has since
been analysed and tested against the pinned SDK (Flutter 3.44.4 / Dart 3.12.2,
fetched into a scratch directory): `flutter analyze` reports zero errors and
zero warnings, and `flutter test` passes.

What still has not happened is measurement. Nothing here was profiled, so every
"measurable win" is a mechanism argument, and a real device baseline
(`docs/perf-baseline.md`, still TBD) would be worth more than any of them. Nor
has an APK been built here: the Android SDK host is denied by this
environment's network policy, so the Gradle side is only ever exercised in CI.

## Done

Render and rebuild cost: tile keys at every chain-list site, memoized text
truncation, footer label measurement cache, unified-card decode out of
`build()`, single-image posts no longer building a `PageView`, one skeleton
ticker per tile, `ScopedBuilder` leaves instead of `Consumer` over a plain
`Provider`.

Startup: the stored chunk reads take a `LIMIT`, the migration purge runs once
rather than twice, libmpv and the plugin fleet are no longer constructed before
the first frame, `Logger.root.level` is set, the X transaction key is warmed
alongside startup.

Requests: pull-to-refresh deletes only the refreshed group's rows, the follow
import resolves its user id once, Substack and Reddit are fetched concurrently
and skipped when their plugin is off, search queries the visible tab only, a
reorder no longer drops every cached feed, the media lookahead is capped on the
group fan-out, profiles are cached for five minutes.

Correctness found along the way: the media frame was unbounded and reached
through `sizes!.large!`; every account row shared one `Dismissible` key; a
failing migration returned an `Object` from a `Future<bool>`; saved and liked
membership was empty until the Saved tab was first opened; a group's mute list
only applied when it happened to be sorted by Custom.

Backup covered 8 of 18 tables; it now covers 14, with a format version.

Since a toolchain became available: the fan-out is bounded to four concurrent
searches, the session feed cache is LRU-bounded, timeline photos no longer
build the zoom stack, and the three tables the startup purge deletes from are
indexed on `created_at` (migration, additively, tests green).

A `timeline_cache` row cap is still open — the index is in, the bound is not.

Since then: the Reddit plugin reads every surface through one shared
`RedditPostSource`, so a subreddit followed in the tab, the home timeline and a
group is fetched once; refusals from the anonymous route are named
(`RedditPageKind`) instead of all reading as one error, and a verdict ends the
route search rather than asking two more hosts to agree.

Backup covered 14 of 18 tables; it now covers 17, and a test compares the
document against `sqlite_master` so the next table cannot be missed quietly.
`immich_upload` is the one deliberate omission, for the reason recorded in
`test/backup_coverage_test.dart`.

Correctness since: a Substack post could run JavaScript in the reader (the
sanitiser dropped `script` elements but left every `on…` attribute, in a web
view with scripting on); deleting an X account left its session cookies on the
device, so the account was still signed in as far as x.com was concerned; every
full-page error screen overflowed at twice the text size and took its own retry
button off the bottom of the screen; and the release workflow, run by hand,
built an APK stamped `main` and published nothing.

The first-run and sign-in dialogs said two things the code contradicts — that
the app does not work without an account, and that accounts are picked
"randomly" — in all 29 locales. Both are corrected.

## Not done — worth doing

**Fewer gap-fill pages.** Passing `limit: 40` to `Twitter.searchTweets` (the
parameter exists, defaulting to 20) would cut how many gap-fill pages a first
load needs. Not done: it changes response size and parse cost per request, so
it wants measuring rather than assuming.

**Icon tree-shaking.** Group icons are deserialized at runtime
(`group_model.dart:19-31`), so `--no-tree-shake-icons` is on in all four
workflows and the full `MaterialIcons-Regular.otf` (~1.5-1.7 MB) ships. A
curated `const IconData` set would let it come off; stored icons already fall
back through `deserializeIconData`. Needs a product decision about which icons
a reader may pick.

**Feed tiles are `SelectableText`**, i.e. an `EditableText` per post: a focus
node, a text controller, a scroll controller and a `RenderEditable` each. Making
selection opt-in on the status screen only is a behaviour change, so it needs a
decision rather than a patch.

## Not done — UX

Nothing at the moment. The first-run dialog now explains up front why accounts
help and what signing in does and does not allow, so the "explanation only
after committing to log in" complaint no longer applies.

Three entries here were wrong and have been dropped rather than fixed: the
drawer is reachable (`_account_avatar.dart:54` calls `openDrawer`); a rate
limit and broken auth render as distinct widgets (`ui/errors.dart` maps each
exception to its own screen); and the audits that claimed otherwise had simply
not looked. Re-grep before trusting the rest of this file for the same reason.

## Considered and rejected

Anything that writes to X. Any telemetry, including opt-in aggregated
endpoint-failure reports. Scraper-based plugins (Instagram, TikTok). Threads now
has an optional direct session mode (see `threads-direct.md`); Instagram/TikTok
remain deferred.
`placeInterleaved`'s bucket allocation, which looks like a wasted `List.generate`
but is required by its contract.
