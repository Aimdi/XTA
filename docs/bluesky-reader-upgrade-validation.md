# Bluesky reader upgrade

Implemented on `codex/bluesky-reader-upgrade`, building on the completed X and
Mastodon changes at `92570a5`. Public reads and local follows/likes remain the
plugin's model; no authentication or remote posting was introduced.

## What changed

| Area | Result |
| --- | --- |
| Following | Saved reading position with a bounded 144-post snapshot, restored tab, loaded-post search, photos/videos/links filters, hide replies/reposts, stable sort choices, visible retry and remaining-account actions. |
| Search | Full-screen People/Posts results with separate pagination, Latest/Top ranking, author/tag filters, direct post URL/AT URI lookup, recent search management and profile-scoped search. |
| Custom feeds and lists | Searchable feed catalog, creator/description metadata, local pins, saved selection, per-source page caches, explicit pagination/retry and preserved generator ranking. |
| Profiles | Pinned post with separate recovery, author search, clickable Unicode-safe biography links, selectable handles, post counts and independent tab pagination. |
| Conversations | Author-thread focus, original/date/popularity sorting, expand/collapse all, jump to selected post and cycle-safe branch traversal. |
| Cards | Protocol-aware warnings and inaccessible-content covers, protected quote previews, explicit quote navigation, accessible engagement and media ALT labels. |
| Reliability | Repost activity time, refresh repainting for count/label changes, progress past failed account batches, stale-response and AppView guards, safe snapshots and exact UTF-8 facet text. |
| Accessibility/localization | Compact toolbar overflow, wrapping controls, 320px/200%-text/RTL checks and 19 new strings in all 29 supported locales. |

The existing Mastodon anchor layout was extracted into `PluginReadingView`;
Mastodon keeps its public adapter and all 17 relevant regressions pass.
The verification workflow now names the nine Bluesky regression files.

## Design sources

Graysky's feed-first reading, deck.blue's independent feeds/list controls,
Klearsky's reading customization, and Bluesky's official public lexicons
informed the work. Implementation is native to XTA's existing Store,
localization, card and plugin architecture. Details and primary-source links
are in the six `docs/specs/bluesky-*` upgrade specifications.

## Validation

Validation uses pinned Flutter 3.44.4 / Dart 3.12.2 and deterministic fixtures.
The upgrade adds 126 regression tests over the completed Mastodon checkpoint.

- Full repository suite: 2,842 passed, 5 opt-in live tests skipped, zero failures (2m20s).
- Static analysis: zero errors, zero warnings; 136 existing information-level notices.
- All 15 newly added Dart files pass the 120-column formatting gate.
- ARB validation and skill-tree synchronization pass; every previous translation
  remains unchanged. Existing unrelated translation gaps are unchanged.
- Frozen `lib/client/`, `lib/database/`, pinned dependencies and Flutter version
  are unchanged from the Mastodon checkpoint.
- Restart integration verifies the same visible post within 1px and zero
  author-feed requests, with filters retaining the original offline source.
- Rendered Following, filter sheet, profile, conversation and media fixtures
  were inspected, including compact enlarged RTL and dark variants.

## Validation limits

No Android SDK or attached device is available in this workspace, so no APK
build or on-device runtime claim is made. Live authenticated or public API
smoke tests were not enabled; network behavior is checked with fixtures and
request-encoding tests against the documented public contracts. Search
capabilities remain dependent on the configured AppView.

All changes are committed locally. Remote publication has not been performed;
the prior automatic approval review required explicit authorization to publish
source to GitHub. The delivery archive includes a Bluesky-only patch from
`92570a5` and a combined patch from public `claude/main` at
`1d1ca2100979e888153858c3c0f42d9138ba2cbf`, so earlier X/Mastodon work is preserved.
