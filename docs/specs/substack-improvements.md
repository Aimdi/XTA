# Substack plugin — gaps vs normal Substack

Read-only public client. No Substack login, no write actions, no paid unlock.

## Already shipped

Local follows, merged feed, archive + search, public reader, free previews of
paid posts, comments (read), podcast audio, TTS, group interleaving, open/share
in browser.

## Missing vs normal Substack — and how we supplement

| Normal Substack | Reality here | Supplement |
|---|---|---|
| Account / email subscriptions | No login | Local follows in SQLite |
| Full paid posts | Public JSON only | Free preview when Substack sends `body_html`; else lock pane → browser |
| Notes (Following) | Needs session | Public discovery via `GET /api/v1/reader/feed` (global Notes mix); open note on substack.com |
| Like / comment / restack | Write APIs | Local like + save (device only); read-only comments; share instead of restack |
| Personalized recommendations | Auth | Follow from a Note’s publication; RSS channel metadata |
| Cross-pub search / inbox | Account feed | Inbox tab (unread follows); Library search; per-pub archive search; filter chips |
| Offline | None before | Cache public article bodies (FFCache) after first open |
| API outages / odd hosts | JSON-only was brittle | RSS `/feed` fallback for listings + publication name/logo/description |

## This pass (MVP)

1. RSS `/feed` fallback when `/api/v1/posts` fails or returns empty
2. Feed filter chips: All · Unread · Free · Podcast
3. Public Notes discovery tab (reader feed), open on site; Follow publication
4. Article body cache after successful fetch
5. In-reader interception of Substack post links → in-app reader

## Discover + TTS (follow-up)

1. Discover sheet: category leaderboards (`/api/v1/categories` +
   `/api/v1/category/public/{id}/all`) and type-ahead search
   (`/api/v1/publication/search` + handle/URL slug probe)
2. Notes host rotation across followed publications (fallback `substack.com`)
3. TTS always has title/excerpt fallback; live-site pages extract article text
   via the web view; Listen stays available for paid teasers; engine failures
   open Voice settings

## Explicitly out of scope

- Pasting `connect.sid` / paid unlock
- Writing notes, comments, reactions
- Email digest / push
- Publisher analytics
