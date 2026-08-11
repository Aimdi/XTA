# Substack in the rest of the app

Recon done on `claude/xta-repo-setup-shh2mn`. The ask: *"Can't Substack
seamlessly integrate with the rest of the app — can't I just create groups for
Substack? The Substack UI is fine, I just don't get the extra tab."*

Two separate wishes, with very different costs.

## 1. No extra tab — shipped

`XtaPlugin` gained `homeTabPrefKey`; when a plugin declares one, the plugin
store offers **Show as a tab**, and `HomeModel.loadPages` skips its tab when it
is off. Turned off, the Substack feed appears as a row in the **Groups** tab and
opens as a pushed route, which is where feeds live. The reader, archive and
add-publication screens are untouched.

Only one entry point exists at a time: the Groups row appears exactly when the
plugin is enabled and its tab is off.

## 2. Newsletters inside a normal group — shipped (interleaving)

Group feeds load Substack members via `SubscriptionSource` / interleaved
items. What was still missing was an **Add to group** control on Substack’s
own screens (archive, Notes, Discover, Library) — Follow alone left
publications out of groups unless you dug through People or the group edit
sheet. That control now mirrors Bluesky / Threads / Mastodon profiles.

## 3. Historical options (kept for context)

So a newsletter post in a group feed needed one of:

| Layer | Type |
|---|---|
| `TweetFeedController` | `CursorPagingController<String, TweetChain>` |
| `PaginatedTweetList` | `PagedListView<int, TweetChain>`, `itemBuilder` → `_buildChain` |
| Page fetch | `TweetPageResult = ({List<TweetChain> chains, String? nextCursor})` |
| Group chunks | `SubscriptionGroupFeedChunk` → X search `from:<screenName>` per member |
| Chunk cache | `feed_group_chunk` rows storing X API JSON |
| Dedup / caught-up | keyed on `TweetChain.id`, `feed_read_position` |

So a newsletter post in a group feed needs one of:

**(a) Fake a tweet.** Convert a `SubstackPost` into a synthetic `TweetWithCard`.
Cheap to write, bad to live with: the footer's reply/repost/like counts, the
save/like actions, translation and the conversation route all key off real X
ids. Every one of those becomes a lie or a crash. **Rejected.**

**(b) Make the feed item a union.** Introduce `sealed class FeedItem` with
`TweetItem` / `NewsletterItem`, thread the generic through the paging
controller, the list view, the page result, the cache read/write and the
caught-up boundary, then give each item its own builder. This is the honest
version and it is invasive: it touches every feed in the app (home, group,
profile, search, saved), the chunk cache, and the read-position logic. It also
needs a merge policy — newsletters have no cursor in the X pagination, so they
have to be interleaved by timestamp per page, with their own paging.

**(c) Keep them side by side.** A group that contains publications shows a
newsletter strip above its posts. Much cheaper than (b), but it is not really
one feed, and it would need a design decision about placement.

### What (b) would additionally need
- Publications as group members: either a third member table
  (`substack_subscription_group_member`) or a synthetic subscription id
  (`substack:<subdomain>`) in `subscription`, which then must be excluded from
  the `from:` query the group feed builds.
- The group edit sheet gaining a publications section.
- A per-page merge that does not starve either source, plus a cache story for
  newsletter pages (the current cache stores X API JSON only).

## Recommendation

Ship (1) — done here — and decide between (b) and (c) before any more code. (b)
is the "seamless" the request asks for and is a genuine refactor of the feed
stack; (c) is a fraction of the work and gets newsletters visible inside a group
without pretending they are posts.
