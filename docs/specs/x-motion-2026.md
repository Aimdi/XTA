# X's 2026 motion

X relaunched its Android app on 2026-07-20, rebuilt in Kotlin and Jetpack
Compose. Reviews single out its motion: "a wide range of animations" that make
the interface feel fluid, and predictive back. The owner asked for the same
animations in XTA.

This pass brings the three that a reader meets on every post. It adds no
posting surface. The heart stays a like on this device: its tooltip and
screen-reader label still say so.

## What X does

Read frame by frame from a screen recording of the new app (12 frames a
second) and X's launch video:

| Moment | X's motion |
|---|---|
| Liking | The outline heart shrinks to a dot, a filled circle blooms out of it, turns into a ring that thins away while small dots scatter, and the filled heart pops back with a little overshoot. About 0.4 s. |
| Opening a photo | The photo grows out of its place in the post to fill the screen while the page behind darkens to black. The viewer's bars come in after it lands. |
| Closing a photo | The reverse: the photo shrinks back into its place as the black lifts. Pulling it down does the same from wherever the finger lets go. |
| Opening a post or profile | The new page fades in while settling from a few percent larger to its size, in under 0.2 s. Nothing slides across the screen. |
| Going back | With Android's back gesture the page shrinks and follows the finger, the page behind showing around it. It goes on letting go and comes back on cancelling. |

## 1. The heart

- Liking plays X's sequence around the heart (`HeartBurst`, `lib/ui/heart_burst.dart`):
  shrink to a dot (0–15 %), bloom (15–45 %), ring thinning to nothing
  (40–70 %), dots scattering and fading (35–85 %), the filled heart popping
  back to 1.2× and settling (45–100 %). Total 450 ms.
- The burst is drawn outside the heart's box without taking space: the footer
  does not move, and the 48 dp touch target is unchanged.
- It plays in the heart's own colour, the theme accent that marks a device
  like, not X's pink, so it does not pose as a like on X.
- Unliking swaps the icon without a burst, as X does.
- Off with the reduced-motion setting, Android's "remove animations", calm
  mode and zen mode: the heart just fills.

## 2. Photos

- A tweet's photo flies from its tile into the fullscreen viewer and back
  (`Hero`). The tags are unique to the tile that opened the viewer, so the
  same post shown twice on a screen does not confuse them.
- The viewer still darkens to black as it opens, now under the flying photo,
  which is X's darkening. Once open it is an opaque page again, so the feed
  behind it stops drawing, and closing takes as long as opening (180 ms) so the
  flight back can be seen.
- The flight shows the picture the tile has already decoded
  (`TweetPhotoFlight`), its crop opening from the tile's to the whole photo,
  so nothing flashes black while the viewer's copy loads. Tapping a photo
  waits up to 180 ms for that copy, which is long enough for one on disk, so
  the flight lands on the photo rather than its loading box.
- Pulling a photo down to close lets it fly back from where it was let go.
- Swiping to another photo of the same post and closing flies that photo back
  to its own tile. A photo with no tile on screen (it scrolled away, or came
  from elsewhere) closes with the plain fade.
- Videos and GIFs keep the fade: their pooled player cannot be in two places
  at once.
- With reduced motion the viewer opens and closes at once, as today.

## 3. Pages

- `XtaPageTransitionsBuilder` (`lib/ui/page_transitions.dart`) replaces the
  quick fade on Android, Linux and Windows. A pushed page fades in while
  scaling from 1.04 to 1 (200 ms in, 160 ms back), easeOutCubic. The page
  underneath stays put.
- On Android 14 and newer, the system back gesture drives the page: it shrinks
  towards 90 % and moves with the finger, its corners rounding, with the page
  behind visible around it. Letting go past Android's threshold finishes the
  pop; cancelling brings it back. A back button press or key is the ordinary
  fade.
- The manifest opts in to Android's back callbacks
  (`android:enableOnBackInvokedCallback="true"`), which Android 13–15 need
  for the gesture to reach Flutter. Existing `PopScope`s still decide whether
  a page may leave.
- With reduced motion, pages appear and leave at once and the gesture is left
  to the system, which pops on release.

## Not in this pass

- The profile bar showing the banner, blurred, behind the name as it
  collapses.
- Count changes rolling, the photo editor and anything to do with posting.
- Plugin cards' hearts and photo viewers; `HeartBurst` is written so they can
  adopt it.

## Tests

- `HeartBurst`: plays on liking, never on unliking or with reduced motion,
  calm or zen mode; takes no layout space.
- Photos: a tile and its viewer page share a hero tag, two tiles of the same
  post do not, and a video tile has none.
- Pages: the builder fades and scales; with reduced motion it adds nothing;
  a back gesture's progress shrinks the page and a cancel restores it.
