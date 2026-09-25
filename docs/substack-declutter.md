# Substack reading layout

Base: aimdi141 (`d629bd2593e74d1436d955a4bd46248c5d90a632`).

The screenshot shows four permanent control rows and a large partial-failure
banner above the first article. Keep the existing article styling and source
identity, while giving the feed the screen space.

- Keep Home, Inbox, Notes, and Library navigation. Put Discover, Add publication,
  and Mark all as read in one accessible overflow menu.
- Replace the publication avatar strip, permanent search box, and filter chips
  with a single compact toolbar. Publication selection opens a named list;
  search and filter buttons open a sheet with the existing local query, sort,
  content filters, and reset action.
- Show an active-filter indicator so hidden settings never silently explain a
  missing post. Preserve Home/Inbox options when changing tabs.
- Show partial publication failures as a compact, labelled warning action.
  Its details and retry remain accessible without pushing down readable posts.
- Preserve the Store pattern, translated labels, navigation, refresh behavior,
  and existing source/network code. Do not modify frozen client/database code.

Validation: exercise search, filters, reset, publication selection, overflow
actions and partial-failure retry; check first-card position; render normal and
narrow large-text/RTL layouts. Verify the full existing suite before release.
