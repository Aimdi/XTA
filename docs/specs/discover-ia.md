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

5. Group tiles, the groups list, and the drawer show an unread dot when
   reading position is on (new installs default on) and a group's cached
   chunks are newer than `feed_read_position.updated_at`. Hashing matches
   the live feed (`feed_chunk_hash.dart`) so parent groups include nested
   members. Existing installs keep their stored off default.
6. Following and For You on the home strip use the same unread dot.
   Following hashes in-feed accounts the way the live `-1` feed does.
   For You compares `feed.for_you_newest_cached_at` (last first page) to
   `for_you`'s last-read time — there is no chunk table for that tab.

## Non-goals

- Renaming the stored page id to `discover`
- Unread dots on pinned plugin strip tabs
- Decoding tweet timestamps from chunk JSON (chunk write time is the heuristic)
- Forcing reading-position on for existing installs
- Posting, compose, or like-on-X
