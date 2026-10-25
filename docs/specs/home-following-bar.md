# Home: Following in one bar

The owner's design canvas "QuaX Home – kompakter Header" set the current Home
beside two compact headers. They chose concept A, "Eine Leiste": everything
Following's header holds, in one bar.

## Before

Following had two header rows under the status bar:

1. The avatar, which opened the side menu; the source picker (Feeds); search
   in the loaded posts; Home feed accounts with its count.
2. Posts | 🖼 Media, and a wide order pill with the order's name
   ("Benutzerdefiniert"), the active filter count and a chevron. This row
   folded away once the feed was scrolled and came back at the top.

## After

One bar, 48 dp tall, with the divider under it:

- **Start: Posts | Media.** Text only; the Media icon goes. The selected tab
  is bold in the primary colour with a 3 dp accent bar under its label,
  inset by the tab's padding and rounded; the other is medium weight in the
  secondary colour. Each tab is a 48 dp target.
- **End, in order:**
  - **Order and filters**, a 32 dp chip on a quiet surface with a hairline
    border: the sort icon, the active filter count when there is one, and a
    chevron. It opens the same menu as before: Recent, Popular, Custom and
    Filters. The order's name is no longer drawn: the tooltip and a screen
    reader still say it.
  - **Feeds**, the source picker, with its unread dot.
  - **Search loaded posts.**
  - **Home feed accounts**, with its count of accounts left out.

  Each keeps Home's 48 dp target, Android's minimum, where the canvas drew
  44. What each does is unchanged.
- **Narrow screens and large text.** The bar measures the tabs' names in
  their bold, widest state. When they do not fit beside the controls, the
  tabs move closer together and the chip drops its chevron; at 360 dp the
  German "Tweets | Medien" fits this way. When even that is too wide, as
  with large text on a small phone, the tabs move to a second row under the
  controls, as the Subscriptions header falls back to two rows. Nothing is
  cut off or scrolled out of sight.
- **No avatar.** The side menu moves into the Feeds sheet: under "Add
  timeline" a row says "Open navigation menu" (Material's own words, in every
  language) and opens the menu; a long press on it opens the avatar sheet, as
  a long press on the avatar did. The Home tab's long press in the bottom bar
  opens the same sheet, so the row is there too.
- **Scrolling.** The bar leaves and comes back with the bottom bar, as the
  header did. Nothing folds away on its own any more: with one bar there is
  no second row to fold, so the feed keeps its height while it is read.

Plugin sources keep their own compact headers; only Following changes.

## Reduced motion

The tab marker moves at once with XTA's reduce-animations setting or
Android's "remove animations", as it did.

## Not in this pass

- Concept B, the floating pill.
- The bottom bar, which the canvas leaves as it is.

## Tests

- The bar is one 48 dp row: Posts | Media at the start; the chip, Feeds,
  search and Home feed accounts at the end, in that order; no avatar.
- The chip shows the filter count and a chevron, not the order's name, says
  the name to a screen reader, and opens Recent, Popular, Custom and Filters;
  the closer fit drops the chevron, and too little room puts the tabs on a
  second row under the controls.
- The selected tab carries the marker; Media switches the feed to media.
- The Feeds sheet's "Open navigation menu" opens the side menu, and a long
  press on it opens the avatar sheet.
- Scrolling a feed keeps the bar and the feed's height; the bar leaves and
  returns with the bottom bar.
