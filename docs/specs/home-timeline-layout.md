# Home timeline layout correction

Base: `claude/main` at `a0b3d436c4dcfe38f99b1608c98bdd2b986ae71e`, containing the released aimdi127 application source.

## User problem

The previous redesign retained the same Home header, horizontal tabs, and feed arrangement. The user wants a visible change to Home's layout and to which controls are immediately present, scoped to the home timeline.

## Layout

- The header names the active timeline (Following, For you, or the plugin), keeping the avatar/drawer and existing contextual action icons.
- Move the source strip to a dedicated dock immediately above the existing app navigation. Preserve Following, For you, every pinned plugin, pin order, unread dots, and Add timeline. Use contained selection instead of another underline tab row. Keep all source labels visible by horizontal scrolling and maintain 48dp targets.
- On Following, replace the hidden-only reading choices with a compact toolbar: Recent / Popular / Custom order, a labeled Media toggle, and the existing detailed Filters action. Accounts remains in the header with its active-filter indicator. The existing GroupModel continues to own ordering; Media only changes the cached feed's presentation.
- When Custom ordering is selected, expose its existing configuration from the toolbar. Keep all existing filter settings reachable.
- For you retains its explicit refresh and account controls. Embedded plugins keep their own sections and contextual actions.
- Keep source dock and feed in separate layout regions, not overlaid on posts. Keep the fixed header so a source switch at a restored scroll offset cannot cover controls.

## Follow-up: recover space while reading

- Collapse the Following reading toolbar and Home source dock after the feed scrolls away from the top. Keep the title/actions and app navigation available. Reclaim their layout space, rather than drawing posts behind hidden controls.
- Once collapsed, scrolling upward partway must not reveal either row. Reveal them together only at the actual top (including pull-to-refresh overscroll).
- Follow the active vertical timeline, ignoring horizontal tabs and nested post scrollers. Restore the collapsed state when returning to a source with a saved reading position; an empty or newly opened feed starts with controls available.
- Keep short feeds expanded if removing the controls would make the content fit and force its offset back to zero. Avoid repeated collapse/expand cycles.
- Use the existing motion preference for the transition. Hidden controls must not take taps, keyboard focus, or screen-reader focus. Keep feed controllers, cached pages, and source-tab state mounted through the animation.
- Verify downward scroll, partial upward scroll, exact-top return, source restoration, short content, and reduced motion with the production Home widget. Capture both expanded and reading views.

## Boundaries

Only Home presentation, Home state, focused tests, this spec, and review evidence wiring, plus the two existing GroupModel order setters used by the new toolbar. The interaction check exposed their unawaited boolean SQLite arguments; persist integer flags and await the write before publishing the selected order. No group UI, plugin-client, shared tweet-card, bottom-navigation, client, database, dependency, SDK, signing, or release changes. Retain localized labels and current icons/marks. No new service APIs or X write actions.

## Verification

- Production Home journeys: source switching, plugin sections, loaded-page and scroll restoration, full-client/back, pin reorder/removal, active-plugin disablement.
- Following ordering, media mode and settings must drive the existing model/feed rather than cosmetic selection. Media survives source changes and account/group reloads.
- Geometry and interaction at 320dp, 200% text, RTL, light/dark/true black; dock never overlays content and action targets stay at least 48dp.
- Deterministic before/after production-widget renders with populated Following content, labeled as test renders. Inspect visually before delivery.
- Pinned Flutter 3.44.4 format/analyze/tests and debug APK via repository CI if the local SDK is unavailable. No claim of device testing without a running device.
