# tweet/ module — Phase 2 performance pass

Incremental **performance** work on `lib/tweet/` only. Complements the chrome /
footer PRs in `docs/specs/tweet.md`. Does **not** touch `lib/client/` or
`lib/database/`.

## Product constraint

XTA is a **read-oriented** X frontend. No compose / reply-to-X / quote /
repost / like-on-X. Footer affordances stay navigation / local.

## Goals (by priority)

1. **Image decode caps** — timeline photos use `extended_image` `cacheWidth`
   from layout width × DPR. Fullscreen / gesture viewer keeps full-res.
2. **`RepaintBoundary`** around media (and skeleton loaders) so scrolling
   tiles do not repaint video / image layers unnecessarily.
3. Feeds stay **`ListView.builder` / `PagedListView`**. Do **not** bump
   `cacheExtent` (GIF `alwaysPlay` creates native players on build — see
   `d66b60b`).
4. **Skeleton** first-page indicator instead of a lone spinner.
5. Extra **`const`** on eligible chrome / skeleton widgets.
6. JSON/model work stays out of `build()` (already true for GraphQL parsing).

## Out of scope

- Video stack (`_video.dart`, pool, audio focus) — visibility-gate later.
- Raising `cacheExtent` until players are visibility-gated.
- Header extract / `_card.dart` null-safety (separate PRs in `tweet.md`).

## Files

| File | Change |
|---|---|
| `lib/tweet/_photo.dart` | Layout-based `cacheWidth` when not in page view |
| `lib/tweet/_media.dart` | `RepaintBoundary` around media item / grid |
| `lib/tweet/_card.dart` | Decode cap on card images when width known |
| `lib/tweet/tweet_skeleton.dart` | New skeleton tiles |
| `lib/tweet/paginated_tweet_list.dart` | Use skeleton for first-page progress |
| `lib/tweet/tweet_chrome.dart` | Optional token-aware divider when X-look present |

## Acceptance

- `fvm flutter analyze` — no new errors in touched files.
- `fvm flutter test` green.
- Debug APK still builds.
- Behavior of like / save / share / translate / media tap unchanged.
