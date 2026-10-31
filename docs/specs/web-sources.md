# Web sources: sites added from a list, without a release

The owner asked for downloadable sources "like Grayjay", for new, simple
sites, drawn with XTA's own screens, while the built-in networks stay as
they are. Flutter cannot load downloaded code (see the discussion of
2026-10-29), so a source here is data: a JSON file that says where a site's
lists are and where each post's title, link, picture and date sit in them.
XTA fetches the pages and draws the posts. Nothing is posted anywhere.

## Before

- Every network is compiled in. A new site needs a release, and so does
  every fix to a site's addresses or page layout.
- The RSS plugin reads any RSS or Atom feed, but a site without a feed, or
  with a JSON API, cannot be read at all.

## After

### For the reader

- A new plugin in the store, **Web sources**. Its tab lists the sources
  installed and offers **Add source**.
- **Add source** shows the sources this repository publishes
  (`sources.json`), each with its name, what it is, and the sites it
  contacts. **Add from link** takes the address of any source file.
- Before a source is added, a dialog names it, says where it came from and
  lists every site it will contact. Nothing is fetched from those sites
  before the reader agrees.
- A source opens on its lists (for example Hottest, Newest), one tab each,
  and its search when it has one. Posts are drawn as XTA draws a link, an
  article or a post, or as a picture grid, as the source asks:
  - a tap opens the post: an article in XTA's reader when the source says
    where its text is, otherwise the link, as any link in XTA opens;
  - a long press offers what every plugin post offers: save to a folder,
    share, open in the browser;
  - a picture opens in XTA's picture viewer.
- **Follow** on a list adds it to Subscriptions. Followed lists can join
  the Following timeline (a switch, off by default, as for RSS) and groups,
  like any other plugin's follows.
- A source can be removed from its screen. Its follows go with it.
- Sources are kept up to date: at most once a day, when XTA starts with
  Web sources installed, it reads each installed source's file again. A newer version that contacts only sites already agreed to
  replaces the old one; one that asks for a new site waits until the
  reader agrees, and the source shows that an update is waiting.

### What a source can and cannot do

- It can only read: every request is a GET, without cookies or the
  reader's accounts.
- It can only contact the sites it lists (`hosts`), over HTTPS. Redirects
  are followed by hand, at most three, and only to listed sites.
- It cannot run code: no JavaScript, no regular expressions. Its rules are
  paths into JSON and CSS selectors into HTML, read by XTA's own code.
- Limits: a source file of at most 64 KB, 12 lists, 8 sites, rules of at
  most 300 characters; a page of at most 4 MB and 100 posts, read off the
  UI thread when long; 20 seconds per request; one request a second per
  source.
- Nothing is fetched from a source's sites, its icon included, until the
  reader agrees to them.
- Links and pictures inside posts may point anywhere, as in any feed
  reader; they are opened or loaded as XTA opens links and loads pictures.

## The format (version 1)

```json
{
  "format": 1,
  "id": "lobsters",
  "version": 1,
  "name": "Lobsters",
  "description": "Computing-focused links and discussion",
  "homepage": "https://lobste.rs",
  "color": "#AC130D",
  "hosts": ["lobste.rs"],
  "layout": "links",
  "type": "json",
  "items": "$",
  "fields": {
    "id": "short_id",
    "title": "title",
    "link": "url",
    "author": "submitter_user",
    "date": "created_at",
    "score": "score",
    "comments": "comment_count",
    "discussion": "comments_url",
    "tags": "tags[*]"
  },
  "feeds": [
    { "id": "hottest", "name": "Hottest", "url": "https://lobste.rs/page/{page}.json" },
    { "id": "newest", "name": "Newest", "url": "https://lobste.rs/newest/page/{page}.json" }
  ]
}
```

- `id`: 2 to 40 of `a-z 0-9 -`. `version`: a whole number; an update must
  raise it. `name`: up to 40 characters; `description`: up to 200.
- `hosts`: the sites it contacts, as `example.com` (that host only) or
  `*.example.com` (its subdomains too). `homepage` and any `icon` must be
  on them.
- `layout`: `links` (title, score, comments), `articles` (title, excerpt,
  picture, opens in the reader), `posts` (author, text, pictures) or
  `gallery` (a grid of pictures).
