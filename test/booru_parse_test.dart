import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';

void main() {
  group('BooruRating', () {
    test('parses legacy and modern labels', () {
      expect(BooruRating.tryParse('g'), BooruRating.general);
      expect(BooruRating.tryParse('safe'), BooruRating.general);
      expect(BooruRating.tryParse('general'), BooruRating.general);
      expect(BooruRating.tryParse('sensitive'), BooruRating.sensitive);
      expect(BooruRating.tryParse('q'), BooruRating.questionable);
      expect(BooruRating.tryParse('explicit'), BooruRating.explicit);
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
      expect(post.previewUrl, 'https://cdn.example/p.jpg');
      expect(post.width, 800);
      expect(booruPostAllowed(post, BooruRating.general), isTrue);
    });

    test('parses a Moebooru post with unix created_at', () {
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
      expect(posts.single.createdAt, isNotNull);
      expect(posts.single.rating, BooruRating.sensitive);
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

      expect(posts.single.id, '7');
      expect(posts.single.tags, ['solo', 'smile']);
    });

    test('filters by max rating', () {
      final explicit = parseBooruPosts(
        [
          {
            'id': 1,
            'rating': 'e',
            'tag_string': 'x',
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
    });
  });
}
