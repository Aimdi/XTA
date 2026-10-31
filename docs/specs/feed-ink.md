# The feed in "Ink": canvas concept A

The owner's canvas "QuaX Feed Restyle" set nine directions for the feed side
by side. They chose A, "Ink (flat, X-nah)": flat, close to X, on black. The
canvas draws one phone: the Following bar, a Reddit post and an X post in one
timeline, and the bottom bar.

## Found

- **A post** is a header row, then everything else across the full width:
  the avatar in a 48 dp target, the name on one line and "@handle · time" in
  13 px on a second, translate and ⋯ at the end; then the text, media, quote,
  card and footer from 16 dp.
- **A self-thread in a timeline** has its own geometry: a 48 dp avatar on the
  rail and its text at 80 dp.
- **A Reddit post in a mixed timeline** has a 48 dp round subreddit picture,
  "r/sub · time", then "u/author" with an outlined "Reddit" label, the title
  in bold, the picture edge to edge and the footer under it.
- **Every post from another network in a mixed timeline** has a 2 dp strip
  in that network's colour down its leading edge, and an outlined label with
  the network's name.
- **The Following bar:** Posts | Media with a 3 dp marker, an order chip (the
  sort icon, a red count of active filters and a chevron), Feeds, search in
  the loaded posts, and Home feed accounts with a red count of what is turned
  off.
- **The bottom bar** is a frosted pill floating 16 dp in from the edges, with
  a highlight that slides to the selected item.

## After

### A post in a timeline

As X lays one out: the avatar in a column of its own, everything else in a
second column beside it.

- **The avatar**, 40 dp, at 16 dp from the edge and 12 dp from the top, in its
  48 dp target as before.
- **The column starts at 68 dp** and holds the author line, the text, the
  "Replying to" line, media, quote, card, poll and the footer. Media keeps
  its 16 dp corners and hairline border, now the column's width.
- **The author line is one line:** the name in bold, its badges, then
  "@handle · time" in the same 15 px in the secondary colour. The handle
  gives way first, then the name; the time stays. With text at 130 % or more
  the handle and time move to a second line, as they do now.
- **Translate and ⋯** stay at the end of the author line, their glyphs on its
  line, their 48 dp targets reaching down over the text as X's ⋯ does.
- **The text starts right under the author line** (2 dp), as the canvas draws
  it.
- **The footer** keeps its actions, counts and fit; its first glyph lines up
  with the text. The column is 60 dp narrower than the full width, so on a
  360 dp phone the footer drops the view count, the first thing its fit lets
  go, where it used to show it; every action keeps its 48 dp target.
- Repost, pinned, thread and community lines already start at 68 dp; they
  stay.
- **A self-thread** takes the same geometry: 40 dp avatars on the rail, which
  runs through their centres, and the text at 68 dp. A conversation's replies
  share the rail, so they take it too.
- **Loading placeholders** already have this geometry and stay as they are.

