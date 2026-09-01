import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_rss.dart';

void main() {
  group('parseSubstackRss', () {
    test('reads channel metadata and items', () {
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
<pubDate>Tue, 04 Aug 2026 23:03:08 GMT</pubDate>
<description><![CDATA[<p>An opening paragraph.</p>]]></description>
<enclosure url="https://example.org/ep.mp3" type="audio/mpeg" />
</item>
</channel></rss>
''';

      final channel = parseSubstackRss(
        xml,
        publicationBaseUrl: 'https://www.slowboring.com',
        publicationName: 'fallback',
      );

      expect(channel.title, 'Slow Boring');
      expect(channel.description, 'Pragmatic takes.');
      expect(channel.imageUrl, 'https://example.org/logo.png');
      expect(channel.looksLikeSubstack, isFalse);
      expect(channel.posts, hasLength(1));
      expect(channel.posts.first.slug, 'hello-there');
      expect(channel.posts.first.isPodcast, isTrue);
      expect(channel.posts.first.publishedAt, isNotNull);
    });

    test('recognises Substack RSS and rejects Ghost', () {
      expect(
        rssLooksLikeSubstack(
          '<rss><channel><generator>Substack</generator></channel></rss>',
        ),
        isTrue,
      );
      expect(
        rssLooksLikeSubstack(
          '<rss><channel><generator>Ghost 6.59</generator></channel></rss>',
        ),
        isFalse,
      );
    });
  });

  group('postMatchesSubstackFilter', () {
    final free = SubstackPost(
      id: '1',
      title: 'Free',
      slug: 'free',
      publicationBaseUrl: 'https://example.substack.com',
      publicationName: 'Ex',
      audience: 'everyone',
    );
    final paid = SubstackPost(
      id: '2',
      title: 'Paid',
      slug: 'paid',
      publicationBaseUrl: 'https://example.substack.com',
      publicationName: 'Ex',
      audience: 'only_paid',
    );
    final pod = SubstackPost(
      id: '3',
      title: 'Pod',
      slug: 'pod',
      publicationBaseUrl: 'https://example.substack.com',
      publicationName: 'Ex',
      audioUrl: 'https://example.org/a.mp3',
      type: 'podcast',
    );

    test('unread / free / podcast', () {
      expect(
        postMatchesSubstackFilter(free, SubstackFeedFilter.unread, {'1'}),
        isFalse,
      );
      expect(
        postMatchesSubstackFilter(free, SubstackFeedFilter.unread, const {}),
        isTrue,
      );
      expect(
        postMatchesSubstackFilter(paid, SubstackFeedFilter.free, const {}),
        isFalse,
      );
      expect(
        postMatchesSubstackFilter(pod, SubstackFeedFilter.podcast, const {}),
        isTrue,
      );
    });
  });

  group('SubstackNote.fromReaderItem', () {
    test('builds a note URL and publication', () {
      final note = SubstackNote.fromReaderItem({
        'entity_key': 'c-1',
        'comment': {
          'id': 1,
          'name': 'Ann',
          'handle': 'ann',
          'body': 'Hello note',
          'date': '2026-08-01T10:00:00Z',
          'reaction_count': 3,
          'user_primary_publication': {
            'subdomain': 'ann',
            'name': 'Ann Writes',
            'logo_url': 'https://example.org/a.png',
          },
        },
      });

      expect(note.body, 'Hello note');
      expect(note.url, 'https://substack.com/@ann/note/c-1');
      expect(note.publication?.name, 'Ann Writes');
    });
  });
}
