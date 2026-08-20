# Hacker News plugin

A guest Hacker News reader inspired by
[Harmonic-HN](https://github.com/SimonHalvdansson/Harmonic-HN): the same
feeds, threads, search, and profiles, without login, voting, commenting, or
submit. XTA does not post.

## Surface

- Home chrome tabs: Top, New, Best, Ask, Show, Jobs, Saved, Following
- Search via Algolia (`hn.algolia.com`), also from Discover when the plugin
  is on
- Story screen: title, domain, points, author, time, open-article, comment
  tree with collapse
- User screen: karma, about, public submissions; follow stays on this device
- Hearts and bookmarks are local preference lists. Nothing is written to HN
- Deep links: `news.ycombinator.com/item?id=` and `/user?id=`

## Sources

- Algolia for Top / New / Ask / Show / Jobs, search, comment trees, and a
  followed user's submissions
- Firebase (`hacker-news.firebaseio.com`) for Best (id list + items) and
  user profiles

No `lib/database` tables. Follows, likes, and saves are prefs JSON.

## Non-goals

- HN accounts, votes, comments, favorites-on-HN, or submit
- Reader-mode article extraction (open the link instead)
