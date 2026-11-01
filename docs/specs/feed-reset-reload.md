# A reset feed loads again

A feed, search tab or media grid is emptied and asked for its first page
again whenever what it shows changes: a new search, a new sort on Quotes, a
new set of members or rules on Following and groups, a pull to refresh on a
media grid. On HEAD the emptying happened and the asking did not: the list
stayed on its placeholders until the screen was left and opened again.

## Found

- The package's own list asks for the first page whenever it finds its
  controller empty. XTA's feeds draw their own placeholders instead, and ask
  for the first page themselves, once (`PaginatedTweetList`,
  `MediaGrid`). After a reset nothing asked again.
- The media grid's pull to refresh reset its controller and nothing else.
- A fix for the feed list existed on the aimdi150–164 line and went with it
  when that line was dropped (b5c27b96). It restarted on any empty state,
  which a read cancelled because the feed was hidden leaves too, so it would
  start a hidden feed's read again straight away.

## After

- The paging controller counts its resets (`pagingResets`). A cancel is not
  a reset and does not count.
- The feed list and the media grid start their first page again when the
  count moves: a new search, sort, set of members or pull to refresh loads.
- A first page that was only cancelled stays as it is: the hidden feed
  starts it itself when it is seen again, as before.
- A group feed whose members change while it is hidden (someone followed
  from a profile opened over it) waits until it is seen to reset, where it
  reset at once and repaired on return.
- A timestamp a list hands to another post as it scrolls says that post's
  time (the Mastodon, Bluesky and Threads cards now carry one, and nothing
  keyed them).

## Code

- `lib/utils/paging.dart`: the reset count.
- `lib/tweet/paginated_tweet_list.dart`, `lib/profile/media_grid/media_grid.dart`:
  start the first page again after a reset.
- `lib/group/_feed.dart`: a hidden feed's member refresh waits.
- `lib/ui/dates.dart`: the reused timestamp.

## Tests

- `test/feed_reset_reload_test.dart`: a reset feed loads its first page
  again; a cancelled first page does not start again; a media grid pulled to
  refresh loads again.
- `test/dates_test.dart`: a timestamp handed to another post says its time.
