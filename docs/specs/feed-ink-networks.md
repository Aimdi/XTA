# Ink for Mastodon, Bluesky and Threads posts

aimdi188 drew X and Reddit posts in the "Ink" layout (docs/specs/feed-ink.md)
and gave Mastodon, Bluesky and Threads posts only the network badge on their
avatar. Read together in a mixed timeline, those three still looked like a
different app. The owner asked to improve the app across the board, the Ink
look included; this finishes it.

## Found

The three cards share one shape, different from an X post's:

- **A 48 dp avatar** (X and Reddit: 40 dp), with the text at 76 dp.
- **Two author lines**: the name and time, then the handle, with Bluesky's and
  Threads' "Follow" as a text button on one of them.
- **Their own repost line**, starting at 60 dp (Bluesky, Threads) or 20 dp
  (Mastodon's "boosted"), and Mastodon's "Pinned" as an outlined label.
- **"Replying to" above the author** on Bluesky and Threads, under it on
  Mastodon.
- **Times written out** ("vor 15 Minuten") on Mastodon and Bluesky, where X,
  Reddit and Threads write "15 Min.".
- **The actions row** inset 16 dp at the end, so its last glyph sits 16 dp left
  of an X post's share glyph.

## After

Each takes an X post's two columns (TweetInkLayout), through one shared
scaffold so the three stay alike:

- **The avatar**, 40 dp, at 16 dp in its 48 dp target, with the network badge
  in a mixed timeline as before. A screen reader hears the author and, with
  the badge, the network.
- **One author line** (TweetAuthorLine): the name, a verified check where the
  network has one (Threads), then "@handle · time" with the short time
  ("15 Min."). Mastodon keeps its instance dimmed in the handle, and adds
  "Edited" after the time when a post was edited, the first thing cut where
  the line is short. The handle gives way first; with large text the handle
  and time take a second line. A tap on the line opens the author, as before;
  a tap on the time shows the date, as on an X post.
- **Follow** (Bluesky, Threads) becomes a 48 dp icon at the end of the author
  line, where an X post has ⋯, with "Follow" as its tooltip and for a screen
  reader. It still goes once the account is followed.
- **Repost, boost and pinned** are the same context lines an X post has, at
  68 dp with their icon; a tap on a repost or boost line still opens the
  reposter.
- **"Replying to"** sits under the author line, as on X.
- **The text, media, quotes, link cards, content warnings, Threads' topic tag
  and "Show thread"** start at 68 dp, as before in order. The text takes an
  X post's body style, 15 px where these cards had 16, so a mixed timeline
  reads at one size.
- **The actions** keep their buttons and counts; the row ends at the edge, so
  its last glyph lines up with an X post's share glyph.
- Tap, long press and every button do what they did.

A Threads conversation's rail then runs through the 40 dp avatars, as X's
does, and its separate rail geometry goes. Each card draws its part of the
rail itself: drawn behind the cards, as it was, the cards' own colour covered
it. Connected posts go without the hairline between them, as an X thread's
do, so the rail runs on unbroken.

Their own screens (a Mastodon instance, a Bluesky feed, a Threads profile)
show the same cards, so they change too.

## Not in this pass

Pixiv, booru, Substack, RSS, Hacker News and web-source cards keep their own
layouts: they are galleries, articles and links rather than posts with an
author.

## Code

- `lib/plugins/plugin_ink_post.dart` (new): the shared scaffold and the
  Follow icon.
- `lib/tweet/tweet_header.dart`: a handle span for Mastodon's dimmed
  instance.
- `lib/ui/dates.dart`: a suffix after the time, for "Edited".
- `lib/plugins/mastodon/mastodon_post_card.dart`,
  `lib/plugins/bluesky/bluesky_post_card.dart`,
  `lib/plugins/threads/threads_post_card.dart`: the cards.
- `lib/plugins/plugin_profile_tabs.dart`: "Replying to" under the author line.
- `lib/plugins/threads/threads_thread_screen.dart`, `lib/tweet/thread_rail.dart`,
  `lib/tweet/threaded_conversation.dart`: one rail geometry, drawn in the card.

## Tests

`test/feed_ink_networks_test.dart`:

- Each card: the avatar at 16 dp, 40 dp, in a 48 dp target; the author line,
  text and actions at 68 dp, the actions ending at the edge; name, handle and
  time on one line, and on two with large text on a 320 dp phone; the author
  and, in a mixed timeline, the network for a screen reader.
- Follow: a 48 dp icon at the end of the author line with its tooltip until
  the account is followed.
- Repost, boost and pinned lines at 68 dp; a repost or boost line opens the
  reposter. "Replying to" and Threads' topic under the author line.
- Mastodon dims the server and says "Edited" after the time; Threads carries
  its verified check on the author line.
- A Threads conversation draws its rail inside the card, through the avatar,
  without a hairline between connected posts.
- German at 320 dp does not overflow (the existing card overflow tests).
- Renders, with `--dart-define=RENDER_INK=true`: a mixed timeline in black,
  light and large text right-to-left, and a Threads conversation.
