import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';

void main() {
  group('BooruRating', () {
    test('preference parse keeps s as sensitive', () {
      expect(BooruRating.tryParse('s'), BooruRating.sensitive);
      expect(BooruRating.tryParse('safe'), BooruRating.general);
    });

    test('Moebooru and e621 wire s means safe', () {
      expect(
        BooruRating.parseWire('s', BooruEngine.moebooru),
        BooruRating.general,
      );
      expect(BooruRating.parseWire('s', BooruEngine.e621), BooruRating.general);
      expect(
        BooruRating.parseWire('s', BooruEngine.danbooru),
        BooruRating.sensitive,
      );
    });

    test('exceeds compares ordinal rank', () {
      expect(BooruRating.explicit.exceeds(BooruRating.general), isTrue);
      expect(BooruRating.general.exceeds(BooruRating.explicit), isFalse);
    });
  });

  group('parseBooruPosts', () {
    test('parses a Danbooru post', () {
      final posts = parseBooruPosts(
        [
          {
            'id': 42,
            'created_at': '2024-01-02T03:04:05.000Z',
            'score': 7,
            'rating': 'g',
            'tag_string': '1girl landscape',
            'preview_file_url': 'https://cdn.example/p.jpg',
            'large_file_url': 'https://cdn.example/l.jpg',
            'file_url': 'https://cdn.example/f.png',
            'image_width': 800,
            'image_height': 600,
            'file_ext': 'png',
            'source': 'https://example.com/art',
          },
        ],
        engine: BooruEngine.danbooru,
        host: 'https://danbooru.donmai.us',
      );

      expect(posts, hasLength(1));
      final post = posts.single;
      expect(post.id, '42');
      expect(post.tags, ['1girl', 'landscape']);
      expect(post.rating, BooruRating.general);
      expect(post.hostPageUrl, 'https://danbooru.donmai.us/posts/42');
      expect(booruPostAllowed(post, BooruRating.general), isTrue);
    });

    test('parses Moebooru safe rating as general', () {
      final posts = parseBooruPosts(
        [
          {
            'id': 99,
            'tags': 'cloud sky',
            'created_at': 1700000000,
            'score': 3,
            'rating': 's',
            'width': 1000,
            'height': 1400,
            'preview_url': '/data/preview/ab.jpg',
            'sample_url': '/data/sample/ab.jpg',
            'file_url': '/data/image/ab.png',
            'file_ext': 'png',
          },
        ],
        engine: BooruEngine.moebooru,
        host: 'https://yande.re',
      );

      expect(posts.single.previewUrl, 'https://yande.re/data/preview/ab.jpg');
      expect(posts.single.rating, BooruRating.general);
      expect(posts.single.hostPageUrl, 'https://yande.re/post/show/99');
      expect(booruPostAllowed(posts.single, BooruRating.general), isTrue);
    });

    test('parses Gelbooru-style posts', () {
      final posts = parseBooruPosts(
        [
          {
            'id': 7,
            'tags': 'solo smile',
            'rating': 'general',
            'width': 500,
            'height': 500,
            'preview_url': 'https://safebooru.org/thumb.jpg',
            'sample_url': 'https://safebooru.org/sample.jpg',
            'file_url': 'https://safebooru.org/file.jpg',
            'change': 1700000000,
            'score': null,
          },
        ],
        engine: BooruEngine.gelbooruV2,
        host: 'https://safebooru.org',
      );

      expect(
        posts.single.hostPageUrl,
        'https://safebooru.org/index.php?page=post&s=view&id=7',
      );
      expect(posts.single.tags, ['solo', 'smile']);
    });

    test('Rule34 API host opens the public site', () {
      final posts = parseBooruPosts(
        [
          {
            'id': 8,
            'tags': 'test',
            'rating': 's',
            'width': 1,
            'height': 1,
            'preview_url': 'https://rule34.xxx/thumb.jpg',
          },
        ],
        engine: BooruEngine.gelbooruV2,
        host: 'https://api.rule34.xxx',
      );

      expect(
        posts.single.hostPageUrl,
        'https://rule34.xxx/index.php?page=post&s=view&id=8',
      );
      expect(posts.single.rating, BooruRating.general);
    });

    test('parses e621 nested posts payload', () {
      final posts = parseBooruPosts(
        {
          'posts': [
            {
              'id': 6617540,
              'created_at': '2026-08-11T19:31:24.361-04:00',
              'score': {'up': 1, 'down': 0, 'total': 1},
              'rating': 's',
              'file': {
                'width': 200,
                'height': 300,
                'ext': 'png',
                'url': 'https://static1.e621.net/data/a.png',
              },
              'preview': {'url': 'https://static1.e621.net/data/preview/a.jpg'},
              'sample': {'url': 'https://static1.e621.net/data/sample/a.jpg'},
              'tags': {
                'general': ['smile'],
                'artist': ['someone'],
                'character': [],
                'copyright': [],
                'species': ['fox'],
                'meta': [],
                'lore': [],
              },
              'sources': ['https://example.com'],
            },
          ],
        },
        engine: BooruEngine.e621,
        host: 'https://e621.net',
      );

      expect(posts, hasLength(1));
      expect(posts.single.rating, BooruRating.general);
      expect(posts.single.score, 1);
      expect(posts.single.tags, containsAll(['smile', 'someone', 'fox']));
      expect(posts.single.previewUrl, contains('preview'));
      expect(posts.single.hostPageUrl, 'https://e621.net/posts/6617540');
    });

    test('filters by max rating and muted tags', () {
      final explicit = parseBooruPosts(
        [
          {
            'id': 1,
            'rating': 'e',
            'tag_string': 'x loud',
            'preview_file_url': 'https://x/a.jpg',
            'image_width': 1,
            'image_height': 1,
          },
        ],
        engine: BooruEngine.danbooru,
        host: 'https://danbooru.donmai.us',
      ).single;

      expect(booruPostAllowed(explicit, BooruRating.general), isFalse);
      expect(booruPostAllowed(explicit, BooruRating.explicit), isTrue);
      expect(booruPostMuted(explicit, {'loud'}), isTrue);
      expect(booruPostMuted(explicit, {'quiet'}), isFalse);
    });
  });

  group('host helpers', () {
    test('normalises hosts and tags', () {
      expect(
        normaliseBooruHost('danbooru.donmai.us'),
        'https://danbooru.donmai.us',
      );
      expect(normaliseBooruTag('  Blue Sky '), 'blue_sky');
      expect(normaliseBooruTag('   '), isNull);
      expect(lastBooruTagToken('1girl blue_sky'), 'blue_sky');
      expect(lastBooruTagToken('1girl rating:g'), isNull);
    });

    test('guesses the engine and Rule34 API host', () {
      expect(guessBooruEngine('rule34.xxx'), BooruEngine.gelbooruV2);
      expect(guessBooruEngine('https://xbooru.com'), BooruEngine.gelbooruV2);
      expect(guessBooruEngine('danbooru.donmai.us'), BooruEngine.danbooru);
      expect(guessBooruEngine('yande.re'), BooruEngine.moebooru);
      expect(guessBooruEngine('e621.net'), BooruEngine.e621);
      expect(guessBooruEngine('rule34.paheal.net'), isNull);
      expect(booruRequestHost('https://rule34.xxx'), 'https://api.rule34.xxx');
      expect(booruPageHost('https://api.rule34.xxx'), 'https://rule34.xxx');
    });

    test('saves custom sites by host', () {
      const site = BooruPreset(
        id: 'custom:example.com',
        name: 'Example',
        engine: BooruEngine.gelbooruV2,
        host: 'https://example.com',
      );
      final encoded = encodeBooruCustomSites([site]);
      final parsed = parseBooruCustomSites(encoded);
      expect(parsed, hasLength(1));
      expect(parsed.single.host, 'https://example.com');
      expect(parsed.single.engine, BooruEngine.gelbooruV2);

      final updated = upsertBooruCustomSite(parsed, site);
      expect(updated, hasLength(1));
    });
  });

  group('Gelbooru-family ratings', () {
    test('letter s means safe, not Danbooru sensitive', () {
      expect(
        BooruRating.parseWire('s', BooruEngine.gelbooruV2),
        BooruRating.general,
      );
      expect(
        BooruRating.parseWire('sensitive', BooruEngine.gelbooruV2),
        BooruRating.sensitive,
      );
    });
  });

  group('tag suggestions', () {
    test('parses Danbooru-shaped tag rows', () {
      final tags = parseBooruTagSuggestions([
        {'name': '1girl', 'post_count': 100},
        {'name': '2girls', 'count': 50},
      ], engine: BooruEngine.danbooru);
      expect(tags.map((t) => t.name), ['1girl', '2girls']);
      expect(tags.first.postCount, 100);
    });
  });
}
