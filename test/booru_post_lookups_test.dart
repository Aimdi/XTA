import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/booru/booru_post_lookups.dart';

void main() {
  test('an answer is asked for once, and handed out again', () async {
    final lookups = BooruPostLookups();
    var asked = 0;
    Future<int> ask() async => ++asked;

    expect(await lookups.remember('tags:a', ask), 1);
    expect(await lookups.remember('tags:a', ask), 1);
    expect(await lookups.remember('tags:b', ask), 2);
    expect(asked, 2);
  });

  test('a failed answer is asked for again', () async {
    final lookups = BooruPostLookups();
    var asked = 0;
    Future<int> ask() async {
      asked++;
      if (asked == 1) throw StateError('host down');
      return asked;
    }

    await expectLater(lookups.remember('tags:a', ask), throwsStateError);
    expect(await lookups.remember('tags:a', ask), 2);
  });

  test('only the most recently used answers are kept', () async {
    final lookups = BooruPostLookups(capacity: 2);
    var asked = 0;
    Future<int> ask() async => ++asked;

    await lookups.remember('a', ask);
    await lookups.remember('b', ask);
    await lookups.remember('a', ask);
    await lookups.remember('c', ask);
    await lookups.remember('a', ask);
    await lookups.remember('b', ask);

    expect(asked, 4, reason: 'b was the least recent when c came in, so it was asked again');
  });
}
