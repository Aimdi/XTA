# Consistent reader gestures

Extend the reviewed Home gesture policy to existing reader navigation, then
merge and publish aimdi149. Keep the slim layout and existing stores, API,
database, dependencies, and localized controls.

## Interaction contract

- Home content swipes continue to switch sources. Compact plugin section
  icons and section pickers accept horizontal swipes within that plugin.
- Standalone plugin readers accept content swipes between their existing
  sections. Embedded readers leave this gesture to Home source navigation.
  A selected Reddit community sits outside those discovery sections, so its
  body keeps reading/scrolling gestures until a discovery section is chosen.
- Profile, search, quote, and subscription tab views use the same deliberate
  release-only policy as Home: 72 logical pixels, or at least 24 pixels with
  650 logical pixels/second velocity. Opposing releases cancel. RTL reverses
  navigation order; nested media controls keep ownership at their boundaries.
- A second pointer anywhere, cancellation, route/source changes, and all
  reported system gesture insets cancel or exclude navigation. New swipes
  cannot inherit progress from a prior return animation.
- No handoff from the last Subscriptions section to the app's main tab.
- Swipe the Saved source filter to change sources; its menu remains available.
- Keep tap targets, tab buttons/menus, saved-item long presses, text selection,
  pull-to-refresh, photo pinch/double-tap zoom, and video seek gestures usable.
  Horizontal scrolling rails keep scrolling when their labels overflow.
- Reuse the caller's existing selection callback, preserving load behavior,
  lazy mounting, state, and reading position. One haptic per accepted change;
  reduced motion removes navigation animation.

## Work and verification

1. Generalize the existing Home gesture widget into a shared reader widget,
   keeping compatibility with Home. Add a TabController adapter.
2. Connect compact plugin headers, section pickers, standalone lazy readers,
   and existing reader tab views. Test real callbacks, lazy mounting,
   embedded/standalone arbitration, media ownership, cancellation, and RTL.
3. Run focused and full tests, analysis, and independent review. Merge the
   verified PR. Bump the Android version above every aimdi148 variant and use
   the existing signed-release workflow. Verify all APK assets, checksums,
   certificate and source provenance before reporting the release ready.

Phone-only Android back/quick-switch behavior, TalkBack and tactile quality
remain explicit verification limits; widget tests do not establish them.
