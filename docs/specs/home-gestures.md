# Home reading gestures

The reader should switch sources with one deliberate horizontal swipe, while
vertical scrolling, media browsing, selection, and Android back retain their
existing meanings. Preserve the compact headers and all visible controls.

X documents swiping between timelines and pinned Lists:
https://help.x.com/en/using-x/x-timeline and
https://help.x.com/en/using-x/stay-connected. Adapt that navigation pattern to
XTA's existing Following, X, and enabled pinned sources. XTA remains read-only
toward X; this change adds no server-side actions.

## Interaction contract

- Home reading content: swipe toward the next or previous source in the same
  stable order as the source picker. RTL reverses the physical direction.
- Show the destination in a small overlay while dragging. It consumes no layout
  space and disappears on cancellation or completion. Keep content stationary.
- Commit once on release after sufficient travel, or a sufficiently fast swipe
  with meaningful travel. A reversed release, pointer cancellation, second
  finger, changed source/order, or covered route cancels the pending switch.
- Nested horizontal controls own their sequences, including at their ends.
  Remove the media strip's old overscroll-to-main-navigation handoff.
- Reject starts inside system gesture insets, with a small conservative edge
  margin when Android reports none. Do not intercept OS back.
- Reuse the same gesture policy for the bottom navigation bar. Do not re-enable
  full-page swiping between the app's main destinations.
- Give one selection tick only after a real navigation change. Cancelled and
  boundary gestures stay quiet. Respect reduced motion in both Android and XTA.
- Existing buttons, source picker, long-press Home, and tap-to-top remain the
  accessible alternatives. Use existing localized source/destination labels.

## Implementation and verification

Keep selection in existing flutter_triple stores, fetching in existing feed
controllers, and transient pointer/animation state local to the gesture widget.
Retain feed caches and reading positions. No API, database, pinned dependency,
or generated-source edits.

Verify actual Home source changes and return to cached content; slow swipes,
flicks, cancellation, reversal, multi-touch, RTL, edge starts, source changes,
child carousels/sliders, and reduced motion. Verify bottom-bar tap/long-press
behavior and one haptic per accepted navigation. Inspect light/dark/large-text
preview renders. System back, TalkBack, and tactile feel still require a phone.
