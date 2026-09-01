# Substack plugin — closer to the Substack app

Read-only. No login, no write, no paid unlock. Builds on
`docs/specs/substack-improvements.md`.

## Goal

Make the Substack tab feel like the Substack mobile app’s **Home + Notes**
shell, not a generic list bolted onto XTA.

## This pass

1. **Home | Notes tabs** — custom top tabs under the app bar (not a second
   bottom bar; XTA already owns the home NavigationBar). IndexedStack keeps
   each pane’s scroll position.
2. **Publication strip** — circular logos (story-style) with unread dots;
   tap opens the publication page; long-press unfollows.
3. **Cover-first post cards** — when a cover exists, lead with media; title
   and publication sit below (Substack Home layout). Pass publication logos
   into cards from the follow list.
4. **Publication page hero** — logo, name, optional description, Follow.
5. **Mark all read** — app-bar action on Home when there are unread posts.

## Follow-up (internal substitutes)

See `docs/specs/substack-internal-substitutes.md`: Inbox + Library tabs,
local like/save, in-app Notes reader. Still no Substack login or write APIs.

## Out of scope

- Personalized Following Notes (session)
- Compose / react / restack **to Substack**
- DB schema for description (fetch on open is enough)
