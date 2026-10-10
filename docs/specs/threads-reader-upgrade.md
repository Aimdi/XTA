# Threads reader upgrade

Read-only, like every earlier Threads pass (`threads-direct.md`,
`threads-polish.md`, `threads-app-like.md`). Nothing here writes to Meta: no
compose, reply, repost, remote like or follow. Likes stay on the device.

## Product direction

Make the Threads plugin read like the Threads app instead of a list of
captions. Posts should carry what Meta already sends — video, quotes, alt text,
resolved links, topic tags, the author's own thread — and conversations,
profiles and the feed should survive a restart, a refresh and a flaky network
without blanking or asking Meta more often.

## Implemented

| Area | Result |
| --- | --- |
| Video | `video_versions` (top level and per carousel item) are parsed; the largest rendition plays in place through the shared `TweetVideo` player, so autoplay, mute and data preferences apply. Posters stay the tile image. |
| Quotes | `share_info.quoted_post` / `quoted_attachment_post` become `ThreadsPost.quoted`, one level deep, shown as a boxed card that opens its own conversation. Quote-only posts are no longer dropped. |
| Captions | `text_fragments` drive mentions (exact account), links (unwrapped `l.threads.com` targets behind shortened display text) and tags — but only when the fragments spell the caption exactly; otherwise the pattern-based caption is used. |
| Topic tags | `tag_header` shows as "› Tag" beside the handle and opens tag search on Threads. |
| Alt text | `accessibility_caption` reaches the shared media tile (ALT badge, long-press, viewer action, semantics). |
| Self-threads | A profile/feed bucket's continuation by the same author is counted; cards offer "Show N more posts in this thread". |
| Conversations | Post pages are read as chains: what the post answers, the post, the author's continuation, then each reply thread joined by rails. Long reply threads are cut to three posts until opened; an "Author's replies" filter keeps threads the author joined. Store-based (`ThreadsThreadStore`), retry keeps the tapped card. |
| Links | Short `/t/CODE` links open in-app; focus is matched by short code, so `threads.net`, `?igshid=` and stub ids all land on the right post. Pasting a post link into Threads search opens it. Shared text from other apps tries enabled plugins before X. |
| Requests | Opened conversations are cached for 10 minutes (40 entries) and single-flight; post URLs are canonicalised to `www.threads.com` to skip a redirect. Guest pages decode malformed UTF-8 instead of throwing. |
| Profiles | Store-based (`ThreadsProfileStore`). Replies come from the public `/@handle/replies` page, asked only when that tab opens, with the profile's own reply posts as fallback. Media covers posts and replies. A Saved tab lists the reader's local hearts on that account. Share and open-in-browser in the app bar. Header and posts are awaited together, fixing an unhandled error when posts failed first. |
| Feed | Filters (All / Media / Links, hide replies, hide reposts) persisted locally with the shared dock filter button and an explicit "no posts match" state. |
| Restarts | The newest 120 posts and each account's answer time are kept in preferences. A restart paints them at once; accounts still inside the 10-minute cache window are not asked again. Unfollowed accounts are dropped on restore. |
| Shared cache | `AccountPostCache` refreshes no longer collapse the timeline to the accounts read so far: every account's held posts stay painted until it answers, and an account whose refresh fails keeps its previous posts. Adds `seed` / `answeredAt` for snapshots. |
| Home timeline | Threads posts mixed into Following / groups now keep their images in the feed snapshot. |
| Localization | 4 new labels across 29 locales; filters reuse existing `all`, `media`, `search_links`, `hide_replies`, `filters` and reader reset strings. |

## Evidence

No live Meta fixture could be captured from the build environment (egress to
`threads.com` is blocked there), so new shapes are pinned by unit tests built
from Meta's documented-by-observation post JSON and parsed defensively through
`Json`: a renamed or missing field yields an absent feature, never a throw.
Fragments are only trusted when they reproduce the caption; video falls back to
the poster tile when no rendition is present.

Tests: `threads_parse_test.dart`, `threads_conversation_test.dart`,
`threads_feed_snapshot_test.dart`, `threads_profile_store_test.dart`,
`threads_reader_widget_test.dart`, plus additions to `account_posts_test.dart`,
`plugin_url_test.dart` and `custom_reader_gestures_test.dart`.

## Next improvements

1. Verify `tag_header`, `text_fragments` and `quoted_attachment_post` against a
   captured live page and add it as a fixture.
2. Older profile pages: needs a stable guest cursor for
   `BarcelonaProfileThreadsTabQuery`.
3. Publish the guest GraphQL doc id through the endpoint registry so a rotation
   can be repaired without a release.
4. Native "Saved" library filter for Threads (currently stored as generic link
   snapshots).
