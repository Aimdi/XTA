# X smoothness pass

Screen recordings of the official X app next to XTA showed four places where
X feels smoother. This pass closes them without changing what XTA reads or
adding any posting surface.

## 1. Use the display's fast refresh rate (Android)

Many phones keep an app at 60 Hz unless its window asks for more, and Flutter
does not ask. `MainActivity` requests the fastest display mode at the current
resolution whenever the activity resumes, so a 90/120 Hz screen scrolls at its
full rate. The resolution never changes, and a 60 Hz-only screen is untouched.

Check on a device: Developer options → "Show refresh rate", then scroll a feed.

## 2. Opening a post

- Pushed screens use a quick fade (200 ms in, 160 ms back) instead of the
  slide-up `FadeUpwardsPageTransitionsBuilder`. The reduce-motion setting still
  turns transitions off. Since X's 2026 app they also settle from 1.04× and
  follow Android's back gesture; see `x-motion-2026.md`.
- A post opened from a timeline already shows at once from the tapped data.
  While its replies load, the post screen shows grey post placeholders
  (`TweetFeedSkeleton` / `TweetSkeletonTile`) instead of spinners, as
  timelines and profiles already do. This covers the first page, the
  zen and threaded views and the next-page footer.

## 3. Photos in a feed

- While a timeline photo loads, its box shows the skeleton surface colour
  instead of black with a spinner.
- A photo fetched from the network fades in (180 ms). One already in memory,
  such as when scrolling back, appears immediately.
- The fullscreen viewer keeps its black background and spinner.

## 4. Home top bar hides with the bottom bar

- The home header collapses when the bottom bar leaves, and comes back with
  it. The header is the source title, actions and feed tabs; the status-bar
  strip stays.
- It follows the bottom bar's existing rules: it returns at the top or end of
  a list, on a tab change, and with a screen reader on.
- It uses the existing collapse animation (`HomeCollapsingControls`). The
  home header stays out of a `NestedScrollView`, since that layout broke
  plugin tabs before.
- The existing setting controls both bars. Its label changes from "Hide
  bottom bar while scrolling" to "Hide top and bottom bars while scrolling".

## 5. The reply order gets its own row

Under an opened post, the "Relevant ▾" control sat loose on the black
background, and in threaded mode the opened post's thread line showed through
it. It is now a row of its own (`ReplySortBar`), closed by the hairline that
separates posts, and sits outside the thread rail, as on X.

## Not in this pass

- The profile header: X keeps that bar pinned as well.
- Explore page banners, larger focal-post text and the floating "new posts"
  pill.
