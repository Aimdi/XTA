# Group discovery and deliberate AI sorting

Base: Aimdi 129. Preserve the read-only APIs, existing database schema and dependency pins.

- Add Discover alongside Recent, Popular and Custom in group feeds. Discover uses real reposted/quoted authors from that group's members and excludes subscriptions already followed. Keep the normal feed mounted while Discover is selected.
- Candidate collection supports X, Bluesky and Mastodon reposts, plus related Pixiv artwork with native follow verification; failures in one network must not suppress results from the others. Requests are bounded and use existing clients/cache. Show source and the supporting post; users can open the actual profile and use its existing local follow/group controls. Disabled plugins cannot fetch. Mastodon discovery owns its cache so it cannot replace the shared timeline with a group subset.
- Add a sparkle button when AI is configured on group pages and group board/list items. The explicit action opens Discover and ranks only verified candidate ids using the configured API. AI is optional; empty, malformed or failed replies preserve deterministic results and explain the fallback. Do not invent handles or auto-follow anyone.
- Ungrouped sorting initially generates its local heuristic plan. Show an explicit AI sorting button only when configured; opening the screen alone must not transmit subscriptions. Preserve review-before-apply and move application state into a Store.
- Translate new UI copy in every supported ARB, regenerate via intl_utils where available. Test candidate filtering/deduplication, AI-id validation and failure fallback, and explicit sorter AI gating.

## Continuation review

Discovery requests must retire when a newer load starts or the pane closes. A
retired source response must not call AI or replace the latest results/loading
state. Accounts followed while discovery is loading must stay excluded when its
results arrive. Verify these cases with controlled overlapping futures.
