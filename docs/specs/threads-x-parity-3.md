# Threads, read as well as X — third pass

The owner asked for Threads to be "A LOT" better, as good as the X reader.
The second pass (`threads-x-parity-2.md`) brought X's photo viewer, the row
of pictures, the opened post, the ⋯ button, ten posts per account in mixed
feeds, offline cards and saved media. This pass takes the smaller gaps from
the same audit that need no code copied from another network.

Read-only as every Threads pass: nothing is written to Meta.

## Search

- **The query can be changed on the results page**, as on X's search: the
  title is a search field holding it. Enter searches again; a post link
  opens the post, a #tag its posts, an @handle the profile, as the search
  sheet does, by the same rules.

## Posts

- **A quoted post shows its link card**, as the post itself does; a quote of
  an article showed only its text.
- **#tags in a caption Meta sent as plain text open the tag's posts**, as
  @handles and links there already open; only captions with Meta's own
  fragments had tappable tags.
- **Double-tap to like**, when the reader turned it on for X posts
  (Settings → Gestures), likes a Threads post on this device too.

## Video

- **Every rendition Meta sends is kept**, largest first, so the video
  quality setting picks among them as it does for X videos, and the
  player's quality menu offers them by height ("720p"). Posts saved before
  have only their largest, and play it.

## Not in this pass (for the owner)

These would copy code another network already has; the owner decides
whether to make that code shared first:

- the profile's **media as a grid** (Bluesky and Mastodon each have their
  own grid);
- the tab's **reading position** (Bluesky and Mastodon each keep their own);
- **muted words** for other networks' posts in groups (a change for every
  network).

## Code

- `lib/plugins/threads/threads_search_screen.dart`, `threads_search_sheet.dart`,
  `threads_search_query.dart` (new): what a query names, shared by both.
- `lib/plugins/threads/threads_quote.dart`, `threads_post_card.dart`: the
  link card, shared.
- `lib/plugins/threads/threads_rich_text.dart`: #tags in plain captions.
- `lib/plugins/threads/threads_post_card.dart`: double-tap to like.
- `lib/plugins/threads/threads_parse.dart`, `threads_models.dart`,
  `threads_media.dart`: renditions kept and offered.

## Tests

- What a query names: a post link, a #tag, an @handle or profile link,
  words; the results page searches again with an edited query.
- A quote with a link card shows it; a plain caption's #tag opens the tag.
- Double-tap likes a post when the setting is on and does nothing when off.
- Renditions are parsed largest first and deduplicated, kept through a
  saved snapshot, and offered as qualities; an old snapshot plays its one
  video.
