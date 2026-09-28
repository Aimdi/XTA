# Home source groups and icon sizing

Normalize the Home picker's painted and font icons to one bounded glyph scale,
with optical inset for full-bleed logo paths. Replace the oversized X drawing
with the compact Bootstrap Icons X geometry while retaining theme-aware color.

Project the existing ordered, enabled source options into two expandable rows:
Art (Pixiv, Booru, EH) and Reading (Substack, RSS). Insert each row at its first
member's position and leave a single available member as a direct source row.
Show small service marks and names beneath the group title. Expanding reveals
the original source rows with their original navigation IDs and 48dp targets.
Keep selection, unread state, individual-source search and Add accessible.
Preserve the existing Microblogs grouping and its reversible preference.

This is a Home picker presentation change: no stored pins, plugin settings,
client code, database schema or reader state changes. Verify each source's
returned ID, source availability, search/clear, icon constraints and narrow
German/Arabic layouts with large text. Inspect generated native widget captures
and report separately from real-device checks.
