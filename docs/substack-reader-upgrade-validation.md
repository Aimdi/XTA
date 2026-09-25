# Substack reader upgrade

Implemented on `codex/substack-reader-upgrade`, building on the completed X,
Mastodon and Bluesky changes at `d499ede`. Public reading and device-local
following, likes, saves and read state remain the plugin's model.

## What changed

| Area | Result |
| --- | --- |
| Article reader | Contents navigator, find-in-article with matching passages, same-article anchor navigation, explicit refresh that retains a readable copy on fetch failure, main-frame load recovery and RTL/table improvements. |
| Discovery | Full-screen publication/category discovery with pagination and recent searches; article search across followed publications with bounded concurrency and partial-failure retry. |
| Adding publications | Pasted publication/article URLs open a preview; following is explicit, and a failed article preview keeps the usable publication preview. |
| Publication pages | Paginated publication search, loaded-article filters and ordering, local pins, expandable metadata and recoverable recommendations. Local publication identity survives metadata enrichment. |
| Home and Inbox | Loaded-article search, newest/oldest/popularity order, videos filter, individual read/unread actions, immediately updated unread filtering and paging beyond an empty loaded Inbox. Controls survive tab changes. |
| Library | Search and selected section survive tab changes; publication follow changes reconcile with Home on return. |
| Comments and Notes | Hierarchical comments with collapse/expand, contextual search and sibling ordering; selectable linked text, shared Note presentation and full-screen images. |
| Reliability | Per-publication offsets, fair bounded feed fetching, retained results on failures, stale-response protection, host-bound Notes cursors, serialized local preference writes and recoverable RSS fallback pages. |
| Accessibility/localization | Compact toolbar overflow, wrapping card actions, enlarged-text/RTL layout checks and 19 new labels across all 29 supported locales. |

The gap analysis prioritized long-form navigation, finding articles, managing an
unread queue and understanding discussions. Substack's public reader workflow
and Readwise Reader's navigation/library patterns informed these choices. The
six `docs/specs/substack-*` specifications record scope and reference links.

The existing public archive API serves article search within a publication;
cross-publication search is explicitly limited to locally followed publications.
Paid previews remain limited to content returned by the public endpoints.

The shared article progress bridge adds an optional navigation hook that stops
initial scroll restoration after an explicit jump without marking an article
finished. Other readers retain their existing behavior. The verification workflow
now names all nine new Substack regression files.

## Validation

Validation uses pinned Flutter 3.44.4 / Dart 3.12.2 and deterministic fixtures.
The upgrade adds 143 tests over the completed Bluesky checkpoint.

- Final repository suite: 2,985 passed, 5 opt-in live tests skipped, zero failures (2m30s).
- Static analysis: zero errors, zero warnings; 136 existing information-level notices.
- All 17 newly added Dart files pass the 120-column formatting gate.
- ARB validation and skill-tree synchronization pass. All 19 new labels are
  present in every locale; previous translation values are unchanged. The
  pre-existing 17 untranslated keys and placeholder warnings remain unchanged.
- Frozen `lib/client/`, `lib/database/`, pinned dependencies and the Flutter
  version are unchanged from the Bluesky checkpoint.
- Focused tests cover request encoding and malformed public metadata, bounded
  concurrency, retry offsets, stale responses, RSS paging, local-write races,
  cache identity, navigation and empty/read-filtered views.
- The full suite caught a repeated empty-feed refresh resetting its timestamp;
  the fix preserves the first timestamp and passes the unchanged regression.
- Rendered contents, find, publication, comments, Notes and Home fixtures were
  inspected, including 320px width, 200% text and RTL variants.

## Validation limits

No Android SDK or attached device is available in this workspace, so no APK
build or on-device WebView validation was performed. The native navigation
sheet and HTML fixtures are tested; contents and find apply to the body rendered
by XTA, while the live publication-page fallback does not expose those native
navigation tools. Physical-device checks are still needed
for platform WebView jumps and main-frame error callbacks.

Live API smoke tests were not enabled. Reverse-engineered public Substack routes
were reused and tested with fixtures; service availability still depends on
Substack. No authentication, remote posting or paywall bypass was introduced.
Some Arabic glyphs are unavailable in the headless screenshot font; the RTL
layout checks pass and readable English previews accompany those fixtures.

All changes are committed locally. Remote publication has not been performed;
the earlier automatic approval review required explicit authorization to publish
source to GitHub. The delivery archive includes a Substack-only patch from
`d499ede` and a combined patch from public `claude/main` at
`1d1ca2100979e888153858c3c0f42d9138ba2cbf`, preserving the earlier upgrades.
