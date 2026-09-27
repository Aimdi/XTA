# Slim plugin headers

The user likes the Substack bar released in aimdi147 and wants the other
plugins to use similarly little screen space. Apply that established visual
direction to existing plugin navigation.

- Use one 52dp row for the service mark, section navigation and actions.
- Show section icons with tooltips and selected indicators when they fit.
  Otherwise use a named section picker; every destination remains available.
- Home's service mark opens the source picker. Put the drawer/avatar controls
  and reversible Microblogs service switcher in the existing options sheet.
- Keep the primary search/add action visible. Combine secondary controls in
  options without losing callbacks, menu entries or full-client access.
- Collapse standalone PluginHomeChrome title and tab rows into the same compact
  row. Preserve inner filter controls, safe areas, Back and reader state.
- Retain 48dp touch targets and permit extra height for very large text.
  Preserve service marks, localization and existing light/dark/true-black themes.
- Substack remains the visual reference; X and Reddit retain their specialized
  Home controls at the same compact toolbar height.

Scope is shared presentation and Home hosting. No API, persistence, dependency,
posting capability, plugin membership or release changes.

Verify action reachability and navigation at phone/tablet widths, long labels,
200% text and RTL; exercise assembled Home and microblog reader regressions.
Inspect widget renders separately from automated tests. Device verification is
not implied by widget renders.
