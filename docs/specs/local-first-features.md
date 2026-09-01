# Local-first features (Bluesky / Mastodon / Misskey / Threads inspired)

Read-only, local-first. No writes to X or any remote service.

## Schema (migration 49)

| Change | Purpose |
|---|---|
| `saved_tweet.note TEXT` | Clip note on a saved post |
| `profile_note (id, note, updated_at)` | Private per-account notes |
| `antenna (id, name, include_terms, exclude_terms, scope, created_at)` | Keyword listener feeds |
| `subscription.max_posts_per_load INTEGER` | Quiet loud accounts (NULL/0 = uncapped) |
| `muted_keywords` JSON upgrade | `{term, until?, action}` — keep legacy CSV readable |

`muted_keywords` storage stays a TEXT column on `subscription_group`. New writers store a JSON array of objects; readers accept both legacy comma-separated strings and JSON.

## Prefs

| Key | Meaning |
|---|---|
| `optionCalmMode` | Hide engagement counts (likes/reposts/views/replies) |
| `optionFeedLanguages` | CSV of BCP-47 prefixes the reader wants |
| `optionFeedLanguageAction` | `off` / `hide` / `fold` |
| `optionDeckGroupIds` | Ordered list of group ids for tablet deck columns |

## Features

1. **Subscription packs** — export one group as `{format:"xta-pack",v:1,name,members:[{type,id,screen_name?}]}`; import merges as a new group. QR/share file. No credentials.
2. **Alt text** — plumb `ext_alt_text` from media JSON onto `TweetWithCard`; ALT badge + long-press dialog.
3. **Quiet accounts** — after feed merge, keep at most N chains per author id for the load.
4. **Expiring filters** — `until` ISO on muted keyword; expired rows skipped.
5. **Fold filters** — action `hide` (default) or `fold`; folded chains render a one-line reason, tap expands.
6. **Profile notes** — local table; shown on profile; in backup.
7. **Language filter** — global pref using `TweetWithCard.lang`.
8. **Antennas** — first-class search-like feeds from include/exclude + scope (`search` \| `following`).
9. **Clips** — note on `saved_tweet`; searchable in Saved.
10. **Deck** — landscape/tablet multi-column of pinned groups.
11. **Calm mode** — pref; footer hides count labels (stronger than zen for engagement vanity).
12. **Topic follow** — hashtag long-press / action → `SearchSubscription` for `#tag`.
13. **Local notes** — reader-authored posts that never go to X. SQLite `local_post` (migration 59); Saved tab + FAB; included in backup / WebDAV (Nextcloud).

## Backup

Bump awareness only if needed; include `profileNotes`, `antennas`, `localPosts`, and `saved_tweet.note` via existing tweet rows. Packs are a separate share format, not the full backup.

## Non-goals

- No compose / like-on-X / remote mute. Local notes are on-device (and backup) only.
- No rewrite of transport.
- Deck is layout only.
