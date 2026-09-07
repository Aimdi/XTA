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

## Latest direction: compact reader and a source picker

The user asked for a more substantial redesign informed by internet/GitHub research. This direction supersedes the dock and toolbar arrangement above, while preserving top-only return.

### Research and alternatives

- [Moshidon HomeTabFragment](https://github.com/LucasGGamerM/moshidon/blob/d90b8a88243608629e7f91b460c77d1089514bb6/mastodon/src/main/java/org/joinmastodon/android/fragments/HomeTabFragment.java) uses the timeline title/icon as a menu trigger. Adapt the navigation idea: a compact source picker in XTA's title.
- [Bluesky HomeHeader](https://github.com/bluesky-social/social-app/blob/08069e2877be9a660aeb70dc6eef4dc7b36e3762/src/view/com/home/HomeHeader.tsx) keeps named pinned feeds together and exposes feed discovery. Preserve named sources, pin order, unread indicators, and Add timeline in one place.
- [Read You FlowPage](https://github.com/ReadYouApp/ReadYou/blob/d2b979ccad9a3e54b9499929a9dc2824578b0fd9/app/src/main/java/me/ash/reader/ui/page/home/flow/FlowPage.kt) separates reading content and filter navigation and collapses the title area. Its repository screenshot also shows a strong reading hierarchy. Use the separation, with XTA's compact header and existing visual tokens.
- Considered a magazine layout with featured posts and a larger source rail. Neither suits the user's space concern as well as a compact reader. Featured posts would also interfere with the actual timeline order.

### Implementation

- Remove the entire 64dp bottom source dock from Home. Keep the main app navigation.
- Make the active source title a clear 48dp-minimum button with its existing icon/mark and chevron. Open a scrollable sheet with Following and For you first, then pinned plugins, selected/unread states, and Add timeline. Keep source selection available while reading, without automatically opening or revealing any rows. Choosing a source costs a second tap but supports long names and many plugins without horizontal hunting.
- Replace the separate sort/Media/filter buttons with clear Posts / Media tabs and one order/filter menu at the trailing edge. Keep the active order visible and move the filter-count badge to the menu that opens those filters. The filter menu also retains Recent, Popular, and Custom configuration.
- The 56dp reading row hides while scrolling and returns only at the top. Reuse the existing source-specific scroll observation, short-content guard, reduced-motion handling, and accessibility exclusions. Adjust reclaimed height to match the actual remaining row.
- Source switching must keep cached pages, Media choice, restored offsets, plugin sections, full-client/back, pin changes, and explicit For you refresh working. A source picker opened at a nonzero offset must not reset that position.
- Reuse existing localized strings, marks and theme tokens. Implement original Flutter code; do not copy external application code.
- Add production-widget checks for picker selection/dismissal, source order/unread presentation, the new Posts/Media selection, and sorting/filter entry. Update the scroll-height expectations and capture the new picker as well as theme/reading views.

## Implementation boundaries

Only Home presentation, Home state, focused tests, this spec, and review evidence wiring, plus the two existing GroupModel order setters used by the new toolbar. The interaction check exposed their unawaited boolean SQLite arguments; persist integer flags and await the write before publishing the selected order. No group UI, plugin-client, shared tweet-card, bottom-navigation, client, database, dependency, SDK, signing, or release changes. Retain localized labels and current icons/marks. No new service APIs or X write actions.

## Verification

- Production Home journeys: source switching, plugin sections, loaded-page and scroll restoration, full-client/back, pin reorder/removal, active-plugin disablement.
- Following ordering, media mode and settings must drive the existing model/feed rather than cosmetic selection. Media survives source changes and account/group reloads.
- Geometry and interaction at 320dp, 200% text, RTL, light/dark/true black; dock never overlays content and action targets stay at least 48dp.
- Deterministic before/after production-widget renders with populated Following content, labeled as test renders. Inspect visually before delivery.
- Pinned Flutter 3.44.4 format/analyze/tests and debug APK via repository CI if the local SDK is unavailable. No claim of device testing without a running device.
