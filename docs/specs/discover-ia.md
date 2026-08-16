# Discover IA

The bottom tab labeled Search is `TrendsScreen` (`id: trending`). That hid
trends behind a search icon and left an empty trends list as a blank
`Container`. This pass makes the tab what it already is: a **Discover** hub.

## Shipped

1. Bottom-tab title is Discover (`L10n.discover`). Internal id stays
   `trending` so stored home-page prefs do not need a migration.
2. Empty trends use `EmptyPane` plus “Add a location”, not a zero-size box.
3. Shortcut chips on the hub: Find people (`SearchArguments` users tab) and
   Antennas. Typed search stays the app-bar field; the drawer “Search” tile
   still pushes `ResultsScreen` (active search, not the hub).
4. Heart / bookmark names say they stay on this device. Still local-only;
   still no write to X.

## Non-goals

- Renaming the stored page id to `discover`
- Unread dots on groups (cache rows are keyed by chunk hash, not group id)
- Posting, compose, or like-on-X
