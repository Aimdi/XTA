# Pixiv — remaining Pixez gaps (on top of gallery PR)

Builds on the Pixez-like gallery (Following / Ranking / Bookmarks, grid,
in-app viewer, search). Follow and bookmark write back to Pixiv; there is
no compose.

## This pass

1. **Search polish** — recent query history; open artwork/user by URL or
   numeric ID; illust search target (partial/exact tag, title/caption) and
   sort (newest / popular).
2. **Bookmarks restrict** — public / private toggle for the signed-in user’s
   bookmarks tab.
3. **Local mute** — mute author ids, tag names, and illust ids in prefs;
   filter following / ranking / bookmarks / search / related / profile grids.
   Mute actions from the illust viewer (author / tags / this work).

## Performance

Gallery scroll/decode work (cacheWidth, soft refresh, medium thumbs, viewer
quality ladder, tab keep-alive, …) lives in `docs/specs/pixiv-performance.md`.

## Still later

- Ugoira frame playback
- Novels
- Local download manager
- Comments
- Proxy / mirror modes