Unchanged: the post a conversation was opened on (X's own large layout),
quoted posts inside a post, community notes, and the Media tab's grid.

### A Reddit post

The same two columns as an X post, as the canvas draws it.

- **The subreddit's picture**, 40 dp, as a rounded square (10 dp corners): a
  community, where people are round.
- **One author line:** "r/sub" in bold, then "u/author · time" in the
  secondary colour, the time as short as an X post's ("15 Min.").
- **The title is the post's text**, 15 px regular as an X post's text, up to
  six lines; the body, flair, NSFW and spoiler follow under it as now.
- **The picture or video in the column**, with 16 dp corners and the same
  hairline border as an X post's media.
- **The footer:** upvote and its count, comments and their count, save and
  open in browser, spread across the column. With large text on a narrow
  phone they take a second row rather than shrink their targets.
- Tap, long press, the picture's own tap and the vote stay as they are.

### Where a post came from

In a mixed timeline, a post from another network says so **with a badge on
its avatar**, not with a strip and a label:

- an 18 dp circle in the network's colour on the avatar's bottom corner (at
  the end of the line: bottom right, bottom left in a right-to-left
  language), with the network's mark in a contrasting colour and a 2 dp ring
  in the card's colour around it;
- a screen reader hears the network's name, as it heard the label.

This covers Reddit, Mastodon, Bluesky and Threads, the posts with an
author's avatar. Substack, RSS and booru posts have none and keep their
strip; so do web sources, whose card already names its site and shows the
site's own mark.

The time on an author line takes what the name leaves, and never less than
45 % of the line, where an absolute date shortens.

### The Following bar

Still one 48 dp bar; everything it does stays.

- **Tweets | Medien** at 16 px, bold when selected, medium in the secondary
  colour when not, 28 dp apart; the marker 4 dp tall in the accent colour
  under the selected label. On a narrow screen they move closer together, and
  with large text they move to a second row, as now.
- **Order and filters is an icon** (the filter lines) instead of a chip. When
  filters are on, their count sits on its top corner in the accent colour.
  The menu, the tooltip and what a screen reader says are unchanged.
- **Feeds** stays, with its unread dot. The canvas leaves it out; it holds
  Home's other timelines and the side menu.
- **Search in the loaded posts** keeps its magnifier with lines: the canvas
  draws a plain magnifier, which is also Discover's in the bottom bar.
- **Home feed accounts is a person** with a small red dot when accounts or
  groups are turned off, instead of the red count. The number moves to what a
  screen reader says.
- Every control keeps its 48 dp target, where the canvas draws 44.

### The bottom bar

- **Flat across the bottom**, 64 dp above the system's gesture area, in the
  page's colour at 88 % with a hairline along its top. No frosted pill, no
  sliding highlight.
- **Icons only**, 26 dp, all in the text colour; the selected one is filled.
  "Show navigation labels" still adds the names under them.
- Long press on Home, reduce motion and the four destinations stay.

### Themes

Ink is drawn on black. Light and dim draw the same layout in their own
colours: the theme's divider for hairlines and the bar's top edge, the card
colour for the badge ring, the page colour under the bottom bar.

## Not in this pass

- Mastodon, Bluesky and Threads posts keep their own layout (avatar and
  name above, text across the width); they gain only the avatar badge.
- The canvas's purple accent: the accent stays the one chosen in settings.
  The verified check already wears it, as the canvas draws it; gold and grey
  still mark organisations and governments.
- The canvas's Figtree typeface: XTA keeps Inter.
- The other eight directions.

## Code

- `lib/tweet/tweet_ink_layout.dart` (new): the two-column post.
- `lib/tweet/tweet_header.dart`: the one-line author line.
- `lib/tweet/tweet.dart`: timeline posts and self-threads in the two columns.
- `lib/tweet/thread_rail.dart`: the rail's geometry.
- `lib/ui/provenance_accent.dart`: the avatar badge; the strip only for posts
  without an avatar.
- `lib/plugins/reddit/reddit_post_card.dart`: the Reddit post;
  `reddit_media_frame.dart` draws the media hairline `lib/tweet/tweet_chrome.dart`
  now shares.
- The Mastodon, Bluesky and Threads cards and their mixed timelines: the
  badge instead of the label and strip.
- `lib/home/home_timeline_controls.dart`, `lib/home/_feed.dart`: the bar.
- `lib/home/home_chrome.dart`: the bottom bar.

## Tests

- A timeline post: the avatar at 16 dp, 40 dp, in a 48 dp target; the author
  line, text and footer start at 68 dp; name, handle and time share one line,
  and with text at 130 % the handle and time take a second; translate and ⋯
  stay tappable at 48 dp; the opened post and a quoted post keep their
  layout.
- A self-thread: avatars and text in the same columns, the rail through the
  avatars' centres.
- A Reddit post: the rounded-square picture, one author line, the title in
  the body style, the media inside the column, the footer's four actions.
- A mixed timeline: a Reddit post carries the badge with the network's name
  for a screen reader, and no strip, bottom left in a right-to-left language;
  a Substack post keeps its strip; Mastodon, Bluesky and Threads posts carry
  their badge too.
- The Following bar: the filter icon with its count, Feeds, search and the
  person with its dot, in that order, each 48 dp; the menu opens as before;
  German at 390, 360 and 320 dp ×1.3 fits as before.
- The bottom bar: flat, full width, 64 dp, hairline on top, icons only by
  default and labelled with the setting on; selection and long press work.
- Renders in black, dim, light and large text right-to-left, beside the
  canvas.
