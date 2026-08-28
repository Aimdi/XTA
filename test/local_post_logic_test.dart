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

  group('localPostMatchesQuery', () {
    test('empty query matches everything', () {
      expect(localPostMatchesQuery('anything', ''), isTrue);
    });

    test('matches case-insensitively', () {
      expect(localPostMatchesQuery('Hello Nextcloud', 'next'), isTrue);
      expect(localPostMatchesQuery('Hello Nextcloud', 'x.com'), isFalse);
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
      );
      final again = LocalPost.fromMap(post.toMap());
      expect(again.id, 'n1');
      expect(again.body, 'stays here');
      expect(again.createdAt, at);
    });
  });
}
