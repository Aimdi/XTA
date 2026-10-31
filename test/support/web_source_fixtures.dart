import 'dart:convert';

import 'package:xta/plugins/websources/web_source_definition.dart';

/// A links source over a JSON API, as Lobsters publishes one.
Map<String, Object?> linksSourceJson({int version = 1, List<String> hosts = const ['lobste.rs']}) => {
  'format': 1,
  'id': 'lobsters',
  'version': version,
  'name': 'Lobsters',
  'description': 'Computing-focused links',
  'homepage': 'https://lobste.rs',
  'color': '#AC130D',
  'hosts': hosts,
  'layout': 'links',
  'type': 'json',
  'items': r'$',
  'fields': {
    'id': 'short_id',
    'title': 'title',
    'link': 'url',
    'author': 'submitter_user',
    'date': 'created_at',
    'score': 'score',
    'comments': 'comment_count',
    'discussion': 'comments_url',
    'tags': 'tags[*]',
  },
  'feeds': [
    {'id': 'hottest', 'name': 'Hottest', 'url': 'https://lobste.rs/page/{page}.json'},
    {'id': 'newest', 'name': 'Newest', 'url': 'https://lobste.rs/newest/page/{page}.json'},
  ],
  'search': {'url': 'https://lobste.rs/search.json?q={query}&page={page}'},
};

const linksPage = '''[
  {"short_id": "abc", "title": "Rust &amp; Dart", "url": "https://example.org/a", "submitter_user": "ann",
   "created_at": "2026-10-29T08:00:00.000-05:00", "score": 12, "comment_count": 3,
   "comments_url": "https://lobste.rs/s/abc", "tags": ["rust", "dart"]},
  {"short_id": "def", "title": "Ask: editors", "url": "", "submitter_user": "bo",
   "created_at": "2026-10-28T08:00:00Z", "score": "4", "comment_count": 0,
   "comments_url": "https://lobste.rs/s/def", "tags": []},
  {"title": "No id or link"}
]''';

/// A posts source with nested JSON, templates and a cursor, as Lemmy's API.
Map<String, Object?> postsSourceJson() => {
  'format': 1,
  'id': 'lemmy-world',
  'version': 2,
  'name': 'Lemmy World',
  'homepage': 'https://lemmy.world',
  'hosts': ['lemmy.world'],
  'layout': 'posts',
  'feeds': [
    {
      'id': 'hot',
      'name': 'Hot',
      'url': 'https://lemmy.world/api/v3/post/list?sort=Hot&page_cursor={cursor}',
      'items': 'posts',
      'next': 'next_page',
      'fields': {
        'id': 'post.id',
        'title': 'post.name',
        'text': 'post.body',
        'link': 'https://lemmy.world/post/{post.id}',
        'author': 'creator.name',
        'avatar': 'creator.avatar',
        'image': 'post.thumbnail_url',
        'date': 'post.published',
        'score': 'counts.score',
        'comments': 'counts.comments',
      },
    },
  ],
  'article': {'url': 'https://lemmy.world/api/v3/post?id={id}', 'html': 'post_view.post.body'},
};

const postsPage = '''{
  "posts": [
    {"post": {"id": 7, "name": "Hello", "body": "<p>First <em>post</em></p>", "thumbnail_url": "/pictrs/7.jpg",
              "published": "1761724800"},
     "creator": {"name": "cy", "avatar": "https://lemmy.world/pictrs/cy.png"},
     "counts": {"score": 9, "comments": 2}}
  ],
  "next_page": "Pa7"
}''';

/// An HTML source, as a site without an API.
Map<String, Object?> htmlSourceJson() => {
  'format': 1,
  'id': 'tildes',
  'version': 1,
  'name': 'Tildes',
  'homepage': 'https://tildes.net',
  'hosts': ['tildes.net'],
  'layout': 'articles',
  'type': 'html',
  'items': 'article.topic',
  'fields': {
    'id': '@id',
    'title': 'h1.topic-title a',
    'link': 'h1.topic-title a@href',
    'text': '.topic-text-excerpt',
    'date': 'time@datetime',
    'comments': '.topic-info-comments span',
  },
  'feeds': [
    {'id': 'all', 'name': 'All', 'url': 'https://tildes.net/', 'next': 'a#next-page@href'},
  ],
};

const htmlPage = '''<html><body><ol>
  <li><article class="topic" id="topic-1">
    <h1 class="topic-title"><a href="/~comp/1/hello">Hello   world</a></h1>
    <div class="topic-text-excerpt">An <b>excerpt</b></div>
    <time datetime="2026-10-29T10:00:00Z">2h</time>
    <span class="topic-info-comments"><span>1,205 comments</span></span>
  </article></li>
  <li><article class="topic" id="topic-2"><div>no title or text</div></article></li>
</ol><a id="next-page" href="/?after=topic-1">Next</a></body></html>''';

/// A gallery source.
Map<String, Object?> gallerySourceJson() => {
  'format': 1,
  'id': 'nasa-images',
  'version': 1,
  'name': 'NASA Images',
  'homepage': 'https://images.nasa.gov',
  'hosts': ['images-api.nasa.gov', 'images.nasa.gov'],
  'layout': 'gallery',
  'feeds': [
    {
      'id': 'moon',
      'name': 'Moon',
      'url': 'https://images-api.nasa.gov/search?q=moon&media_type=image&page={page}',
      'items': 'collection.items',
      'fields': {
        'id': 'data[0].nasa_id',
        'title': 'data[0].title',
        'image': 'links[0].href',
        'link': 'https://images.nasa.gov/details/{data[0].nasa_id}',
      },
    },
  ],
};

WebSourceDefinition sourceFrom(Map<String, Object?> json) => (readWebSource(jsonEncode(json)) as ReadWebSource).source;
