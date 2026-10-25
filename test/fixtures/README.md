# API response fixtures

Captured live X GraphQL JSON for parser characterization tests. Prefer real
responses over hand-written stubs.

## Present

| Path | Source | Used by |
|---|---|---|
| `UserByScreenName/ok.json` | Guest `UserByScreenName` for `@X` | `UserWithExtra.fromNonLegacyJson` |
| `TweetDetail/tweet_result.json` | Tweet node from guest `UserTweets` | `TweetWithCard.fromGraphqlJson` |
| `UserTweets/add_entries.json` | Slim `TimelineAddEntries` from guest `UserTweets` | `Twitter.createTweetChains` |
| `Retweeters/ok.json` | Hand-shaped `Retweeters` timeline (`user_results` + cursor) | `TimelineParser.retweetersInstructions` / `parseUsersTimeline` |
| `Retweeters/module.json` | Hand-shaped Retweeters `TimelineTimelineModule` + `userResults` | `TimelineParser.parseUsersTimeline` |
| `UnifiedCard/image_carousel_website.json` | Card of a recorded `TweetResultByRestId` (QuaX fixture, MIT), trimmed to two pictures | `CarouselCardData`, `TweetCard` |
| `UnifiedCard/grok_share.json` | Card of a recorded `TweetResultByRestId` (QuaX fixture, MIT), conversation text shortened | `GrokShareCardData`, `TweetCard` |
| `TweetDetail/image_poll.json` | Focal post of a `TweetDetail` for x.com/quax_tests/status/2109301223363064091, recorded by QuaX: a `…:poll_choice_images` card, three options with an image each; Grok and edit fields and the image colour palettes dropped | `TweetPoll`, `TweetCard` (`poll_test.dart`) |
| `TweetDetail/subscriber_preview.json` | `TweetDetail` body for x.com/Osemka8/status/2105589900078641579, recorded by QuaX: a `TweetPreviewDisplay` focal post and an `ExclusiveTweet` reply | `TimelineParser.createTweetChains` (`subscriber_preview_test.dart`) |
| `UserTweets/subscriber_previews.json` | x.com/Osemka8 posts tab recorded by QuaX (`UserOriginalsTimeline`), slimmed to a post, a `profile-originals-conversation-` thread of previews, a preview and the bottom cursor | `TimelineParser.createUnconversationedChains` |
| `UserByScreenName/modern.json` | `UserByScreenName` (`KybxDj9RrADIITXlGG8kpw`) for the QuaX test account `@quax_tests`, recorded by QuaX — no `legacy` left | `UserWithExtra.fromNonLegacyJson` / `pinnedTweetIdsOf` |
| `UserOriginalsTimeline/page.json` | `UserOriginalsTimeline` (`qtvmQffnepvr0oPe4A8MqQ`) for `@quax_tests_2`, recorded by QuaX, slimmed to two posts + cursors | `TimelineParser.createUnconversationedChains` |
| `Community/community.json`, `timeline.json`, `members.json`, `search.json` | `CommunityQuery`, `CommunityTweetsTimeline`, `membersSliceTimeline_Query` and `CommunitiesSearchQuery` for x.com/i/communities/1472249315724771329 ("Flutter") and a search for `flutter`, recorded by QuaX on 2026-10-24 (MIT); the timeline slimmed to three posts and its cursors, members and search to three entries | `Community`, `parseCommunityMembers`, `parseCommunitySearch` (`community_parse_test.dart`) |
| `Pixivision/article_en.html`, `Pixivision/article_zh.html` | Hand-shaped pixivision.net article pages in the English and Chinese layouts | `parsePixivisionArticle` |
| `Reddit/listing_pics.html`, `listing_aww.html`, `listing_flutterdev_top.html`, `listing_android.html` | www.reddit.com's community feed piece (`/svc/shreddit/community-more-posts/…`) for r/pics, r/aww, r/FlutterDev (top, week) and r/Android on 2026-10-20, trimmed to a picture, a gallery, crossposts, a video, self, text and link posts | `readWwwListing` (`reddit_www_test.dart`) |
| `Reddit/profile_posts.html` | The profile feed piece for u/spez, trimmed to three posts | `readWwwListing` |
| `Reddit/comments.html` | The comments piece of an r/FlutterDev thread, trimmed to two top-level comments with a reply, a held-back reply and the top-level "more" | `readWwwThread` |
| `Reddit/search_posts.html`, `search_communities.html`, `search_people.html` | www.reddit.com's search piece for posts, communities and people, three results each | `readWwwSearch*` |
| `Reddit/browser_check.html` | The script page www.reddit.com puts in front of a full page | `redditWwwPageKind` |
| `Reddit/comments_big.html` | The comments piece of an r/AskReddit thread with 5,277 comments on 2026-10-22, trimmed to two top-level comments, two of those Reddit renders inside its lazy `<template>`, and the top-level "more comments" partial with its cursor | `readWwwThread`, `fetchMoreComments` |
| `Reddit/embed_self.html`, `embed_long.html`, `embed_image.html`, `embed_gallery.html`, `embed_video.html`, `embed_link.html`, `embed_crosspost.html`, `embed_gone.html` | embed.reddit.com's card for a self post, u/spez's long profile post, a picture, a 9-picture gallery, a video, a link and a crosspost on 2026-10-21/22, and for a post that does not exist | `readWwwEmbed`, `fetchPost` |

The Reddit pages were fetched through Google's translation proxy, since Reddit
blocks datacenter addresses; the proxy's link rewriting was undone, so they
read as www.reddit.com serves them.

Guest `TweetDetail` returned 404; tweet shapes were taken from `UserTweets`
instead. No auth tokens are stored in these files.

## Two test layers

`client_parser_test.dart` asserts the parsers read **today's** shapes — the
fixtures as captured.

`parser_resilience_test.dart` asserts they survive **tomorrow's**. It replays
each fixture with one field removed or nulled at a time, which approximates the
renames and drops X actually ships far better than a hand-written stub can.
Per `.claude/skills/parse-api`, a missing field must yield null or a documented
default; a throw in a parser empties a whole timeline.

That layer found three live crashes when it was added:

- `UserWithExtra.fromNonLegacyJson` threw on a response without `legacy` —
  the migration X is currently rolling out — and `_getProfile` calls it
  unguarded, so every profile view would have broken at once.
- `createTweetChains` threw on an entry whose `tweet_results` or `content` had
  moved, losing the entire page rather than the one entry.

## Rules

- Redact auth tokens and personal secrets before committing.
- When a parser change breaks a fixture test, treat it as a compatibility
  signal: either X changed shape, or the parser regressed.
- The fixtures predate the `legacy` → `core` / `avatar` migration. Tests that
  need the new shape reconstruct it from the captured one rather than asserting
  against fields the capture never had.
- Optional next captures: authenticated `TweetDetail`, `SearchTimeline`,
  `HomeTimeline`, plus unavailable/tombstone shapes.
