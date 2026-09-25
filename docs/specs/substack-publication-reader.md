# Substack publication reading

The publication archive already has posts, follow/group actions and author
recommendations. Its requests currently replace the whole page during loading,
lose visible content on errors, search every keystroke without paging, and keep
feature state in widgets. Publication headers also squeeze actions beside the
logo and truncate descriptions permanently.

Implement a publication Store with independent archive and search pages,
debounced remote search, explicit pagination, duplicate-request and stale-result
guards, retained pages on failed refresh/load-more, and retries for the failed
operation. Stop paging when the server repeats a page. Metadata lookup must not
block reading. Preserve archive pages when search is cleared.
Preserve the local publication identity during metadata enrichment so existing
pins and group membership remain attached to a custom-domain subscription.

Add clearly scoped loaded-post filters (all, unread, free, podcasts and videos)
and stable newest/oldest/popular sorting. Keep paging available even when the
current filter matches nothing. Make descriptions expandable and selectable,
move header actions into a full-width wrapping row, and expose local publication
pins. Keep the header and content in one scroll view.

Move recommendation request state into a Store, observe local follows directly,
and provide responsive rows with accessible follow/group controls and retry.

Use existing localized labels where the meaning matches. Add one publication
search hint through the coordinated translation pass. No posting, credentials,
dependencies, database schema or frozen-client changes. Validate race/error and
pagination behavior with fake public clients plus narrow/RTL widget layouts.
