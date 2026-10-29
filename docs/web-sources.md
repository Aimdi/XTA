# Writing a web source

A web source is a JSON file that tells XTA where a site's lists of posts
are, and where each post's title, link, picture and date sit in them. XTA
fetches the pages itself and draws the posts with its own screens. A
source is data, not code: it cannot run scripts, post, sign in, or reach a
site it does not list.

Readers add sources in the **Web sources** plugin: **Add source** lists
the ones this repository publishes (`sources.json`), and **Add from link**
takes the address of any source file on HTTPS. Before anything is read, XTA
shows the reader every site the source will contact, and asks.

The design and its reasons are in `docs/specs/web-sources.md`; the
sources this repository publishes are in `sources/`.

## A first source

The smallest useful source reads a JSON API:

```json
{
  "format": 1,
  "id": "lobsters",
  "version": 1,
  "name": "Lobsters",
  "homepage": "https://lobste.rs",
  "hosts": ["lobste.rs"],
  "type": "json",
  "items": "$",
  "fields": { "id": "short_id", "title": "title", "link": "url" },
  "feeds": [
    { "id": "hottest", "name": "Hottest", "url": "https://lobste.rs/page/{page}.json" }
  ]
}
```

`https://lobste.rs/page/1.json` answers with a list of stories. `"items":
"$"` says the page itself is that list, and each field names a key in one
story. `{page}` counts 1, 2, 3… as the reader scrolls. From there, add what
the site offers: an author, a date, a score, a comment count, where the
discussion is, tags (`sources/lobsters.json` has them all).

## The file

