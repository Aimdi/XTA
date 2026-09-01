# Reddit plugin — discovery polish

Read-only. Local follow + local upvote only. No Reddit write APIs /
scopes beyond `read,identity`.

## Goal

Make the Reddit tab feel closer to Reddit’s own app for **discovery** and
**content gates**, without inbox/DMs/posting.

## This pass

1. **Feed modes** — Following · Popular · All (tabs or segmented control).
   Popular/All use `r/popular` / `r/all` listings with pagination.
2. **Time filters** — for Top / Controversial: hour · day · week · month ·
   year · all (`t=` query). Stored in prefs; shown in sort UI.
3. **Spoilers** — parse spoiler flag; blur/gate media and mark text like NSFW.
4. **NSFW preference** — hide · tap-to-show · always show (settings).
5. **Local saves** — bookmark posts on-device (prefs JSON snapshots); Saved
   entry from post sheet / thread.

## Out of scope

- Reddit-side vote / save / subscribe / comment
- Inbox / chat
- Comment `more` expansion (follow-up)
- Multireddit sync from Reddit account
