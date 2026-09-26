# Unified Home controls

## Intent

Implement the approved compact Home proposal on aimdi144 without replacing XTA's Flutter architecture. More content should appear above the fold, source changes should be predictable, and every existing plugin action must remain accessible. This is a UI pull request, not permission to merge or publish a release.

## September 26 refinement: Reddit-style plugin headers

The user requested Reddit's minimal Home presentation across plugins, with options on the right. Embedded plugin feeds now use the existing 56px Home header as their only navigation toolbar. The source title can show the active section beneath it at normal text sizes; at larger sizes the section remains available in the options sheet and accessibility label.

Search or the primary action stays direct. A right-side options button opens section selection, Alt Microblogging service switching and the existing reading controls. Secondary actions share an overflow menu, including Open full client. Closing the sheet must leave the reader and its loading/scroll state intact. Reddit and X retain their existing compact host controls. Other embedded feed plugins use the shared host without an allowlist. Standalone navigation stays independent.

The earlier always-visible service/context row is superseded by this smaller layout. No automatic collapse is needed for the plugin navigation; the existing expanded host remains supported for standalone fixtures and consumers.

## Earlier layout (superseded where stated above)

- Home owns the source title, avatar, and primary actions. For migrated embedded readers, plugin navigation and reading controls share a context row rather than adding independent toolbars.
- Alt Microblogging retains its reversible category and one-tap service switching. Use existing service marks with 48px targets; the title names the selected service. Fall back to readable additional rows with long labels or large text.
- Preserve each plugin's existing search scope, filters, source ordering, paging, and standalone client behavior. Move Open full client to a named menu action only where the common actions host is available.
- Bluesky combines local sorting/filter controls; Substack combines section and publication/reading controls. Publications must still open the archive, not silently become a filter.
- The title and long-press Home launch the same live source chooser, including groups, unread state and Add. Normal Home tap retains scroll-to-top behavior. Keep pin order stable. Add local search for larger collections and flatten ordinary rows without shrinking targets.
- Suggestions should not occupy the first-content slot. Insert after initial content where possible without inserting above a restored reading anchor; preserve profile/follow actions.
- Secondary controls may recede during sustained reading and return on deliberate upward motion. Never hide focused controls or collapse short content into oscillating layout. A visible way to keep controls open is required before enabling automatic collapse for a newly migrated surface.

## Boundaries

Preserve accounts, subscriptions, saved items, sessions, independent scroll positions, grouping undo, existing icons, true-black themes and localization. Keep flutter_triple. Do not edit lib/client, lib/database, generated code, dependencies, Flutter 3.44.4, signing or release metadata. No remote X write actions.

## Proof

Tests must exercise real widget placement and moved actions, not just the new components in isolation: 320/390/840 widths, LTR/RTL, 100/200 percent text, long names, loading/error/empty/filtered-empty states, source switching, and untouched standalone clients. Report source checks, Flutter tests, screenshots and physical-device checks separately. Capture failing behavior before implementation and run the existing regression suites afterwards.
