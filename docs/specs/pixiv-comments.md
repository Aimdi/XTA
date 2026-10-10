# Pixiv — comments (reading only)

What batch B3 of the PixEz parity plan (`pixiv-pixez-gaps.md`) built: reading
the comments on artworks and novels, their reply threads, Pixiv's emoji and
stickers, local comment and author mutes, a guard against link spam, and
selectable comment text. XTA stays a reader, so there is no composer, no
reply box and no report: nothing on these screens writes to Pixiv.

## Endpoints

All of them live in `PixivCommentsApi` (`pixiv_comments_api.dart`), read
through `PixivCommentsApi.of(context)` so tests pass a fake with
`pumpPixiv(extraProviders: …)`. Every call is a `GET` over the shared
transport; later pages follow `next_url` as Pixiv wrote it.

| Call | Path | Notes |
|---|---|---|
| `comments(illust)` | `GET /v3/illust/comments?illust_id=` | `comments`, `total_comments`, `next_url` |
| `comments(novel)` | `GET /v3/novel/comments?novel_id=` | Same shape |
| `replies(illust, id)` | `GET /v2/illust/comment/replies?comment_id=` | `comments`, `next_url` |
| `replies(novel, id)` | `GET /v2/novel/comment/replies?comment_id=` | Same shape |

`PixivCommentTarget` (`pixiv_comment_models.dart`) names the work:
`PixivCommentTarget.illust(id)` or `PixivCommentTarget.novel(id)`. Its
`PixivCommentWork` holds the list path, id parameter and replies path, so the
screen, store and API never branch on the kind of work.

## Model

`PixivComment` carries the id, text, date, author (a `PixivUser`, or null for
a missing or withdrawn account), whom a reply answers (`parent_comment.user`),
`has_replies` and the sticker URL (`stamp.stamp_url`). Parsing goes through
`Json`: a comment without an id is dropped, and anything else that is missing
or reshaped becomes an empty field rather than a throw.
`pixivCommentAuthorName` gives the name, else `@account`, else null, which the
screen shows as *Unknown user*.

## Emoji and stickers

`pixiv_comment_emoji.dart` holds Pixiv's 38 emoji names in the five sets Pixiv
numbers them in (the n-th of set s is image s × 100 + n) and a pure tokenizer,
`pixivCommentParts`, that splits text into text and emoji runs. Only a known
`(name)` becomes an emoji; unknown codes, uppercase codes and parentheses
around ordinary words stay text. `PixivCommentText` draws the emoji inline at
20 px from Pixiv's own `s.pximg.net/common/images/emoji/<id>.png`, so XTA
ships no emoji images; the text scale grows them with the words. A sticker
comment shows its `stamp_url` at 100 px. A failed emoji or sticker shows the
same quiet mark a grid tile does, never the image widget's tiny tap target.

## Screen

`PixivCommentsScreen` (`pixiv_comments_screen.dart`, opened with
`openPixivComments(context, target)`) reads its comments into
`PixivCommentsStore` (`pixiv_comments_store.dart`), a `PixivPagedListStore`
that pages through `next_url`, drops duplicates and skips pages the mutes
empty. Each comment is a `PixivCommentTile` (`pixiv_comment_tile.dart`) in the
shared `CommentBubble`, the same colours and layout as Reddit and Hacker News
comments:

- a 36 px avatar inside a 48 dp target that opens the author's profile,
  announced as a button with the author's name that a screen reader can
  activate too;
- *Name · To someone · when*, the name set apart;
- the text with emoji, inside a `SelectionArea`, so Copy, Share and
  Android's process-text actions (installed translators) appear when text is
  selected; no server translation is called. Each emoji carries its code as
  clear text over the image, so a selection copies `(heart)` rather than
  dropping it;
- the sticker;
- *View replies* when `has_replies` is set, a text button so a long
  translation wraps at large text;
- a 48 dp menu offering *Mute this comment* and *Mute {author}*.

Pull to refresh reloads; the list asks for the next page near its end, and a
short first page asks by itself. The screen opens on the loading skeleton, so
an empty list is never shown before the first page has answered. No comments
shows an empty pane; a failed first page shows the full-page error with Retry.
A failed later page keeps what loaded and puts what went wrong and Retry
where the spinner was; the store records that failure (`moreError`), since
the shared paged store only keeps the list. Scrolling does not retry it by
itself, so a dropped connection is not asked again on every scroll.

**Thread mode.** *View replies* pushes the same screen with the comment as
`parent`: the comment sits on top and its replies follow one level in, paged
the same way. A thread with nothing in it says *No replies yet*.

**Novels.** `PixivCommentTarget.novel(id)` gives the same screen over the
novel endpoints. The novel reader adds its button with the novel batches,
using `PixivCommentsLink` or `openPixivComments`.

## Mutes and spam

Muting goes through `confirmPixivMute`, which asks first. *Mute this comment*
adds the id to `PixivMuteState.commentIds` (pref
`plugin.pixiv.muted_comments`); *Mute {author}* adds the author to the
existing `authorIds`, which also hides their works. `pixivVisibleComments`
applies both as each page lands and again whenever the mutes change, so a
muted comment disappears at once, as does everything else its author wrote.
Muting the comment a thread hangs off, or its author, leaves the thread.
Muted comments are listed, and can be unmuted, on the mute settings page.

Comments that link anywhere other than Pixiv's own sites (pixiv.net,
pixiv.me, pximg.net, pixivision.net, fanbox.cc, booth.pm) are collapsed
behind *Hidden: links outside Pixiv* with a *Show* button
(`pixivTextLinksOutside`). A link counts when it has a scheme, is a bare
`www.` followed by a dotted host (so the Japanese laugh "www" is not taken
for one), or is a bare `host.tld/` such as `bit.ly/abc`. The host is read the
way a browser reads it: what follows the last `@` (`pixiv.net@evil.example`
is evil.example), and a plain, full-width or encoded dot after the ASCII part
carries it on to another domain (`pixiv.net。evil.example`). A Japanese
sentence glued to a Pixiv address without such a dot stays a Pixiv link.
That replaces matching a list of known spam domains, which would need upkeep.
What was shown stays shown while the screen is open, and in a thread opened
from it.

Report is not offered: XTA does not write reports to Pixiv.

## Entry points

- **Artwork detail.** `PixivCommentsLink` (`pixiv_detail_comments.dart`) under
  the tags: *View comments (N)* from the work's `total_comments`, or *View
  comments* when the work arrived without a count.
- **Novels.** Wired by the novel reader batch, with the same link.
