import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/local_post_logic.dart';

void main() {
  group('normalizeLocalPostBody', () {
    test('rejects blank notes', () {
      expect(normalizeLocalPostBody(''), isNull);
      expect(normalizeLocalPostBody('   \n\t'), isNull);
    });

    test('trims and keeps the text', () {
      expect(normalizeLocalPostBody('  hello  '), 'hello');
    });

    test('clips bodies longer than the cap', () {
      final long = 'a' * (localPostMaxLength + 12);
      final out = normalizeLocalPostBody(long);
      expect(out, isNotNull);
      expect(out!.length, localPostMaxLength);
    });
  });

  group('localPostHasContent', () {
    test('a blank note with no media is empty', () {
      expect(localPostHasContent('  ', const []), isFalse);
    });

    test('media alone is enough', () {
      expect(
        localPostHasContent('', const [
          LocalPostMedia(id: 'm1', name: 'pic.jpg', mime: 'image/jpeg'),
        ]),
        isTrue,
      );
    });
  });

  group('localPostMatchesQuery', () {
    test('empty query matches everything', () {
      expect(localPostMatchesQuery('anything', ''), isTrue);
    });

    test('matches case-insensitively', () {
      expect(localPostMatchesQuery('Hello Nextcloud', 'next'), isTrue);
      expect(localPostMatchesQuery('Hello Nextcloud', 'x.com'), isFalse);
    });
  });

  group('inferLocalPostMime', () {
    test('keeps a real mime', () {
      expect(inferLocalPostMime('x.bin', 'image/png'), 'image/png');
    });

    test('fills in from the extension', () {
      expect(inferLocalPostMime('clip.mp4', null), 'video/mp4');
      expect(inferLocalPostMime('pic.JPEG', ''), 'image/jpeg');
    });
  });

  group('LocalPost map', () {
    test('round-trips through toMap/fromMap', () {
      final at = DateTime.utc(2026, 8, 28, 12);
      final post = LocalPost(
        id: 'n1',
        body: 'stays here',
        createdAt: at,
        updatedAt: at,
        media: const [
          LocalPostMedia(id: 'm1', name: 'shot.png', mime: 'image/png'),
        ],
        quotedTweetId: 't1',
        quotedTweetJson: '{"id_str":"t1"}',
      );
      final again = LocalPost.fromMap(post.toMap());
      expect(again.id, 'n1');
      expect(again.body, 'stays here');
      expect(again.createdAt, at);
      expect(again.media, hasLength(1));
      expect(again.media.first.name, 'shot.png');
      expect(again.quotedTweetId, 't1');
    });

    test('a backup map keeps media bytes and fromMap reads them', () {
      final at = DateTime.utc(2026, 8, 28, 12);
      final post = LocalPost(
        id: 'n2',
        body: 'photo',
        createdAt: at,
        updatedAt: at,
        media: const [
          LocalPostMedia(
            id: 'm2',
            name: 'a.jpg',
            mime: 'image/jpeg',
            data: 'Zm9v',
          ),
        ],
      );
      final backup = post.toBackupMap();
      final again = LocalPost.fromMap(Map<String, Object?>.from(backup));
      expect(again.media.single.data, 'Zm9v');
      expect(post.toMap()['media_json'], isNot(contains('Zm9v')));
    });

    test('legacy rows without media columns still parse', () {
      final post = LocalPost.fromMap({
        'id': 'old',
        'body': 'plain',
        'created_at': '2026-08-28T12:00:00.000Z',
        'updated_at': '2026-08-28T12:00:00.000Z',
      });
      expect(post.media, isEmpty);
      expect(post.quotedTweetId, isNull);
      expect(post.inReplyToId, isNull);
    });

    test('round-trips a reply id', () {
      final at = DateTime.utc(2026, 8, 29, 3);
      final post = LocalPost(
        id: 'r1',
        body: 'later',
        createdAt: at,
        updatedAt: at,
        inReplyToId: 'n1',
      );
      expect(LocalPost.fromMap(post.toMap()).inReplyToId, 'n1');
    });
  });

  group('parseQuotedTweet', () {
    test('null and junk yield null', () {
      expect(parseQuotedTweet(null), isNull);
      expect(parseQuotedTweet(''), isNull);
      expect(parseQuotedTweet('{'), isNull);
    });
  });

  group('local note threads', () {
    LocalPost note(String id, {String? reply, DateTime? at}) {
      final time = at ?? DateTime.utc(2026, 8, 29, 3, int.tryParse(id) ?? 0);
      return LocalPost(
        id: id,
        body: id,
        createdAt: time,
        updatedAt: time,
        inReplyToId: reply,
      );
    }

    test('roots skip replies whose parent is still around', () {
      final posts = [note('a'), note('b', reply: 'a'), note('c')];
      expect(localPostRoots(posts).map((p) => p.id), ['a', 'c']);
    });

    test('a reply whose parent is gone becomes a root', () {
      final posts = [note('b', reply: 'missing')];
      expect(localPostRoots(posts).single.id, 'b');
    });

    test('direct reply count ignores nested grandchildren', () {
      final posts = [
        note('a'),
        note('b', reply: 'a'),
        note('c', reply: 'b'),
        note('d', reply: 'a'),
      ];
      expect(localPostDirectReplyCount(posts, 'a'), 2);
      expect(localPostDirectReplyCount(posts, 'b'), 1);
    });

    test('thread walks replies under each parent, oldest first', () {
      final t1 = DateTime.utc(2026, 8, 29, 3, 1);
      final t2 = DateTime.utc(2026, 8, 29, 3, 2);
      final t3 = DateTime.utc(2026, 8, 29, 3, 3);
      final posts = [
        note('a', at: t1),
        note('c', reply: 'a', at: t3),
        note('b', reply: 'a', at: t2),
        note('d', reply: 'b', at: t3),
      ];
      final thread = localPostThread(posts, 'a');
      expect([for (final row in thread) row.post.id], ['a', 'b', 'd', 'c']);
      expect([for (final row in thread) row.depth], [0, 1, 2, 1]);
    });

    test('thread root walks up to the oldest living ancestor', () {
      final posts = [
        note('a'),
        note('b', reply: 'a'),
        note('c', reply: 'b'),
      ];
      expect(localPostThreadRootId(posts, 'c'), 'a');
      expect(localPostThreadRootId(posts, 'a'), 'a');
    });

    test('a cycle does not hang the thread walk', () {
      final posts = [note('a', reply: 'b'), note('b', reply: 'a')];
      final thread = localPostThread(posts, 'a');
      expect(thread.map((row) => row.post.id).toSet(), {'a', 'b'});
      expect(localPostThreadRootId(posts, 'a'), 'a');
    });
  });
}
