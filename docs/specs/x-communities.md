# X communities (read-only)

## Problem
X communities are where many topics now gather, and XTA cannot show one. A
community link (`x.com/i/communities/<id>`) opens the "unable to open link"
dialog, a post written in a community does not say so, and search cannot find
communities. QuaX added community browsing in v4.20.0 (03d01f9c).

## Scope
Read-only, like the rest of XTA towards X: browse, never join, leave, post,
invite or report.

- **Community page**: banner, name, topic, member count with the member
  faces X sends, description, and two tabs: **Top** (X's ranked community
  timeline, paged) and **About** (description, rules, creation date). The
  member count opens the **members** list (paged), whose rows open profiles.
- **Search**: a **Communities** tab next to the existing ones, paged, each
  row opening the community page.
- **Posts**: a post written in a community carries a small community chip
  that opens it. The id comes from `community_results.result.id_str`; the
  name is not in the post, so the chip says "Community" and the page loads
  the rest.
- **Links**: `x.com/i/communities/<id>` and `/about` open the page (the
  About tab for the latter), from deep links, shares and links in posts.

Not in scope: Latest/Media community tabs, community notes, joining, the
"communities" home tab of the web client.

## X operations
Four GraphQL operations, registered in `lib/client/endpoints.dart` so a
rotated id can be repaired through `endpoints.json`. Query ids are the ones
x.com's web client sent on 2026-10-24 (QuaX's recordings), checked against X
with the made-up-id probe before shipping.

| Key | Operation | Variables |
|---|---|---|
| `CommunityQuery` | `CommunityQuery` | `communityId` |
| `CommunityTweetsTimeline` | `CommunityTweetsTimeline` | `communityId`, `count`, `cursor`, `displayLocation: Community`, `rankingMode: Relevance`, `withCommunity: true` |
| `membersSliceTimeline_Query` | `membersSliceTimeline_Query` | `communityId`, `cursor` |
| `CommunitiesSearchQuery` | `CommunitiesSearchQuery` | `query`, `cursor` |

Responses are parsed with the `Json` extension type (no throw on a missing
field); a missing community is a not-found error, an empty slice an empty
page. Timelines reuse `TimelineParser`. Adding operations to the frozen
`lib/client` was approved by the owner for this feature.

## Design
- `lib/community/`: `Community` model, a `Store` for the page, the page and
  members screens, a search results list and a community row. Pages use
  `PaginatedTweetList` / XTA's cursor paging, `TweetContextScope`, and XTA's
  existing error and empty states; no setState or ChangeNotifier.
- Route `routeCommunity` with `CommunityScreenArguments(id, about:)`;
  `CommunityUriInfo` in `utils/urls.dart`, handled in `main.dart` and the
  in-app link router.
- Strings through ARB in every locale.

## Validation
- Parsers over trimmed recordings: community header fields, rules, member
  faces, timeline chains and cursor, members page and cursor, search page and
  cursor, a missing community, an empty slice.
- Link parsing for `/i/communities/<id>` and `/about`, and the post chip id.
- Widget tests: the page shows its header and Top posts and pages on; About
  shows the rules; members and search rows open their targets.
- Analyzer clean; focused suites pass.
