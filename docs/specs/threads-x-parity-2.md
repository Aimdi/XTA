# Threads, read as well as X — second pass

The owner asked again for Threads to be "A LOT" better, as good as the X
reader. The first pass (`threads-x-parity.md`) added post and tag search,
whole-post saving, honest profiles, the refresh rotation, retrying by what
Meta said and reply order. This pass takes the gaps a reader meets on almost
every post, from an audit of the X reader against the Threads plugin
(2026-11-01).

Read-only as every Threads pass: nothing is written to Meta. Likes, saves and
follows stay on the device.

## Posts

### Pictures and videos

- **A picture opens in X's viewer**: pull down to close, double-tap to zoom,
  as an X photo does. Threads drew its pictures through its own image widget,
  which left the viewer with pinch-zoom only.
- **Pictures are decoded at the size they are shown**, as X's are, where every
  tile decoded Meta's largest rendition at full size.
- **Several pictures or videos lie along one row at one height**, the next one
  showing an edge, as X lays them out and the Threads app does; the row
  scrolls sideways. A post showed one at a time with dots under it, and the
  card's height jumped from one to the next.

### Times and the opened post

- A post's time follows the "absolute timestamps" setting, as on X; a card in
  a list keeps the short form.
- **The opened post** says its time and date on a line under it, as X's opened
  post does, instead of a short time in its header. Its text can be selected
  and copied.
- **A conversation opens at the post that was tapped**: when posts above it
  lead to it, the list starts on it with them above, as X's does, instead of
  at the first of them.

### Opening and actions

- A post, a quote or a reply opens as **the card that is dragged back**, as an
  X post does (`post-swipe-back-card.md`).
- **A ⋯ button** in the header opens the post's actions (save to a folder,
  share, copy the link, group the author), where only a long press did, out
  of a mouse's reach.
- The reply and repost buttons are **named for screen readers**, the like
  button says it likes on this device, and the topic tag and "Show N more
  posts" are 48 dp tall.

## Feeds

- **In Following and groups, each followed Threads account brings up to 10
  posts**, as the code always said; the ten newest of all of them were taken,
  so one busy account crowded out the rest.
- **A mixed feed opened offline shows Threads posts as Threads cards** — text,
  pictures, counts, opening in the app — where they came back as bare link
  cards that opened the browser.
- **Followed accounts keep their current name and picture**, updated from
  their posts as X subscriptions are; a renamed account kept its old name and
  a picture link Meta had expired.
- **F5 and Ctrl+R refresh the Threads tab** on the desktop, as the X feeds do.

## Saved

- **A saved Threads post can be kept offline**, pictures and videos, as X,
  Mastodon and Reddit posts can. Meta's picture links expire, so a saved post
  lost its pictures in time.

## Not in this pass

- **The tab's reading position.** Bluesky and Mastodon each keep their own
  copy of the code that remembers it; Threads should share one with them,
  which wants that code made shared first (the owner decides).
- **Video quality** (picker and setting): the post keeps only its largest
  rendition; every rendition would need storing.
- **Muted words for other networks' posts in groups**: a change to every
  network's posts in groups, not to Threads alone.
- Polls, sensitive media, pinned posts: Meta's pages have not been seen to
  carry them; a captured page would show whether they do.
- Profile media grid and a Posts/Reposts filter, following a tag, a stored copy
  of a conversation to reopen offline.

## Code

- `lib/plugins/plugin_post_media.dart`: a row layout for several items
  (`strip`), laid out by X's `mediaStripLayout`.
- `lib/plugins/threads/threads_media.dart`: no image builder of its own; the
  row layout.
- `lib/plugins/threads/threads_post_card.dart`: times, the opened post
  (`focal`), the ⋯ button, names and sizes, the card route.
- `lib/plugins/threads/threads_thread_screen.dart`, `threads_quote.dart`: the
  opened post, opening at the tapped post, the card route.
- `lib/plugins/threads/threads_interleaved.dart`: ten per account; the archive
  as the snapshot.
- `lib/plugins/feed_snapshot_posts.dart`, `lib/tweet/feed_snapshot_cache.dart`:
  a snapshot reads back as the post it is, a Threads card or a link post.
- `lib/tweet/focal_post.dart`: the date line can start with a text column.
- `lib/plugins/threads/threads_store.dart`: accounts kept current.
- `lib/offline/offline_saved_media.dart`: Threads media.
- `lib/plugins/threads/threads_screen.dart`: refresh keys.

## As built

- Reply and repost both open the post (Meta lists neither apart), so both are
  named "Open post", the name X gives its reply button.
- The ⋯ sits at the end of the author line, Follow before it, as on X.
- The topic tag's row is 48 dp tall, which adds a little space under it.
- The opened post's date line starts with its text, not at the page margin.
- A post opened from a pasted link (the search sheet, a link from another
  app) still opens as a page.

## Tests

- Media: a post's pictures open X's viewer; several items lie in a row whose
  cards share a height; tiles decode at their width.
- Times: the absolute setting is followed; the opened post has the date line
  and no header time, and its text is selectable.
- Conversation: with posts above, the tapped post is in view at the top.
- Actions: the ⋯ button opens the actions; reply and repost have names; the
  tag and "Show N more posts" are at least 48 dp.
- Feeds: two accounts with twelve posts each give ten each; a snapshot of a
  Threads post reads back as a Threads card; a post with a new name or
  picture updates the followed account.
- Saved: a saved Threads post's pictures and videos are offered offline.
