import 'dart:convert';
import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/translation.dart';

/// `cacheRequest` guarded its read with `result != null && result == true`,
/// comparing the cached *string* against a bool. That is always false, so the
/// cache was written on every translation and read on none: each one
/// re-requested from X while the entries piled up on disk, unread.
void main() {
  group('decodeCachedTranslation', () {
    test('a stored translation is a hit', () {
      final result = decodeCachedTranslation(jsonEncode({'translation': 'bonjour'}));

      expect(result, isNotNull);
      expect(result!.success, isTrue);
      expect(result.body['translation'], 'bonjour');
    });

    test('nothing stored is a miss', () {
      expect(decodeCachedTranslation(null), isNull);
      expect(decodeCachedTranslation(''), isNull);
    });

    // The old guard's mistake, pinned: a non-string must not be mistaken for a
    // hit, and a string must not be mistaken for a miss.
    test('a value that is not a string is a miss', () {
      expect(decodeCachedTranslation(true), isNull);
      expect(decodeCachedTranslation(42), isNull);
    });

    test('an entry that no longer parses is a miss, not a crash', () {
      expect(decodeCachedTranslation('not json at all'), isNull);
    });
  });

  group('translationCacheKey', () {
    // The key used to name only the post and its source language. That was
    // harmless while the cache was never read; repairing the read without this
    // would have served a reader who changed their device language the
    // translation into the old one.
    test('translations into different languages do not share a key', () {
      final french = translationCacheKey(id: '123', sourceLanguage: 'en', target: const Locale('fr'));
      final german = translationCacheKey(id: '123', sourceLanguage: 'en', target: const Locale('de'));

      expect(french, isNot(german));
    });

    test('the post and its source language still separate keys', () {
      const target = Locale('fr');

      expect(
        translationCacheKey(id: '123', sourceLanguage: 'en', target: target),
        isNot(translationCacheKey(id: '456', sourceLanguage: 'en', target: target)),
      );
      expect(
        translationCacheKey(id: '123', sourceLanguage: 'en', target: target),
        isNot(translationCacheKey(id: '123', sourceLanguage: 'es', target: target)),
      );
    });

    test('the same request maps to the same key', () {
      expect(
        translationCacheKey(id: '123', sourceLanguage: 'en', target: const Locale('fr')),
        translationCacheKey(id: '123', sourceLanguage: 'en', target: const Locale('fr')),
      );
    });

    test('a regional locale is kept distinct from its base language', () {
      expect(
        translationCacheKey(id: '1', sourceLanguage: 'en', target: const Locale('pt', 'BR')),
        isNot(translationCacheKey(id: '1', sourceLanguage: 'en', target: const Locale('pt'))),
      );
    });
  });

  // The key names one post, so without an expiry the cache grows with every
  // post ever translated and nothing ever prunes it.
  test('cached translations expire', () {
    expect(translationCacheTtl.inSeconds, greaterThan(0));
    expect(translationCacheTtl, lessThan(const Duration(days: 365)));
  });
}
