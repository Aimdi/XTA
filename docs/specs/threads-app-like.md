# Threads plugin — more like the app

Read-only. Builds on `threads-polish.md` / `threads-direct.md`. No Meta
writes (compose, reply, repost-to-Threads, like-on-Meta, follow-on-Meta).

## This pass

1. **Repost chrome** — show “{name} reposted” when `repostedByHandle` is set
   (parser already unwraps `share_info.reposted_post`).
2. **Always-on engagement** — comment / repost / local like icons even when
   Meta counts are missing (RSSHub); counts stay blank when unknown.
3. **Following strip Add** — trailing `+` chip to add an account from Home.
4. **Thread conversation** — AppBar “Thread”, replies section label, reply
   cards indented with the shared thread rail.
5. **Linkified caption** — `@handle` opens a profile; `http(s)` opens outside.
6. **Verified on cards** — plumb `user.is_verified` onto `ThreadsPost`.
7. **Tap images** — open a simple fullscreen pager (video still out of scope).

## Out of scope

- Profile Replies tab (needs a stable guest GraphQL doc_id)
- Quoted-post embeds until a live fixture confirms `quoted_post`
- Full video playback / For You without Bearer
