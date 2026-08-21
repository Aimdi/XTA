import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/rss/rss_models.dart';

RssItem _item(
  String id, {
  DateTime? at,
  String feedId = 'https://a.example/feed',
}) {
  return RssItem(
    id: id,
    title: id,
    feedId: feedId,
    feedTitle: 'A',
    publishedAt: at,
  );
}

void main() {
  group('mergeRssItems', () {
    test('sorts newest first and drops duplicate ids', () {
      final older = _item('a', at: DateTime.utc(2026, 1, 1));
      final newer = _item('b', at: DateTime.utc(2026, 8, 1));
      final updated = _item('a', at: DateTime.utc(2026, 8, 2));

      final merged = mergeRssItems([older, newer], [updated]);

      expect(merged.map((e) => e.id), ['a', 'b']);
      expect(merged.first.publishedAt, DateTime.utc(2026, 8, 2));
    });

    test('items without a date sink below dated ones', () {
      final dated = _item('dated', at: DateTime.utc(2026, 1, 1));
      final undated = _item('undated');

      expect(mergeRssItems([undated], [dated]).map((e) => e.id), [
        'dated',
        'undated',
      ]);
    });
  });

  group('itemMatchesRssFilter', () {
    final item = _item('post-1', feedId: 'feed-1');

    test('unread hides ids already marked read', () {
      expect(itemMatchesRssFilter(item, RssFeedFilter.all, {'post-1'}), isTrue);
      expect(
        itemMatchesRssFilter(item, RssFeedFilter.unread, {'post-1'}),
        isFalse,
      );
      expect(
        itemMatchesRssFilter(item, RssFeedFilter.unread, const {}),
        isTrue,
      );
    });

    test('tag keeps only items from tagged feeds', () {
      expect(
        itemMatchesRssFilter(
          item,
          RssFeedFilter.all,
          const {},
          tag: 'news',
          tagsByFeed: {
            'feed-1': ['news'],
          },
        ),
        isTrue,
      );
      expect(
        itemMatchesRssFilter(
          item,
          RssFeedFilter.all,
          const {},
          tag: 'news',
          tagsByFeed: const {},
        ),
        isFalse,
      );
    });
  });

  group('rssFeedId / looksLikeRssUrl', () {
    test('normalises host case and trailing slashes', () {
      expect(
        rssFeedId('https://Example.COM/feed/'),
        'https://example.com/feed',
      );
    });

    test('listFromPrefs accepts a restored List, not only a JSON string', () {
      final feed = {
        'id': 'https://example.com/feed',
        'feedUrl': 'https://example.com/feed',
        'name': 'Example',
      };
      expect(RssFeed.listFromPrefs([feed]).single.name, 'Example');
      expect(RssFeed.listFromPrefs(jsonEncode([feed])).single.name, 'Example');
      expect(RssFeed.listFromPrefs(null), isEmpty);
      expect(readIdsFromPrefs(['a', 'b']), ['a', 'b']);
      expect(
        rssTagsFromPrefs({
          'https://example.com/feed': ['news'],
        }),
        {
          'https://example.com/feed': ['news'],
        },
      );
    });

    test('recognises feed paths and not site homes', () {
      expect(looksLikeRssUrl('https://example.com/feed'), isTrue);
      expect(looksLikeRssUrl('https://example.com/atom.xml'), isTrue);
      expect(looksLikeRssUrl('https://example.com/'), isFalse);
      expect(looksLikeRssUrl('https://example.com/feedback'), isFalse);
    });
  });
}
