# Discover reading entry

Persist a bounded list of recent committed searches per network. Show them only
before a query, in one compact horizontal row, with an accessible remove action.
Selecting one fills the search field and submits the same query. Never record
each intermediate keystroke as a search.

Mastodon results render inline in Discover using the full client's result pane
and request store: accounts, posts and hashtags, retry, latest response wins,
and separate result-tab positions. Its empty query offers locally followed
accounts and live trending tags. Profiles and tags open their existing routes
and Back retains the results. Keep X search routing and other plugins intact.

New feature state uses Store. Reuse translated search/history labels. Verify
per-source history limits/removal, actual recent-query submission, query races,
and returning from an inline profile without losing the query.
