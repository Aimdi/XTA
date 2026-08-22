import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/rss/rss_parser.dart';

void main() {
  group('parseRss', () {
    test('reads RSS 2.0 channel metadata and items', () {
      const xml = '''
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel>
<title><![CDATA[Slow Boring]]></title>
<description><![CDATA[Pragmatic takes.]]></description>
<link>https://www.slowboring.com</link>
<image><url>https://example.org/logo.png</url></image>
<item>
<title><![CDATA[Hello]]></title>
<link>https://www.slowboring.com/p/hello-there</link>
<guid>https://www.slowboring.com/p/hello-there</guid>
<pubDate>Tue, 04 Aug 2026 23:03:08 GMT</pubDate>
<dc:creator>Matt</dc:creator>
<category>Politics</category>
<description><![CDATA[<p>An opening paragraph.</p>]]></description>
<enclosure url="https://example.org/cover.jpg" type="image/jpeg" />
</item>
</channel></rss>
''';

      final channel = parseRss(xml, feedUrl: 'https://www.slowboring.com/feed');

      expect(channel.title, 'Slow Boring');
      expect(channel.description, 'Pragmatic takes.');
      expect(channel.imageUrl, 'https://example.org/logo.png');
      expect(channel.items, hasLength(1));
      final item = channel.items.single;
      expect(item.title, 'Hello');
      expect(item.link, 'https://www.slowboring.com/p/hello-there');
      expect(item.author, 'Matt');
      expect(item.categories, ['Politics']);
      expect(item.imageUrl, 'https://example.org/cover.jpg');
      expect(item.publishedAt, DateTime.utc(2026, 8, 4, 23, 3, 8));
      expect(item.excerpt, contains('opening paragraph'));
    });

    test('reads Atom entries and html links', () {
      const xml = '''
<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Example Feed</title>
  <link href="https://example.org/"/>
  <entry>
    <title>Atom-Powered Robots Run Amok</title>
    <link href="https://example.org/2003/12/13/atom03" rel="alternate" type="text/html"/>
    <id>urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a</id>
    <updated>2003-12-13T18:30:02Z</updated>
    <summary>Some text.</summary>
    <author><name>John Doe</name></author>
    <category term="robots"/>
  </entry>
</feed>
''';

      final channel = parseRss(xml, feedUrl: 'https://example.org/atom.xml');

      expect(channel.title, 'Example Feed');
      expect(channel.items, hasLength(1));
      final item = channel.items.single;
      expect(item.title, 'Atom-Powered Robots Run Amok');
      expect(item.link, 'https://example.org/2003/12/13/atom03');
      expect(item.author, 'John Doe');
      expect(item.categories, ['robots']);
      expect(item.publishedAt, DateTime.utc(2003, 12, 13, 18, 30, 2));
      expect(item.excerpt, 'Some text.');
    });

    test('skips items that have no title, link or guid', () {
      const xml = '''
<rss><channel>
<item><description>orphan</description></item>
<item><title>Kept</title><link>https://example.org/kept</link></item>
</channel></rss>
''';

      final channel = parseRss(xml, feedUrl: 'https://example.org/feed');
      expect(channel.items, hasLength(1));
      expect(channel.items.single.title, 'Kept');
    });

    test('does not throw on mangled markup', () {
      expect(
        parseRss('<not-a-feed', feedUrl: 'https://example.org/feed').items,
        isEmpty,
      );
      expect(parseRss('', feedUrl: 'https://example.org/feed').items, isEmpty);
    });
  });

  group('parseRssDate', () {
    test('reads RFC 822 and ISO-8601', () {
      expect(
        parseRssDate('Tue, 04 Aug 2026 23:03:08 GMT'),
        DateTime.utc(2026, 8, 4, 23, 3, 8),
      );
      expect(
        parseRssDate('2026-08-04T12:00:00Z'),
        DateTime.utc(2026, 8, 4, 12),
      );
      expect(parseRssDate('not a date'), isNull);
      expect(parseRssDate(null), isNull);
    });
  });

  group('discoverFeedUrls', () {
    test('resolves alternate rss and atom links against the page', () {
      const html = '''
<html><head>
<link rel="alternate" type="application/rss+xml" href="/feed.xml">
<link rel="alternate" type="application/atom+xml" href="https://cdn.example.org/atom.xml">
<link rel="stylesheet" href="/app.css">
</head></html>
''';

      expect(
        discoverFeedUrls(
          html,
          page: Uri.parse('https://blog.example.org/about'),
        ),
        [
          'https://blog.example.org/feed.xml',
          'https://cdn.example.org/atom.xml',
        ],
      );
    });
  });
}
