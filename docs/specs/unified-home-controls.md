# Unified Home controls

## Intent

Implement the approved compact Home proposal on aimdi144 without replacing XTA's Flutter architecture. More content should appear above the fold, source changes should be predictable, and every existing plugin action must remain accessible. This is a UI pull request, not permission to merge or publish a release.

## Layout

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
