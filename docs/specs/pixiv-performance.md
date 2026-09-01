# Pixiv — performance (Pixez-oriented)

Read-only. Builds on `pixiv-plugin.md` / `pixiv-pixez-gaps.md`. Oriented
around how [pixez-flutter](https://github.com/Notsfsssf/pixez-flutter) keeps
galleries smooth — **no code copied**.

## This pass

1. **Decode caps** — `cacheWidth` on masonry tiles and avatars (same pattern as
   tweet/`CappedNetworkImage`), with Pixiv Referer headers.
2. **Aspect thumbs** — prefer `medium` over cropped `square_medium` for the
   waterfall; keep square as fallback.
3. **Viewer quality ladder** — page images prefer `large` over `original`;
   decode capped to viewport; thumb under large until high-res lands.
4. **Soft refresh** — pull-to-refresh keeps the grid; no blank spinner wipe.
5. **Pagination** — earlier load-more, visible `loadingMore`, id dedupe, soft
   append errors (keep the list), larger `cacheExtent`, thumb prefetch.
6. **Illust open** — parallel detail + related; Hero from tile; seed stays up.
7. **Tabs** — keep-alive so Ranking/Bookmarks don’t drop scroll/decodes.
8. **Mute rebuilds** — drop animated `.transition` on the grid mute builder.
9. **No forever spinners** — image `timeLimit`, token refresh single-flight,
   bookmarks without forced `verify()`, parallel search/user loads, skip fully
   filtered empty pages so R18/mute cannot leave a blank feed.

## Out of scope

- Ugoira / novels / download manager / proxy modes
- Pixiv write APIs
