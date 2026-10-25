# X's 2026 motion, part 2

The first pass (`x-motion-2026.md`) brought the heart, photos that fly into
the viewer and pages that follow the back gesture. The owner asked for more of
the new X app's animations. The sources are the same: X's launch video and a
screen recording of the new app, read frame by frame at 12 frames a second.

It adds no posting surface, and a heart here still never changes X's numbers.

## What X does

| Moment | X's motion |
|---|---|
| A count changes | When X sends newer numbers the count rolls: an opened post's views go from 624 to 692 and its replies from 1 to 2 while the page settles. The old number slides up out of its line and fades as the new one comes up from below. |
| Scrolling a profile | Once the header has scrolled away, the top bar shows the profile's banner, blurred and darkened, with the name in white over it. Scrolling back to the top clears it. |
| Dragging a row | In the Timelines sheet a picked-up row lifts: it grows a little, gets a shadow and a lighter surface, and the rows around it make room. |
| A price chart appears | The stock card's line draws itself from left to right, its shaded area following, in about two thirds of a second. |

Already in XTA, so not part of this pass: the home header leaving with the
bottom bar (`HomeHidingHeader`), loading placeholders that pulse, the tab
indicator sliding between tabs, sheets sliding up over a dimmed page, and the
viewer's bars coming in after a photo lands.

## 1. Counts roll

- `RollingCount` (`lib/ui/rolling_count.dart`): when its text changes from
  one number to another, the old text slides up by its line height and fades
  out while the new text slides up into place and fades in. A count that fell
  moves the other way. 300 ms, easeOutCubic. The width moves from the old
  text's to the new one's over the same time, so the neighbours slide instead
  of jumping.
- It rolls only between two shown numbers. A count that appears or goes away
  (zen or calm mode, a footer too narrow for counts, a post without views)
  changes at once.
- Used by the footer's reply, repost, like and view counts and by the opened
  post's "77K Views".
- An opened post first shows the copy it was opened from, then X's answer.
  Its counts roll from the copy's numbers to X's, as in X's recording. The
  conversation screen hands the copy (`CountRollOrigin`) to the first build of
  its focal post only, so scrolling back to it later does not roll again.
- A heart here never changes the count: the count is X's, and a like on this
  device is not a like on X.
- Screen readers hear the new count only.
- A count that never changes costs nothing: its controller is made on the
  first change.

## 2. The profile's top bar

- When the name appears in the bar (as today, once the header has scrolled
  away), the bar fades in the profile's banner behind it, cropped to the bar,
  blurred and darkened. The name and handle rise into place as they fade in,
  and the bar's text, icons and status bar turn light over the banner. 180 ms,
  XTA's standard motion time.
  Scrolling back to the top reverses it.
- Without a banner the bar keeps its own colour and only the title rises in.
- The banner is the copy the header already loaded, so nothing new is
  downloaded.

## 3. A dragged row lifts

- One decorator (`xtaReorderLift`, `lib/ui/reorder_lift.dart`) for every
  reorderable list: subscriptions in custom order, groups, the home tabs in
  Settings and in the add-tab sheet, saved folders, Pixiv favourite tags and
  Booru prompts. The picked-up row grows to 103 %, gets an 8 dp shadow,
  rounded corners and the raised surface colour; dropping it settles it back.
  Flutter's default gave a shadow only, and the subscriptions list had none.

## 4. The price line draws in

- The ticker screen's chart draws its line from left to right, the shaded
  area following, in 700 ms (easeOutCubic), when it first appears and when
  another range is picked. Gridlines, labels and the previous-close line are
  there from the start, and scrubbing works during it.

## Reduced motion

XTA's reduce-animations setting and Android's "remove animations" turn all
four off: counts change at once, the profile bar changes at once, a dragged
row is raised without growing, and the chart's line is there at once.

## Not in this pass

- X's toasts from the top ("Your post was sent") and anything about posting.
- Plugin cards' hearts and photo viewers.
- The stocks plugin's sparklines: its rows are rebuilt while scrolling, so a
  draw-in would replay all the time.

## Tests

- `RollingCount`: rolls between numbers, up for a rise and down for a fall;
  changes at once to or from no count and with reduced motion; screen readers
  get the new text; nothing is animated before the first change.
- Footer: a post whose counts change rolls them; the opened post rolls from
  the copy it was opened from, once.
- Profile bar: scrolled with a banner it shows the blurred banner and a light
  title; without a banner it shows no banner layer; with reduced motion it
  changes at once.
- Reorder: the lifted row grows and rises; with reduced motion it only rises;
  every reorderable list uses the decorator.
- Chart: the line starts undrawn and is complete after 700 ms; another range
  draws it again; with reduced motion it is complete at once.
