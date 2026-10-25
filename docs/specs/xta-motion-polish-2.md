# Motion polish, part 2

After the first polish pass (`xta-motion-polish.md`) the owner asked for more.
A read-only sweep of the reading screens listed the places where the screen
still changes in a single frame; this pass takes the ones a reader meets most
and smooths them the same way. It adds no posting surface and changes no data.

## What still jumps

| Moment | Today | This pass |
|---|---|---|
| Images in cards, link previews, video posters, the media grid, poll options, the profile banner, group faces | A spinner, then the picture at once | They wait on a quiet surface and fade in, as feed photos do |
| Opening a post | The placeholder rows become the replies in one frame, and the reply order bar drops in above them | The replies and the bar fade in where the placeholders were; the opened post stays still |
| A feed or a profile's posts loading cold, or a profile opening | The placeholders give way to the posts, or the skeleton to the profile, in one frame | The posts or the profile fade in where the placeholders were |
| Translating a post | The text is swapped in one frame and everything below it jumps | The translation fades in while the post eases to its new height, and so does the original on the way back |
| A profile refreshing from X | Following and followers change as plain text; "Followed by" pops in and pushes the tabs down | The counts roll; "Followed by" grows in |
| Things that open inside a feed | Price chips, link stacks, folded posts, repost runs and a revealed sensitive post change height at once | They ease to their new height |
| Muting a video | The mute icon swaps | The icon cross-fades |
| Tapping a tab in the bottom bar | The page is replaced in one frame | The new page fades in quickly |
| The line over a loading feed | It appears and vanishes at once, and batch progress steps | It fades in and out, progress glides, and it fills before it goes |
| Swiping in the media viewer | "1 / 4" changes at once | The number rolls, as counts do |
| Changing a follow on the Subscriptions list | The list flashes to a spinner and comes back at the top | It stays where it was |

## 1. Images fade in everywhere

- Every network image in a post or a card waits on a quiet surface instead of
  a spinner and fades in over it once fetched (180 ms, `XtaFadeIn`), as feed
  photos and avatars already do. One already in memory is drawn at once.
- This covers card images, link previews, video and GIF posters, the media
  grid, poll option images, the profile banner and the faces on group boards,
  where the photo now fades in over the monogram it replaces.
- A failure looks as it does today.

## 2. Opening a post

- When the conversation replaces the preview a post was opened from, the
  replies, the posts above it and the reply order bar fade in where the
  placeholder rows were. The opened post itself does not move or fade: it was
  already on screen.
- Opened without a preview, the whole conversation fades in over its
  placeholders.
- Only the conversation's first tiles fade; one built later, as the reader
  scrolls, is drawn at once.

## 3. First pages fade in

- A feed's first page, and a profile's posts, fade in over the placeholder
  rows they replace. The same list never fades again while it stays on screen.
- A profile fades in over its loading skeleton.
- Posts that replace cached posts already on screen do not fade: that would
  blink content the reader is looking at.

## 4. Translating fades through

- Tapping Translate fades the translation in as the original goes, while the
  post eases to its new height (180 ms), so the media, card and footer below
  glide instead of jumping. Show original does the same. Translating a whole
  conversation with a long press reflows it tile by tile, smoothly.
- A post expanded with "Show more" stays expanded.
- Only posts that offer translation carry the animation.

## 5. A profile refreshing

- When X's copy replaces the cached profile, Following and Followers roll to
  their new numbers (`RollingCount`), as a post's counts do.
- The "Followed by" row grows in and fades in (180 ms) instead of pushing the
  tabs down at once.

## 6. Things that open inside a feed

- Cashtag price chips grow in under a post's text when their prices arrive.
- Link stacks ("N more posts with this link"), folded posts and repost runs
  ease to their new height as they open and close (180 ms).
- A revealed sensitive post eases from the gate's height to the media's.

## 7. Video

- The mute icon cross-fades between muted and unmuted (120 ms), on the badge
  of a feed video and in the full-screen player.

## 8. Bottom-bar tabs

- Tapping a tab fades the new page in over 120 ms instead of replacing the old
  one in a frame. Swiping between pages slides, as it does today.

## 9. The loading line

- The line over a loading surface fades in when loading starts and out when
  it ends (120 ms).
- With known progress (a group's batches), the line glides from one value to
  the next, and when loading ends it fills to the end before it fades.

## 10. The media viewer's counter

- Swiping to another photo rolls the counter's number up going forward and
  down going back (`RollingCount`).

## 11. The Subscriptions list stays put

- Unsubscribing, or putting someone in or out of the main feed, no longer
  replaces the list with a spinner while the change is saved. The list stays
  where the reader had it; only a first load shows the spinner.

## Reduced motion

XTA's reduce-animations setting and Android's "remove animations" make every
change in this pass happen at once.

## Not in this pass

- Removing a saved post or a like, where the rows below jump up: that needs a
  list that animates removals.
- Switching the home source, search swapping its body, and the Following
  "Posts / Media" switch: each puts a second scrolling body on screen during a
  cross-fade, and nested scrolling allows one at a time.
- "Show additional replies": the new replies land among those already shown.
- Fading in a video's mute badge and loading ring as it starts: both belong to
  several of the player's states, each built afresh, so a fade on arrival
  would replay at every step.

## Tests

- Images: a fetched image fades in over its placeholder, one in memory does
  not, and a group face fades in over its monogram.
- Opening a post: the replies and the reply order bar fade in, the opened post
  does not, and a reply built later is drawn at once.
- First pages: posts fade in over placeholders and not over cached posts; a
  profile fades in over its skeleton.
- Translation: the post eases to the translation's height and back, the new
  text fades in, and an expanded post stays expanded.
- Profile: the counts roll when X's copy replaces the cached one, and
  "Followed by" grows in.
- Expanders: price chips, link stacks, folded posts and repost runs ease their
  height; a sensitive reveal eases too.
- Video: the mute icon cross-fades, and swaps at once with reduced motion.
- Tabs: a tap fades the new page in; a swipe does not.
- Loading line: it fades in and out, progress glides, and it fills before it
  goes.
- Counter: the number rolls on a swipe.
- Subscriptions: a change keeps the list and its scroll position, with no
  spinner.
- Reduced motion: each of these happens at once.
