# Swipe back from a post as a card

The owner's concept "XTA swipe-back concept" sets how an opened post leaves:
drag it and it lifts off as a card that turns around the finger, throw it
and it flies away, let it go short and it drops back. They asked for the
animation in XTA. It only moves pages; nothing is posted or written.

## Before

- A post opens like every page (`x-motion-2026.md`): it fades in while
  settling from 104 % (200 ms) and leaves the same way (160 ms).
- On Android 14 and newer the system back gesture shrinks the page towards
  90 % with the page behind around it. There is no swipe back inside the app.

## After

### Opening and leaving

- A post slides in from the right edge (260 ms, easeOutCubic). On the way it
  is lifted into a card, a little smaller, its corners rounded and a shadow
  under it, and it lands flat: the lift fades over the last 40 % of a width.
- The page it was opened from shrinks to 96 %, rounds its corners (16 dp) and
  darkens by 25 % as the post covers it, over a near-black backdrop. It is
  back to full size, square and clear once the post is 85 % of a width away.
- The back button, Android's back button and a pop from code slide the post
  out past the right edge (220 ms, easeInQuad), lifted the same way.
- In a right-to-left language all of this is mirrored: the post comes from the
  left and is dragged to the left.

### Dragging

- Drag the post to the right to go back. A drag is the post's once it has gone
  6 dp, more to the right than 1.15 times up or down; anything else is left to
  the page, which scrolls as before. It follows the finger both ways; dragged
  left of where it rests, it gives only a third.
- The moment the drag is the post's, it lifts into a card in 140 ms: 6 %
  smaller and 3 % more over a full width of travel, 24 dp corners, a shadow.
- **Tilt with your finger** (on by default): the card turns around the point
  where it was grabbed. Across a full width it turns up to 7°, clockwise when
  grabbed above the middle and the other way below it, 55–100 % of that by how
  far from the middle. It also swings with the finger's speed (3° per px/ms,
  at most 4°) and swings back when the finger stops.
- Letting go:
  - Past 30 % of the width or 25 % of the height (an ellipse between the two),
    or thrown faster than 350 px/s away from rest after 16 px of travel, the
    card flies off along the throw, or along the drag when let go slowly. It
    starts at the throw's speed and eases out until it is off screen, in
    140–260 ms, turning 10° more with tilt on. Then the post is gone.
  - A flick back towards rest faster than 200 px/s never leaves.
  - Otherwise the card drops back on a stiff spring that does not overshoot
    (stiffness 900, critically damped), carrying the speed it was let go at;
    its turn and swing settle and it lands flat.
- Grabbing it again while it drops back catches it where it is.
- The page behind follows the card in every phase, as it does while the post
  slides in and out.

### Where a drag can start

- **Swipe back from anywhere** (on by default): anywhere on the post.
- Off: only from the left edge, 28 dp wide (the right edge in a right-to-left
  language).
- Never inside Android's system gesture edges. A swipe there is Android's back
  gesture, which moves the card too (below).
- What moves sideways inside the post keeps its drags: the row of a post's
  photos and a video's seek bar take a drag the moment the card could, and
  win it. A drag that starts on a button does not press it.
- A post that may not be left (a `PopScope` that says no) cannot be dragged
  away, and nothing drags while a dialog or sheet is over the post.

### Android's back gesture

- On Android 14 and newer the system back gesture lifts the card and moves it
  with the finger: to the right from the left edge, to the left from the right
  edge, turning around the touch point with tilt on. Letting go past Android's
  threshold throws it off that way; cancelling drops it back.
- A back button press is the ordinary slide out.

### Reduced motion

With XTA's reduced-motion setting or Android's "Remove animations":

- The post appears and leaves at once, and the page behind is drawn as it is.
- A drag still moves the post with the finger, flat: no lift, turn or swing.
  Letting go past the line leaves at once; otherwise it is back at once.
- Android's back gesture is left to Android, which pops on release.

### Unchanged

Every other page keeps its fade and its back gesture. Photos still fly into
the viewer from the post, and the post's media row, polls, links and buttons
work as before.

## Settings

Accessibility, after "Double-tap to like":

- "Swipe back from anywhere": "Drag an opened post from anywhere on it to go
  back. Off: only from the edge of the screen".
- "Tilt with your finger": "A post you drag back turns around your finger and
  swings with your speed".

The concept's "Slow motion" and "Show gesture values" are its demo controls
and are not carried over.

Strings: `swipe_back_anywhere`, `swipe_back_anywhere_description`,
`swipe_back_tilt`, `swipe_back_tilt_description`, in every language.
Preferences: `gestures.swipe_back_anywhere` and `gestures.swipe_back_tilt`,
both on by default.

## Code

- `lib/ui/swipe_back_card.dart`: the concept's numbers and the pure parts:
  the card's pose and transform, its lift, turn and swing, the page behind,
  the release decision, the throw and the spring.
- `lib/ui/swipe_back_drag.dart`: the recogniser that decides when a drag is
  the card's.
- `lib/ui/card_page_route.dart`: `XtaCardPageRoute`, the route a post opens
  in: the slide in and out, the drag and Android's back gesture, and the page
  behind as its delegated transition.
- `lib/main.dart`: `routeStatus` opens in `XtaCardPageRoute`.
- Settings: `lib/settings/_accessibility.dart`,
  `lib/settings/settings_search_index.dart`, `lib/constants.dart`.

## Tests

- Pure parts: the lift over time and across a slide, the turn by grab point,
  the swing following speed, the release decision (distance, ellipse, fling,
  flick back), the throw's direction, length, duration and turn, the spring
  landing without overshoot, the page behind's scale, corners and dimming.
- The recogniser: a drag to the right is taken, one to the left or up is not,
  a sideways scroller inside wins its own drags.
- The route: a post slides in and out; a drag past the line pops it and one
  short of it drops back with the post still open; a throw pops it; the page
  behind is scaled while the post is moved; edge-only drags start only at the
  edge; a `PopScope` that says no keeps the post; Android's back gesture
  throws the card on commit and drops it back on cancel; with reduced motion
  nothing animates and the drag still works; right-to-left mirrors the drag.
