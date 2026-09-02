# Aimdi126 Home refinement

## Scope

Refine only the main Home feed surface on the released `aimdi125` baseline.
The destination screens reached from the bottom navigation are not redesigned.
Shared widgets may gain Home-specific wrappers, but their existing callers must
remain visually unchanged.

## Preserve

- The drawer avatar, localized Home title, and current icons.
- Following and For you as the first two feed-strip entries.
- Pinned plugin order, plugin brand marks, unread badges, and the trailing add
  action.
- Following filters, For-you refresh, account filtering, and Reddit-specific
  actions.
- Pull-to-refresh, paging, feed caching, read position, and scroll-to-top.
- Icon-only bottom navigation by default, optional labels, bar swipes, dynamic
  destinations, and the Networks overflow.
- Local likes, local saves, quote navigation, sharing, and translation.
- System, Light, Dim, and Lights Out themes with every selectable accent.

## Home chrome

- Keep the pinned app bar flat at every scroll offset. Home chrome must not gain
  a Material elevation or surface tint over true black.
- Keep each app-bar action in an independent 48dp target. Preserve icon order
  and add a small end inset so the final action does not crowd the screen edge.
- Keep the title leading-aligned, single-line, and ellipsized at large text
  scales.

## Feed strip

- Use one 48dp-high, flat strip directly below the title row.
- Keep tabs horizontally scrollable and leading-aligned.
- Use compact 12dp horizontal tab padding, the existing 14sp label style, real
  plugin marks, and a restrained 2dp accent indicator.
- Keep the add action fixed outside the scrollable tabs in its own 48dp target.
  Separate it with a single hairline so it reads as an action, not another tab.
- Keep one hairline under the complete strip. Do not stack the TabBar divider
  with the shell divider.
- Preserve unread semantics in addition to the visible dot.

## Feed and navigation

- Do not add a second toolbar for sorting, content type, Zen, or Calm mode.
- Do not globally redesign shared tweet cards. The existing flat tiles, 16dp
  media radius, footers, loading, empty, error, and pagination states remain.
- Keep the current 64dp bottom navigation and existing icons. This pass may add
  tests, but it must not rename destinations or change their screens.

## Accessibility and responsive behavior

- Every icon action must retain at least a 48dp target.
- At 320dp width, the tabs scroll while the add action remains visible.
- At 200% text scale, labels may grow or scroll horizontally but must not
  overlap the add action or app-bar actions.
- Preserve semantic labels, focus order, RTL directionality, reduced motion,
  and color-independent unread state.

## Verification

- Add characterization tests for Home action geometry and the dedicated strip.
- Run Home-focused tests, full tests, analysis, and a debug APK build with
  Flutter 3.44.4.
- Confirm that `lib/client`, `lib/database`, generated localization, shared
  tweet visuals, and non-Home destination screens are unchanged.
