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
- Substack remains the visual reference. X and Reddit use the same Home row as
  Pixiv and Hacker News: service mark as source picker, sections when the
  source has them (Reddit: Following, Popular, All), search as the visible
  primary action, and the options button. X's library destinations and its
  timeline actions (search loaded posts, refresh, Home feed accounts, with the
  attention dot while accounts or groups are filtered) move into options; X
  needs no Open client entry there. Reddit's sort, saved, communities, reading
  route and settings join options, and its followed-community chips move there
  instead of adding a row above the feed. Only Following keeps its own app bar
  and reading controls.

Scope is shared presentation and Home hosting. No API, persistence, dependency,
posting capability, plugin membership or release changes.

Verify action reachability and navigation at phone/tablet widths, long labels,
200% text and RTL; exercise assembled Home and microblog reader regressions.
Inspect widget renders separately from automated tests. Device verification is
not implied by widget renders.
