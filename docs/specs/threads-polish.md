# Threads plugin — less barebones

Read-only. No Meta write APIs. Builds on `docs/specs/threads-direct.md`
and consolidates open work: in-app threads (#85), profile posts (#87),
repost unwrap (#88).

## This pass

1. **Merge navigation depth** — in-app thread, profile posts, reposts.
2. **Home | Liked tabs** — Liked is a local library of hearted posts
   (snapshots in prefs; ids still in `threads_local_like`).
3. **Enrich on follow** — guest/profile lookup fills name + avatar.
4. **Following strip** — real avatars when known; long-press unfollow.
5. **Empty states** — CTA to add / look up an account.

## Later polish

Card chrome closer to the official app (repost line, always-on engagement,
thread rails, linkified captions, verified badges, image viewer, strip add
chip) is in `docs/specs/threads-app-like.md`.

## Out of scope

- Compose / reply / like-on-Meta / follow-on-Meta
- Full video playback
- Personalized For You without a Bearer
