# Bluesky reading improvements

Base: aimdi128. Scope: Bluesky profile and public conversation presentation.

## Intended behavior

- Preserve profile identity, existing icons, local follow/group actions and public navigation.
- Media uses a responsive thumbnail grid with video and collection indicators. Labeled sensitive media stays hidden and never requests a thumbnail before the user opens and reveals the post.
- Posts, Replies, Media and local Likes keep separate data, paging cursors, errors and reading positions during tab switches. Refresh updates the selected tab. In-flight requests remain bound to their originating tab.
- Rename the local-like tab label from Saved to Likes; the existing local-only store remains authoritative.
- Threads emphasize the opened post, optionally reveal ancestor context, and connect replies using parent URIs. Branches collapse without hiding siblings. Orphans, cycles and duplicates remain safe.
- Keep primary actions and content accessible at narrow widths, large text and RTL. Feature state uses flutter_triple Store.

## Boundaries

No X client/database edits, API request changes, remote write actions, dependency or SDK changes. Preserve existing post parsing and add only optional reply-parent metadata needed to reconstruct threads. Reuse localized labels; the integration owner handles any ARB additions.

## Verification

Meaningful tests cover parent metadata round trips, reply ordering/collapse and malformed topology, tab switch during paging, media filtering and sensitive-thumbnail exclusion, selected-post emphasis and local Likes labeling. Flutter analysis/tests/render checks run through integration CI using pinned Flutter 3.44.4; no local Flutter SDK is available.