- `feeds`: 1 to 12 lists, each with an `id`, a `name` (its tab) and a
  `url`. `{page}` counts pages from `first_page` (default 1), until a page
  brings no new posts. A list whose next page is named in the page uses
  `next` instead: a JSON path to a cursor (put in `{cursor}`, empty for the
  first page) or to the next page's address, or a CSS selector to the next
  page's link.
- `type`, `items` and `fields` can be set once at the top and for any list.
  `type` is `json`, `html` or `rss`; RSS and Atom pages are read by the RSS
  plugin's parser and need no `items` or `fields`. `items` is a JSON path
  to the list of posts, or a CSS selector matching each post.
- `search` (optional): a list whose `url` contains `{query}`.
- `article` (optional): where an item's full text is, for the reader:
  `{"url": "https://dev.to/api/articles/{id}", "html": "body_html"}`. Its
  `url` may use the item's fields, and must be on a listed site.

### Reading fields

- **JSON**: a path of keys and indexes from the item, `user.name`,
  `images[0].url`; `[*]` collects every element, for `images` and `tags`;
  `$` is the page itself.
- **HTML**: a CSS selector inside the item, for its text, or
  `selector@attribute` for an attribute (`a.title@href`), `@attribute` for
  the item's own attribute, and `selector@html` for its markup (article
  text only).
- A field with `{…}` is a template filled with other paths:
  `"link": "https://lemmy.world/post/{post.id}"`.
- Fields: `id`, `title`, `link`, `text`, `html`, `author`, `avatar`,
  `date`, `image`, `images`, `score`, `comments`, `discussion`, `tags`.
  Only `title` or `text`, and `link` or `id`, are needed. Relative links
  are resolved against the page. Dates may be ISO 8601, RSS dates, or Unix
  seconds or milliseconds.

## The published list

`sources.json`, next to `plugins.json`, read from the default branch:

```json
{
  "format": 1,
  "sources": [
    { "id": "lobsters", "name": "Lobsters", "description": "…", "url": "sources/lobsters.json" }
  ]
}
```

A `url` may be relative to the list. The repository's own sources live in
`sources/`. `docs/web-sources.md` explains the format to anyone writing
one.

## Storage

All in preferences, so the settings backup carries it: the installed
sources (their file, where it came from, the sites agreed to, when it was
last checked) and the followed lists. No table is added.

## Code

New, in `lib/plugins/websources/`:

- `web_source_definition.dart` (pure): reads and checks a source file.
- `web_source_paths.dart` (pure): JSON paths, templates, HTML selectors.
- `web_source_items.dart` (pure): a page and its rules to posts.
- `web_source_client.dart`: the only network code: GET, listed sites only,
  limits, redirects by hand, throttle.
- `web_source_catalogue.dart`: the published list.
- `web_sources_store.dart`, `web_source_follows_store.dart`,
  `web_source_feed_store.dart` (`Store`s).
- `web_sources_screen.dart`, `web_source_add_screen.dart`,
  `web_source_screen.dart`, `web_source_item_card.dart`.
- `web_sources_plugin.dart`: the plugin, its follows as subscriptions,
  and their posts in the timeline.

Shared pieces reused, not copied: the card rows (`plugin_card_row.dart`),
plugin media and the picture viewer (`plugin_post_media.dart`), the post
actions sheet (`plugin_link_post.dart`), the RSS reader
(`RssReaderScreen`), the RSS parser for `rss` pages, the gallery column
count, the feed skeleton.

The repository: `sources.json`, `sources/*.json`, `docs/web-sources.md`.
A daily live check (`test/live/web_sources_smoke_test.dart`, its own row)
loads every published source's lists and fails, opening an issue, when one
returns no posts.

## Tests

- A source file: a valid one is read; each limit and rule above refuses a
  bad one with a reason (unlisted host, HTTP, too many lists, a bad id, no
  title field…).
- Paths: keys, indexes, `[*]`, `$`, templates, missing values; HTML
  selectors, attributes, the item's own attribute, relative links; dates in
  each form.
- Pages to posts, from saved JSON, HTML and RSS pages, for each layout.
- The client refuses an unlisted host, a redirect to one, HTTP, a page too
  large, and anything but GET; it waits a second between requests.
- Updates: a higher version on agreed sites replaces the old; one asking
  for a new site waits; a lower one is ignored.
- Follows become subscriptions and timeline posts; removing a source
  removes them.
- Screens: the list, the add dialog naming the sites, a source's tabs and
  its posts in each layout, at large text sizes.
- The published files: every source in `sources.json` is a valid source.
