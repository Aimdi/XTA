# Stocks: posts from accounts you follow first

The owner asked: when a ticker is picked on Stocks, the posts of people they
follow who post that ticker should come first.

Read-only as before: X searches and the posts already on the device. Nothing
is written to X.

## Where

Every posts list about **one** ticker:

- Stocks, with a ticker picked in the reel (the filtered feed);
- a ticker's page (`$NVDA` opened from a post, a row or the reel);
- a token's page.

The whole-watchlist feed, with no ticker picked, stays as it is; a watchlist
of one ticker is that ticker's feed.

## What the reader sees

- The list opens with **Accounts you follow**: posts by X accounts in the
  reader's subscriptions that name the ticker, newest first, at most 10, from
  the last 7 days. A label row above them says so.
- Then **Everyone else**: the ticker's posts as before, without the posts
  already shown above. A label row starts this part.
- With no such post (or no subscriptions), there are no labels and the list
  is exactly as before.
- A post "names the ticker" when it carries the cashtag (`$NVDA`, read from the
  post's cashtags, not its letters) or, for a token, its contract address. A
  repost does not count: the account did not write it. A reply does.
- Only X accounts count. Saved searches and other networks' follows do not
  post on X.

## Where the posts come from

1. **On the device, at once:** the pages the Following and group feeds have
   cached (they keep 7 days). Rows that mention the ticker are read and the
   posts by followed accounts kept. No request.
2. **From X, a little:** at most 3 searches, each the ticker's search narrowed
   to about 25 followed accounts the way the Following feed packs them
   (`$NVDA (from:a OR from:b …) -filter:retweets`, at most 512 characters).
   Accounts the device already saw post the ticker go into these searches
   first, then the others in subscriptions order. A reader following more
   accounts than three searches hold gets the rest from the device only.
3. The searches start with the ticker's first page and run beside it. The
   first page waits at most 3 seconds longer for them; what arrives later is
   kept and shown the next time the list loads.
4. A ticker's searches are kept for 5 minutes, so picking it again, or
   reopening its page, asks X nothing new. A failed search is left out; the
   list never fails because of it.

## Later pages

Posts already in the top part are left out of the pages that follow.

## Code

- `lib/group/feed_search_query.dart`: a search can narrow another search
  (`within`), and packing counts that search's length.
- `lib/plugins/stocks/stocks_followed_posts.dart`: what names a ticker, which
  posts count, the searches, and the merge of the top part with a page.
- `lib/plugins/stocks/stocks_followed_source.dart`: the device's cached feed
  pages and the X searches, kept 5 minutes.
- `lib/plugins/stocks/stocks_followed_section.dart`: the two label rows, and a
  Store with where each part starts.
- `stocks_posts_feed.dart`, `stocks_screen.dart`, `crypto_asset_screen.dart`,
  `lib/tweet/ticker_screen.dart`: use it for one ticker.

## Tests

- What names a ticker: cashtags by entity (not `$NVDAX`), a contract address
  in either case for EVM, a repost left out, a reply kept.
- The searches: packed within 512 characters, at most 3, known posters first.
- The merge: newest first, at most 10, 7 days, no post twice, later pages
  without the top part's posts, nothing changes without followed posts.
- The source: device first, searches kept 5 minutes, a failed search left out,
  the first page not held past its wait.
- Widgets: the two labels, in the right places, read as headings; none when
  nobody followed posted.
