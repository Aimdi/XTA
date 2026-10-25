# Motion polish

After the two passes of X's motion (`x-motion-2026.md`, `x-motion-2026-part-2.md`)
the owner asked for animations to be improved further, now on XTA's own
judgement rather than from X's footage. This pass goes through the moments a
reader meets most often and smooths the ones that still jump, pop or feel
flat. It adds no posting surface and changes no data.

## What still jumps

| Moment | Today | This pass |
|---|---|---|
| An avatar arrives from the network | It pops in at once, while a photo beside it fades in | It fades in like a photo |
| A notice comes or goes (a retry, cached posts, posts from outside a group) | The pill appears and vanishes at once | It drops in and lifts away |
| New posts are waiting in For you or a group | A "New posts" button appears above the feed and pushes every post down | A "New posts" pill drops in over the feed; nothing below moves |
| An opened post has a finished poll | The bars are already full | They fill to their shares, one after another |
| Saving a post | The bookmark swaps outline for filled | It pops as it fills, with a light tap, as liking now taps too |
| The profile's scroll-to-top button | It pops in and out | It grows in and shrinks away |

## 1. Avatars fade in

- An avatar that has to be fetched fades in over 180 ms, as photos already do
  (`XtaFadeIn`). One already in memory, as when scrolling back, is drawn at
  once, so nothing flickers.

## 2. Notices drop in and lift away

- Every `FloatingNotice` pill slides down from 12 points above its place while
  it fades in (180 ms), and fades out while rising when it goes (120 ms).
- A notice whose content changes while it shows (a countdown, another source
  failing) updates in place; only coming and going move.
- Notices below another still wait for its height, so they never overlap.

## 3. "New posts" floats

- In For you and group feeds the "New posts" button becomes a pill that drops
  in over the top of the feed, above any source chips, instead of a row that
  pushes the posts down. Tapping it reveals the new posts, as today.

## 4. Poll results fill in on an opened post

- On the opened post, a finished poll's bars grow from nothing to their shares
  (600 ms, easeOutCubic), each starting 80 ms after the one above. The
  percentages are there from the start.
- In feeds the bars stay full: tiles are rebuilt while scrolling, so a fill
  would replay every time a poll came back into view.

## 5. Saving pops, liking and saving tap

- Saving a post pops the bookmark: it grows to 125 % and settles back with a
  slight overshoot (300 ms). Unsaving swaps it at once, as unliking does.
- Liking and saving give a light haptic tap; unliking and unsaving do not.
  Calm and zen mode keep both quiet, as they keep the heart's burst.

## 6. Scroll-to-top grows in

- The profile's scroll-to-top button grows from 60 % and fades in when it
  appears (180 ms) and shrinks away when it goes, instead of popping. While
  hidden it can be neither tapped nor focused.

## Reduced motion

XTA's reduce-animations setting and Android's "remove animations" make every
change in this pass happen at once. Haptic taps are not motion and follow
Android's own vibration settings.

## Not in this pass

- Animating posts into a feed as they arrive: paging lists rebuild whole
  pages, and moving them would fight the reader's scroll position.
- Themes: the owner chose to stay with X Look.

## Tests

- Avatars: a fetched avatar fades in; one already loaded does not.
- Notices: a pill drops in and lifts away, an update in place does not move
  it, and with reduced motion both are at once.
- New posts: the pill floats over the feed without moving the posts, sits
  above source chips, and reveals on a tap.
- Polls: an opened post's bars grow in order to their shares; a feed's are
  full at once, and so is an opened post's with reduced motion.
- Saving: the bookmark pops on save and not on unsave; liking and saving tap,
  unliking, unsaving and calm mode do not.
- Scroll-to-top: the button grows in and shrinks away, and cannot be tapped
  while hidden.
