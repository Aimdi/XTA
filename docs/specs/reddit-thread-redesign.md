# Reddit: the thread view, redesigned

The owner's design "Reddit thread view redesign" (boards "Thread view,
redesigned", "Comment states" and "Link preview options") turns a Reddit post
and its comments from tinted bubbles under a crowded app bar into one quiet
column: the post, a row of actions, the comment count, and comments that hang
off thin coloured thread lines.

Read-only as before. Nothing is sent to Reddit: the upvote stays on this
device, and there is no reply, downvote or vote on Reddit itself.

## Before

- The app bar holds the subreddit, a fold-all toggle, save and a sort menu.
- The post is its title, a row of subreddit picture, author, age, a small
  upvote button and the comment count, then its media and text.
- Every comment is a tinted bubble, indented by depth; tapping it folds it.
  Its header holds the avatar, name, score and age, and a folded comment
  shows `+N`. Replies Reddit held back are an outlined "More replies · N"
  pill.

## After

### Header

- Back, **r/subreddit** (17 sp, semibold) and **⋮**, which opens the post's
  sheet (author, subreddit, follow, save, note, group, open in browser, share
  link). A hairline under it.

### The post

- **Author row**: the author's avatar (22 dp, round), `u/name` and the age.
  Tapping it opens the author's posts.
- **Title**, 21 sp on 27, semibold.
- **Media** as before for pictures, galleries and videos. A **link** is a card
  with a rounded border: Reddit's preview as a picture 168 dp tall across the
  card, over a 44 dp row with the domain and the open-in-browser arrow. With
  no preview, or only Reddit's 70 px thumbnail, it is the fallback row: a tile
  with the domain's first letter, the domain over "Open in browser", and the
  arrow. Over-18 previews keep their cover.
- The post's own text follows, 15 sp on 22.

### Actions under the post

- A **vote pill**: the up arrow and the score. Tapping it upvotes on this
  device (as before, with the post filed among likes); upvoted, arrow and
  score turn Reddit orange.
- A **comments pill**: the bubble and the count. Tapping it scrolls to the
  comments.
- **Share** (the post's link) and **save** (as before: filed on this device,
  a long press picks the folder), at the end.

### Comments header

- "**N comments**" (15 sp, semibold), then the **sort** button showing the
  current order (Best, Top, New, Controversial, Old, Q&A in a menu), then
  **Collapse all** / **Expand all** when any top-level comment has replies.

### Comment rows

- **Thread lines**: a reply sits one 20 dp column further in per level, each
  column holding a 2 dp line in its level's colour: blue, violet, amber,
  green, repeating, darkened where a light theme needs it for 3:1. Six levels
  at most are drawn, so a deep reply keeps room for its words.
- A hairline separates top-level threads.
- **Header**, one tappable row that folds and unfolds the comment: the
  author's avatar (20 dp), `u/name` (13.5 sp, semibold), flair when Reddit
  sent one, the age, and, folded, a "+N replies" chip counting the comments
  under it. A screen reader hears whether it is expanded. **⋮** at its end:
  the author's posts, Copy, Share link, Open in browser.
- The **body**, 15 sp on 22, with its pictures.
- An **action row** at the end: the up arrow and the score, upvoting on this
  device the same way the post does.
- **The post's author** reads in the accent with an **OP** chip and a round
  avatar.
- **New since your last visit**: a comment written after the reader last
  opened this thread shows a dot and "21m, new" in the accent. The last visit
  of each thread is kept on this device, the 200 most recent.
- **Removed**: a neutral avatar tile, the name in the secondary colour, and in
  italics what happened — deleted, removed by moderators, removed by Reddit or
  unavailable. Replies under it stay.
- **Held back**: at the top of the thread, "Load more comments" loads the next
  ones in place; anywhere else "Continue thread ›" opens the rest on their own
  page, or on Reddit when it will not serve them. Both are accent text at the
  reply's depth, with its thread lines.

### Jump to the next comment

- A round button (52 dp) at the bottom end scrolls to the next top-level
  comment below the top of the screen, or to the end after the last. It shows
  once there are two top-level comments; with reduced motion it jumps.

### Sizes

Every tap target is 48 dp where the design drew 40 to 44, Android's minimum
and this app's rule; the comment header and action rows grow to match.

## Not in this pass

- **Reply** and **downvote**: replying writes to Reddit, which this reader
  does not do, and a downvote kept on the device would need a new column in
  the local vote table, a schema change outside a UI pass.
- **Swipes** (right to upvote, left to reply): reply is not built, and a
  sideways drag on a comment would fight the swipe back to the feed.
- **The compact link card** (thumbnail beside the title) and **plain grey
  thread lines**: options on the boards, not the default.
- **Flair from the anonymous page**: shown where Reddit's answer names it
  (signed in, and old.reddit); the www.reddit.com samples carry none to read.

## Code

- `lib/plugins/reddit/reddit_thread_screen.dart`: the screen — app bar, post,
  actions, comments header, rows and the jump button.
- `lib/plugins/reddit/reddit_thread_comment.dart`: a comment row, its thread
  lines, header, action row and menu, and the held-back row.
- `lib/plugins/reddit/reddit_thread_post.dart`: the post, its link card and
  its action row.
- `lib/plugins/reddit/reddit_thread_visits.dart`: when each thread was last
  opened.
- `lib/plugins/reddit/reddit_comments.dart`, `reddit_comments_json.dart`: a
  comment's author flair.
- `lib/plugins/reddit/reddit_avatar.dart`: a round avatar.
- `lib/plugins/reddit/reddit_post_media.dart`: the link card can be
  replaced, so the thread draws its own.
- `lib/plugins/plugin_comment_bubble.dart` is no longer used by Reddit; Hacker
  News and Pixiv keep it.

## Tests

- Header, author row, title, link card (large and fallback), vote pill,
  comments pill scrolling to the comments, share and save.
- Thread lines: a reply one column in with its level's colour, a hairline
  between top-level threads, no bubbles.
- Folding from the header, "+N replies", Collapse all / Expand all, sort menu.
- OP chip, flair, removed wording, new since the last visit, ⋮ menu.
- "Load more comments" in place, "Continue thread" to the subtree's page.
- The jump button lands on the next top-level comment.
- Every target 48 dp; 320 dp at 2× text, left to right and right to left, in
  all three themes, without overflow.
- Renders in Lights Out (German and English) and light, beside the boards.
