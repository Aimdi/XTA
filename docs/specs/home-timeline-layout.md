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

## Boundaries

Only Home presentation, Home state, focused tests, this spec, and review evidence wiring. No plugin-client, shared tweet-card, bottom-navigation, client, database, dependency, SDK, signing, or release changes. Retain localized labels and current icons/marks. No new service APIs or X write actions.

## Verification

- Production Home journeys: source switching, plugin sections, loaded-page and scroll restoration, full-client/back, pin reorder/removal, active-plugin disablement.
- Following ordering, media mode and settings must drive the existing model/feed rather than cosmetic selection. Media survives source changes and account/group reloads.
- Geometry and interaction at 320dp, 200% text, RTL, light/dark/true black; dock never overlays content and action targets stay at least 48dp.
- Deterministic before/after production-widget renders with populated Following content, labeled as test renders. Inspect visually before delivery.
- Pinned Flutter 3.44.4 format/analyze/tests and debug APK via repository CI if the local SDK is unavailable. No claim of device testing without a running device.
