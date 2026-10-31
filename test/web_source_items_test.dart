import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_items.dart';

import 'support/web_source_fixtures.dart';

void main() {
  group('a JSON list page', () {
    final source = sourceFrom(linksSourceJson());
    final hottest = source.feeds.first;
    final url = webSourceListUrl(hottest)!;
    final page = webSourcePage(source, hottest, linksPage, url, page: 1);

    test('starts at the first page', () {
      expect(url, Uri.parse('https://lobste.rs/page/1.json'));
    });

    test('gives a post per element that has an id or link and a title', () {
      expect(page.items.map((item) => item.key), ['lobsters:abc', 'lobsters:def']);
    });

    test('reads every field, decoding entities and numbers', () {
      final first = page.items.first;
      expect(first.title, 'Rust & Dart');
      expect(first.link, 'https://example.org/a');
      expect((first.author, first.score, first.comments), ('ann', 12, 3));
      expect(first.discussion, 'https://lobste.rs/s/abc');
      expect(first.tags, ['rust', 'dart']);
      expect(first.date, DateTime.utc(2026, 10, 29, 13));
      expect(page.items[1].score, 4);
      expect(page.items[1].link, isNull);
    });

    test('names the next page by number', () {
      expect(page.next, Uri.parse('https://lobste.rs/page/2.json'));
    });

    test('an empty page has no next page, and a broken one no posts', () {
      expect(webSourcePage(source, hottest, '[]', url, page: 3).next, isNull);
      expect(webSourcePage(source, hottest, '{oops', url).items, isEmpty);
    });

    test('a long page is read the same off the UI thread', () async {
      final long =
          '[${List.filled(300, '{"short_id": "x", "title": "${'t' * 80}", "url": "https://example.org/a"}').join(',')}]';
      final background = await readWebSourcePage(source, hottest, long, url, page: 1);

      expect(long.length, greaterThan(20000));
      expect(background.items, hasLength(webSourceMaxItems));
      expect(background.items.first.title, 't' * 80);
      expect(background.next, Uri.parse('https://lobste.rs/page/2.json'));
    });

    test('search fills the query', () {
      expect(
        webSourceListUrl(source.search!, query: 'dart & flutter'),
        Uri.parse('https://lobste.rs/search.json?q=dart%20%26%20flutter&page=1'),
      );
    });
  });

  group('a nested JSON page with templates and a cursor', () {
    final source = sourceFrom(postsSourceJson());
    final hot = source.feeds.single;
    final url = webSourceListUrl(hot)!;
    final page = webSourcePage(source, hot, postsPage, url);

    test('starts with an empty cursor', () {
      expect(url.toString(), 'https://lemmy.world/api/v3/post/list?sort=Hot&page_cursor=');
    });

    test('builds links from templates and resolves relative pictures', () {
      final post = page.items.single;
      expect(post.link, 'https://lemmy.world/post/7');
      expect(post.image, 'https://lemmy.world/pictrs/7.jpg');
      expect(post.text, 'First post');
      expect(post.date, DateTime.fromMillisecondsSinceEpoch(1761724800 * 1000, isUtc: true));
    });

    test('names the next page by its cursor', () {
      expect(page.next.toString(), 'https://lemmy.world/api/v3/post/list?sort=Hot&page_cursor=Pa7');
    });

    test('finds the full text where the article rule says', () {
      final address = webSourceArticleUrl(source.article!, page.items.single);
      expect(address, Uri.parse('https://lemmy.world/api/v3/post?id=7'));
      expect(webSourceArticleHtml(source.article!, '{"post_view": {"post": {"body": "<p>Full</p>"}}}'), '<p>Full</p>');
    });
  });

  group('an HTML page', () {
    final source = sourceFrom(htmlSourceJson());
    final all = source.feeds.single;
    final url = Uri.parse('https://tildes.net/');
    final page = webSourcePage(source, all, htmlPage, url);

    test('gives a post per matching element with a title', () {
      final post = page.items.single;
      expect(post.id, 'topic-1');
      expect(post.title, 'Hello world');
      expect(post.link, 'https://tildes.net/~comp/1/hello');
      expect(post.text, 'An excerpt');
      expect(post.comments, 1205);
      expect(post.date, DateTime.utc(2026, 10, 29, 10));
    });

    test('follows the next page link', () {
      expect(page.next, Uri.parse('https://tildes.net/?after=topic-1'));
    });

    test('an article rule reads markup', () {
      const article = WebSourceArticle(url: '{link}', type: WebSourcePageType.html, html: 'div.body');
      expect(webSourceArticleHtml(article, '<div class="body"><p>Text</p></div>'), '<p>Text</p>');
    });
  });

  test('a gallery page gives pictures', () {
    final source = sourceFrom(gallerySourceJson());
    final page = webSourcePage(source, source.feeds.single, '''{"collection": {"items": [
      {"data": [{"nasa_id": "as11", "title": "Earthrise"}], "links": [{"href": "https://images-assets.nasa.gov/a.jpg"}]}
    ]}}''', Uri.parse('https://images-api.nasa.gov/search?q=moon'));

    final picture = page.items.single;
    expect(picture.pictures, ['https://images-assets.nasa.gov/a.jpg']);
    expect(picture.link, 'https://images.nasa.gov/details/as11');
  });

  test('an RSS page is read by the RSS parser', () {
    final source = sourceFrom(
      {
        ...linksSourceJson(),
        'type': 'rss',
        'feeds': [
          {'id': 'rss', 'name': 'RSS', 'url': 'https://lobste.rs/rss'},
        ],
      }..remove('search'),
    );
    final page = webSourcePage(source, source.feeds.single, '''<rss><channel><item>
      <title>From RSS</title><link>https://lobste.rs/s/x</link><guid>x</guid>
      <pubDate>Wed, 29 Oct 2026 08:00:00 GMT</pubDate></item></channel></rss>''', Uri.parse('https://lobste.rs/rss'));

    expect(page.items.single.title, 'From RSS');
    expect(page.items.single.link, 'https://lobste.rs/s/x');
    expect(page.next, isNull);
  });

  test('dates in each form sites write them', () {
    expect(webSourceDate('2026-10-29T10:00:00Z'), DateTime.utc(2026, 10, 29, 10));
    expect(webSourceDate('Wed, 29 Oct 2026 10:00:00 GMT'), DateTime.utc(2026, 10, 29, 10));
    expect(webSourceDate('1761732000'), DateTime.utc(2025, 10, 29, 10));
    expect(webSourceDate('1761732000000'), DateTime.utc(2025, 10, 29, 10));
    expect(webSourceDate('yesterday'), isNull);
  });
}