| Key | Needed | What it is |
|---|---|---|
| `format` | yes | `1`. A file with a higher format is refused by installs that do not know it, with a message saying XTA needs updating. |
| `id` | yes | 2 to 40 of `a-z`, `0-9` and `-`, starting with a letter or digit. It names the source on every install: keep it for the source's life. |
| `version` | yes | A whole number above 0. Raise it with every change installs should take (see [Updates](#updates)). |
| `name` | yes | Up to 40 characters. |
| `description` | no | Up to 200 characters. |
| `homepage` | yes | The site, on one of `hosts`. **Open in browser** opens it, and a post with neither a link nor a discussion shares it. |
| `color` | no | `#RRGGBB`: the source's mark shows its initial on this colour. |
| `icon` | no | A picture's address, on one of `hosts`, shown in place of the initial. |
| `hosts` | yes | 1 to 8 sites the source may contact: `example.com` (that host only) or `*.example.com` (it and every subdomain), in lower case. |
| `layout` | no | How posts are drawn: `links` (the default), `articles`, `posts` or `gallery`. |
| `type`, `items`, `fields` | see [Lists](#lists) | How pages are read, for every list; a list can set its own. |
| `feeds` | yes | 1 to 12 lists, one tab each. |
| `search` | no | A list whose address holds `{query}`. |
| `article` | no | Where a post's full text is, for XTA's reader. |

The layouts:

- `links`: the link's site, the score, author and age on one line, the
  title, and a comments button when the post has a `discussion` address. A
  tap opens the link.
- `articles`: a title, its excerpt (`text`), a picture and the author. A
  tap opens the full text in XTA's reader when the source has an `article`
  rule, else the link.
- `posts`: the author with their picture, the title when there is one,
  the text, the post's pictures, its score and comment count.
- `gallery`: a grid of pictures; a tap opens the picture viewer. A
  gallery's fields must include `image` or `images`.

In every layout a long press offers what every plugin post offers: save to
a folder, share, open in the browser.

## Lists

`feeds` are the source's tabs; `search` is the list its search box reads.

| Key | Needed | What it is |
|---|---|---|
| `id` | yes (not for `search`) | 1 to 40 of `a-z`, `0-9` and `-`, unique in the file. Readers' follows find the list by it: a follow whose list id is gone brings nothing. |
| `name` | yes (not for `search`) | Up to 40 characters: the tab's title. |
| `url` | yes | The page's address, on one of `hosts`, with `{page}`, `{cursor}` and, for `search` only and there required, `{query}`. Values put in are escaped for an address. |
| `first_page` | no | What `{page}` starts at; 1 unless set (0 for a site that counts from 0). |
| `next` | no | Where a page names the next one (below). |
| `type` | no | `json` (the default), `html` or `rss`. |
| `items` | not for `rss` | Where the posts are in a page: a JSON path to them, or a CSS selector matching each one. |
| `fields` | not for `rss` | Where each field is in one post. A list's fields are added to the file's, replacing those of the same name. |

### Pages

- **JSON**: `items` is a JSON path. When it leads to one list, the list's
  elements are the posts (`"posts"`, `"collection.items"`, or `"$"` when
  the page is the list).
- **HTML**: `items` is a CSS selector, and each element it matches is a
  post; its fields are read inside that element.
- **RSS** and **Atom**: read as the RSS plugin reads a feed, so `items`
  and `fields` are not needed. An RSS list is one page.

### More pages

- With `{page}` in `url` and no `next`, XTA asks for `first_page`, then
  the next number, and so on, until a page brings no post it has not
  already shown.
- With `next`, each page names the one after it. On a JSON page, `next` is
  a path to either the next page's address (a value starting with `http`
  or `/`) or a cursor, which fills `{cursor}` in `url` (empty for the first
  page). On an HTML page, it is a rule to the next page's link, such as
  `.pagination a[href*="after="]@href`. No value, or the same address
  again, ends the list.
- With neither, the list is one page.

## Fields

| Field | What it is |
|---|---|
| `id` | What tells one post from another; the link stands in when there is none. |
| `title` | The post's title. |
| `text` | Its text or excerpt. Markup in it is reduced to plain text. |
| `html` | Its full text as markup: when a post has it, a tap opens it in the reader without another request. |
| `link` | Where a tap goes. |
| `author` | Who posted it. |
| `avatar` | Their picture. |
| `date` | When: ISO 8601, an RSS date, or Unix seconds or milliseconds. Followed posts join the Following timeline only when they have a date. |
| `image` | The post's picture. |
| `images` | Every picture, from a rule that finds several (`[*]`, or a selector matching several). |
| `score` | A count: `12`, `12.0`, or the first number in text such as `1,205 points`. |
| `comments` | A count, read as `score` is. |
| `discussion` | Where its comments are: the comments button, and where a tap goes when there is no link. |
| `tags` | Labels, several. |

Each source needs `title` or `text`, and `link` or `id`. A post in which
neither of a pair is found is skipped; any other field missing from a post
is simply not shown.

`link`, `avatar`, `image`, `images` and `discussion` may be relative: they
are resolved against the page they were read from. Only `http` and `https`
addresses are kept. Those addresses may point anywhere, as links in any
feed reader do; only the source's own requests (lists, search, articles)
are limited to `hosts`.

## Rules

A rule says where a value is, in at most 300 characters.

**JSON paths** read from the post: `title`, `user.name`, `images[0].url`;
`[*]` takes every element of a list (`tags[*]`, `media[*].url`), and `$`
is the page itself. A key cannot hold `.`, `[`, `]`, `{`, `}`, `@` or a
space. Numbers and booleans are read as text; objects and lists give no
value.

**HTML rules** read inside the post's element:

| Rule | Reads |
|---|---|
| `h2 a` | the text of the first element matching the selector |
| `a.title@href` | an attribute of that element |
| `@data-id` | an attribute of the post's own element |
| `div.body@html` | that element's markup (for `html` and articles) |

Selectors are CSS as the `html` package reads them: tags, classes, ids,
attributes (`[href*="after="]`), the combinators (` `, `>`, `+`, `~`),
`:first-child`, `:last-child`, `:only-child`, `:empty` and `:not(…)`. One
it cannot read, such as `:has()`, `:contains()` or `:first-of-type`,
refuses the file when it is added, so a mistake shows at once rather than
as an empty list. Avoid `:nth-child()`: it is accepted, but counts text
between elements and from 0, so it rarely matches what a browser would.

**Templates** put values together: `"https://lemmy.world/post/{post.id}"`
or `"{user.first} {user.last}"`. Each `{…}` holds a rule of the page's
kind. When any one of them finds nothing, the field has no value, so a
link is never built from half its parts.

## Articles

```json
"article": { "url": "https://dev.to/api/articles/{id}", "html": "body_html" }
```

When a post is opened, XTA builds `url` from the post's fields by name
(`{id}`, `{link}`, `{title}`…), reads that page, and opens `html` in its
reader. A `url` of just `{link}` reads the post's own link as it is; other
values are escaped. The address must be on one of `hosts`: one that is not,
or a page that cannot be read, opens the post's link instead.

- `type`: `json` or `html`, the file's `type` unless set.
- `html`: on a JSON page, a path to the markup; on an HTML page, a
  selector whose markup is the text (`article .content`; `@html` is
  implied).

## What a source can and cannot do

- Every request is a GET, with no cookies and none of the reader's
  accounts, sent as `XTA web sources`.
- Only to `hosts`, over HTTPS on the standard port. Redirects are followed
  by hand, at most three, each to a listed site.
- No JavaScript and no regular expressions: rules are read by XTA's own
  code, and a page that changes shape gives fewer posts, never a crash.

| Limit | |
|---|---|
| The source file | 64 KB |
| Lists | 12, and the search |
| Sites | 8 |
| A page | 4 MB, 100 posts |
| A request | 20 seconds, one at a time and one a second per source |

## When a file is refused

A file that breaks a rule is refused whole. The reader is told why, and
"Where:" names the key it broke as written in the file (`feeds[1].url`,
`fields.title`) or the limit (`hosts: 8`):

| The reader sees | Means |
|---|---|
| This is not a web source file. | not JSON, or not an object |
| This source needs a newer version of XTA. | `format` is higher than this install knows |
| This source file has a mistake. | a key is missing or malformed: a bad id, an unknown placeholder, a rule or selector that cannot be read, a list without `title`/`text` or `link`/`id` fields |
| This source would read a site it does not list, or without HTTPS. | `homepage`, `icon` or a list's `url` is not HTTPS or not on `hosts` |
| This source goes beyond what XTA allows. | over 64 KB, or over a limit above |

## Updates

At most once a day, XTA reads each installed source's file again from
where the reader added it.

- A file with a higher `version` replaces the installed one when every
  site it lists was already agreed to.
- One that adds a site waits until the reader agrees, and the source shows
  that an update is waiting.
- A file with the same or a lower `version`, another `id`, or one that no
  longer reads changes nothing: the installed source keeps working.

Keep `id` and each list's `id`: readers' follows are kept by them.

## Publishing

**In this repository**, so every install offers it under **Add source**:

1. Put the file in `sources/`, named after its `id`.
2. Add it to `sources.json`, with the same `id`, `name` and `description`
   as the file, and its `url` relative to the list:

   ```json
   { "id": "lobsters", "name": "Lobsters", "description": "Computing-focused links and discussion.", "url": "sources/lobsters.json" }
   ```

3. `fvm flutter test test/published_sources_test.dart` checks every listed
   file reads as a source and matches its entry.
4. `fvm flutter test test/live/web_sources_smoke_test.dart --dart-define=RUN_LIVE=true`
   reads the first page of each list, the first post's article, and the
   search when the test names a query for the source (`_queries`; add one).
   A list without posts fails it. The daily `api-canary` workflow runs it
   as "Web sources" and opens an issue when a site changes.

Installs read `sources.json` and the files from the default branch, so a
source is offered once it is merged there, with no release.

**Anywhere else**: any HTTPS address works with **Add from link**, and
updates are read from that same address.
