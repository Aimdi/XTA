# Danbooru search that works

The owner reported that searching Danbooru "doesn't really work" once tags
appear. Live checks from CI on 2026-10-29 (danbooru.donmai.us, no account)
found three causes. This spec fixes them. Nothing is posted or written to
any site.

## Before

- **Suggestions match only the start of a tag name.** They come from
  `/tags.json?search[name_matches]=<typed>*`. Typing `miku` offers
  `miku_day` and `mikuma_(kancolle)`, never `hatsune_miku` (148,527 posts).
  Typing `frieren` misses `sousou_no_frieren`, an alias such as `kancolle`
  finds nothing useful, and a typo or a Japanese name finds nothing at all.
  Danbooru's own search box asks `/autocomplete.json`, which finds all of
  these.
- **A third tag breaks the search.** Without an account, and with a Member
  account, Danbooru takes 2 tags per search. A third one gets HTTP 422 with
  `{"error":"PostQuery::TagLimitError","message":"You cannot search for more
  than 2 tags at a time."}`, and XTA shows "Too many tags for one search on
  this host — remove some" in place of any post. It takes only one more tap
  to get there: a third suggestion, a related tag above the results, "Add
  to current search" from a post, "Highest score" with two tags, or a saved
  search of three tags in Following, which then says some queries failed.
- **Sorting a big tag by score times out.** `blue_eyes order:score` (with or
  without a rating term), `order:score` alone and `order:favcount` all come
  back after about 4 s as HTTP 500 `{"error":"ActiveRecord::QueryCanceled",
  "message":"The database timed out running your query."}`. XTA says "The
  host returned something unexpected". `frieren order:score` (16,526 posts)
  answers in 0.3 s.

What counts towards the limit, from the same checks: plain tags, `-tag`,
`~tag` and `order:` count, one each. `rating:` (also `rating:g,s` and three
`-rating:` terms), `status:`, `-status:`, `limit:`, `is:`, `parent:`,
`filetype:`, `score:` and `age:` do not. The rating terms XTA adds for the
rating cap never count.

## After

### Suggestions

- On a Danbooru host, suggestions come from
  `/autocomplete.json?search[query]=<typed>&search[type]=tag_query&limit=12`,
  as on the site: tags whose name or any word of it starts with what is
  typed, tags reached through an alias, another name or a corrected typo,
  most used first.
- A tag reached through another name shows that name first, dimmed, then an
  arrow and the tag: `kancolle → kantai_collection`,
  `初音ミク → hatsune_miku`. Picking it adds the tag itself.
- The part of the name that matches what is typed is bold, wherever in the
  name the matching word starts (`hatsune_` **`miku`**).
- When the autocomplete request fails (a fork without it, an unexpected
  answer), the prefix lookup is asked instead, as before. A network failure
  shows no suggestions, as before.
- Other engines are unchanged.

### Searches with more tags than the host takes

- A search runs whatever its number of tags. The first request goes out as
  typed. When the host answers that it takes only N tags, XTA keeps N for
  that site and account for the rest of the session, and from then on:
  - the host gets every metatag, then as many tags as still fit, the least
    used first, so that its pages hold as many matching posts as possible;
    how often each tag is used comes from one `tags.json` lookup per search;
  - the device keeps, from each page, only the posts that also have the
    other tags, lack the excluded ones, and have one of an either group;
  - an either group (`~a ~b`) stays together, on the host or on the device;
    excluded tags go to the host last.
- The split of a search is decided once and kept for all its pages, so they
  follow on.
- Pages are counted as the host sends them: a page the device emptied does
  not end the feed, and the feed skips such pages as it does for the rating
  cap.
- Only when the metatags that count are more than the host takes (for
  example `order:score`, `fav:` and `ordfav:` together on a guest) does the
  tag-limit message stay.
- This holds wherever posts are searched: the search screen, related and
  added tags, tag pages, saved searches in Following.
- Known limit: a tag left to the device is compared by name, so an alias
  typed by hand and left to the device matches nothing. Tags picked from
  suggestions are real names, and a tag that has no posts (an alias, a
  typo) goes to the host first.

### A search the host gave up on

- An HTTP 500 that says the database timed out reads: "The host took too
  long on this search — add a tag or sort it another way". Retry stays.

## Strings

`plugin_booru_error_timed_out`, in every language.

## Code

- `lib/plugins/booru/booru_client.dart`: the autocomplete request with its
  fallback; the tag-limit answer read and kept per site; the split asked
  for and kept per search; posts filtered on the device; the timeout kind.
- `lib/plugins/booru/booru_tag_limit.dart` (new): pure parts: which tokens
  count on Danbooru, the split, the limit read from the host's answer.
- `lib/plugins/booru/booru_endpoints.dart`: the autocomplete request, and
  the tag list lookup shared by a post's tags and the split.
- `lib/plugins/booru/booru_query.dart`: where a typed word starts in a name,
  for the bold match.
- `lib/plugins/booru/booru_parse.dart`: autocomplete rows; the blacklist's
  matcher shared as `booruPostMatches` for the device's part of a search.
- `lib/plugins/booru/booru_models.dart`: `BooruTagSuggestion.alias`.
- `lib/plugins/booru/booru_search_landing.dart`: the alias and the bold
  match in a suggestion row.
- `lib/plugins/booru/booru_errors.dart`: the timeout message.

## Tests

- Autocomplete rows as Danbooru sends them (word match, alias, typo, other
  name), parsed into suggestions with their alias; the fallback to the
  prefix lookup when autocomplete fails.
- The split: counted and free tokens, least used first, either groups kept
  together, excluded tags last, too many metatags, unknown counts keep the
  typed order.
- The client: a 3-tag search on a guest gets 422, then sends 2 tags and
  filters the third on the device; the next search goes straight to the
  split; pages keep the same split; a 500 timeout has its own kind.
- The suggestion row shows the alias and the arrow, and picking it adds
  the tag.
- Live (`test/live/sources_smoke_test.dart`): `miku` suggests
  `hatsune_miku`, and a 3-tag search shows posts.
