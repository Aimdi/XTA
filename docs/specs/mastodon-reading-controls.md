# Mastodon reader upgrade: timeline controls and shared data

## Goal

Make busy public Mastodon feeds easier to read with local controls inspired by
Phanpy Catch-up and Tusky timeline filters. Keep XTA's public, read-only model.
Existing reading snapshots, bookmarks and source configuration remain compatible.

## Timeline implementation

- Add a compact filter/sort toolbar to Explore, Local, Federated and Following.
- Filter loaded posts by text, media/links, boosts and replies; apply controls
  locally and explicitly explain their loaded-post scope.
- Offer server order, newest and oldest order with stable tie handling.
- Persist filter/sort choices per timeline on the device, not search text.
- Keep canonical loaded posts in reading snapshots so filtering never deletes
  hidden posts from offline recovery. Use clear empty-filter recovery.
- Provide explicit load-more and retry controls with real loading/error state.

## Additive model corrections

- Preserve the outer timeline ID separately from original post ID when a boost
  is unwrapped, and use it for paging. Preserve it in bounded snapshots.
- Retain poll voter totals, closing date and unavailable-result state.
- Preserve content warnings on quoted statuses through snapshots and navigation.
- Retain profile banners, join dates and verified HTTP(S) metadata links.
- Return raw initial profile posts separately from pinned display posts.

## References and decisions

- https://phanpy.app/ : Catch-up sorting/filtering suggests local reading controls.
- https://tusky.app/faq/ : independent boost/reply visibility controls.
- https://docs.joinmastodon.org/entities/Status/ : boost envelope and status IDs.
- https://docs.joinmastodon.org/entities/Poll/ : nullable option vote counts,
  voters_count for multiple-choice percentages, expires_at.
- https://docs.joinmastodon.org/entities/Account/ : header, created_at, fields.
- https://docs.joinmastodon.org/methods/search/ : guest full-text search limitations
  make clearly labelled local text filtering more useful than server operators.

No new dependencies, schema changes, OAuth or remote writes. Do not change
lib/client or lib/database. Validate pure filtering, malformed parsing, archive
compatibility, widget controls, pagination and stale async responses; run the
existing Mastodon regression suite and shared full-suite gate.
