# Adaptive Plugin Store

Improve the existing Plugin Store without changing the catalogue, plugin
lifecycle, persistence, navigation, or XTA's read-oriented product boundary.

## Opportunity and priority

The current store is functional, but four presentation details make discovery
harder than it needs to be:

1. A search with no matches leaves an unexplained blank area.
2. Store content stretches edge to edge on tablets and in landscape.
3. Installed-plugin actions compete with the title and footprint on narrow
   screens and at large text sizes.
4. Two icon controls use 36dp targets, below XTA's 48dp Android target.

This slice ranks above broader redesign ideas because it reaches every reader
who explores optional sources, has low implementation risk, and can be verified
without live services or changes to `lib/client/` or `lib/database/`.

## Product assumptions and checks

| Assumption | Low-cost check | Success threshold |
|---|---|---|
| Searchers need an explicit terminal state | Widget test a query with no matches | Existing localized `no_results` state is visible and announced |
| Inline actions fail before the row content does | Render rows at 320dp and 200% text | No overflow; actions move below the identity block |
| Wide layouts need readable measure, not stretched rows | Render at tablet width | Store content stays centered at the existing settings width |
| Off-screen footprint reads are unnecessary work | Build through a lazy list | Footprint widgets initialize only when their rows enter the viewport |

Physical-device confirmation should still cover TalkBack focus order and
landscape ergonomics. Do not claim frame-time or memory improvement without a
profile-mode trace on a representative Android device.

## Implementation

- Keep catalogue refresh, search matching, install, open, configure, tab
  visibility, uninstall, and private-plugin behavior unchanged.
- Constrain the store to `kSettingsContentWidth` and center it on wider windows.
- Use a lazy list for top-level store entries so installed-plugin footprint
  reads are tied to visible rows.
- Extract a presentation-only plugin tile that adapts from one row to stacked
  identity/actions using `LayoutBuilder` and the actual parent width.
- Keep all icon actions at a minimum 48x48dp target with visible Material press
  feedback and existing tooltips.
- Reuse `L10n.no_results`; add no localization keys.
- Add isolated light/dark and large-text Widget Previewer configurations for
  the presentation-only tile. Previews must not load plugin storage or native
  APIs.

## Tests

- Available and installed actions remain available.
- A no-match search displays `no_results`.
- At 320dp and 200% text, actions stack and the tile has no overflow.
- At wider widths, actions remain inline.
- Icon action targets are at least 48x48dp.
- Existing query matching and install/uninstall behavior remain covered by the
  current tests.

## Boundaries

- No new dependencies, raw UI strings, database changes, client changes, or
  network endpoints.
- No plugin card/grid redesign and no change to brand marks.
- No claim of device profiling without a connected physical Android device.
