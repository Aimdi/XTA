import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/account_posts.dart';

typedef _Post = ({String from, DateTime? at});

AccountPostCache<_Post> _cache({int perAccount = 10, Duration? ttl}) =>
    AccountPostCache<_Post>(dateOf: (post) => post.at, perAccount: perAccount, ttl: ttl ?? kAccountPostsCacheTtl);

DateTime _at(int day) => DateTime.utc(2026, 1, day);

void main() {
  group('AccountPostCache', () {
    test('no accounts asks nobody', () async {
      var asked = 0;
      final posts = await _cache().merge(const [], (_) async {
        asked++;
        return const [];
      });

      expect(posts, isEmpty);
      expect(asked, 0);
    });

    test('merges every account newest first', () async {
      final posts = await _cache().merge(['a', 'b'], (key) async => [(from: key, at: key == 'a' ? _at(1) : _at(5))]);

      expect(posts.map((e) => e.from), ['b', 'a']);
    });

    test('takes only a page of each account', () async {
      final posts = await _cache(
        perAccount: 2,
      ).merge(['a'], (key) async => [(from: key, at: _at(3)), (from: key, at: _at(2)), (from: key, at: _at(1))]);

      expect(posts, hasLength(2));
    });

    test('one account failing does not empty the others', () async {
      final posts = await _cache().merge(['good', 'bad'], (key) async {
        if (key == 'bad') throw StateError('down');
        return [(from: key, at: _at(1))];
      });

      expect(posts.map((e) => e.from), ['good']);
    });

    test('the error surfaces only when nothing at all could be read', () async {
      expect(_cache().merge(['bad'], (_) async => throw StateError('down')), throwsA(isA<StateError>()));
    });

    test('a second read inside the window does not ask again', () async {
      var asked = 0;
      final cache = _cache();
      Future<List<_Post>> fetch(String key) async {
        asked++;
        return [(from: key, at: _at(1))];
      }

      await cache.merge(['a'], fetch);
      await cache.merge(['a'], fetch);

      expect(asked, 1);
    });

    test('a refresh asks again even inside the window', () async {
      var asked = 0;
      final cache = _cache();
      Future<List<_Post>> fetch(String key) async {
        asked++;
        return [(from: key, at: _at(1))];
      }

      await cache.merge(['a'], fetch);
      await cache.merge(['a'], fetch, forceRefresh: true);

      expect(asked, 2);
    });

    test('an expired entry is asked for again', () async {
      var asked = 0;
      final cache = _cache(ttl: Duration.zero);
      Future<List<_Post>> fetch(String key) async {
        asked++;
        return [(from: key, at: _at(1))];
      }

      await cache.merge(['a'], fetch);
      await cache.merge(['a'], fetch);

      expect(asked, 2);
    });

    test('clearing forgets what was cached', () async {
      var asked = 0;
      final cache = _cache();
      Future<List<_Post>> fetch(String key) async {
        asked++;
        return [(from: key, at: _at(1))];
      }

      await cache.merge(['a'], fetch);
      cache.clear();
      await cache.merge(['a'], fetch);

      expect(asked, 2);
    });

    test('only so many accounts are asked for on one call', () async {
      final asked = <String>[];
      final posts = await _cache().merge(['a', 'b', 'c', 'd'], (key) async {
        asked.add(key);
        return [(from: key, at: _at(1))];
      }, maxFetches: 2);

      expect(asked, hasLength(2));
      expect(posts, hasLength(2));
    });

    test('the accounts left over are read on the next call', () async {
      final cache = _cache();
      final asked = <String>[];
      Future<List<_Post>> fetch(String key) async {
        asked.add(key);
        return [(from: key, at: _at(1))];
      }

      await cache.merge(['a', 'b', 'c', 'd'], fetch, maxFetches: 2);
      final second = await cache.merge(['a', 'b', 'c', 'd'], fetch, maxFetches: 2);

      expect(asked.toSet(), {'a', 'b', 'c', 'd'});
      // The first two came back from the cache rather than the network.
      expect(second, hasLength(4));
    });

    test('a cached account never counts against the budget', () async {
      final cache = _cache();
      var asked = 0;
      Future<List<_Post>> fetch(String key) async {
        asked++;
        return [(from: key, at: _at(1))];
      }

      await cache.merge(['a'], fetch);
      asked = 0;
      await cache.merge(['a', 'b'], fetch, maxFetches: 1);

      expect(asked, 1);
    });

    // Latent data-loss: under forceRefresh the cache read is skipped, so a key
    // past the fetch budget returned nothing even though the cache held fresh
    // posts — a pull-to-refresh with more follows than the cap would collapse
    // the timeline to the first batch.
    test('a forced refresh past the budget keeps what the cache holds', () async {
      final cache = _cache();
      Future<List<_Post>> fetch(String key) async => [(from: key, at: _at(1))];

      await cache.merge(['a', 'b'], fetch);
      final refreshed = await cache.merge(['a', 'b'], fetch, forceRefresh: true, maxFetches: 1);

      expect(refreshed.map((e) => e.from).toSet(), {'a', 'b'});
    });

    test('a forced refresh still refetches inside the budget', () async {
      final cache = _cache();
      var asked = 0;
      Future<List<_Post>> fetch(String key) async {
        asked++;
        return [(from: key, at: _at(1))];
      }

      await cache.merge(['a'], fetch);
      await cache.merge(['a'], fetch, forceRefresh: true, maxFetches: 1);

      expect(asked, 2);
    });

    test('pendingCount is what the cache cannot answer yet', () async {
      final cache = _cache();
      await cache.merge(['a'], (key) async => [(from: key, at: _at(1))]);

      expect(cache.pendingCount(['a', 'b', 'c']), 2);
    });


    test('forceRefresh with onPartial paints stale posts first', () async {
      final cache = _cache();
      await cache.merge(['a'], (_) async => [(from: 'old', at: _at(1))]);

      final seen = <List<_Post>>[];
      final slow = Completer<void>();
      final future = cache.merge(
        ['a'],
        (_) async {
          await slow.future;
          return [(from: 'new', at: _at(2))];
        },
        forceRefresh: true,
        onPartial: seen.add,
      );

      for (var i = 0; i < 40 && seen.isEmpty; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 5));
      }
      expect(seen, isNotEmpty, reason: 'stale must paint before the network returns');
      expect(seen.first.single.from, 'old');

      slow.complete();
      final posts = await future;
      expect(posts.single.from, 'new');
    });
    test('a post with no date sorts last rather than being dropped', () async {
      final posts = await _cache().merge([
        'a',
      ], (key) async => [(from: 'undated', at: null), (from: 'dated', at: _at(1))]);

      expect(posts.map((e) => e.from), ['dated', 'undated']);
    });
  });
}
