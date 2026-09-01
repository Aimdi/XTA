import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/muted_keyword.dart';

void main() {
  group('parseMutedKeywordsStored', () {
    test('reads legacy CSV as hide keywords', () {
      final parsed = parseMutedKeywordsStored('bitcoin, black friday');

      expect(parsed, hasLength(2));
      expect(parsed.first.term, 'bitcoin');
      expect(parsed.first.action, KeywordFilterAction.hide);
      expect(parsed.first.until, isNull);
    });

    test('reads JSON with until and action', () {
      final raw =
          '[{"term":"spoilers","until":"2099-01-01T00:00:00.000Z","action":"fold"},{"term":"ads","action":"hide"}]';
      final parsed = parseMutedKeywordsStored(raw);

      expect(parsed, hasLength(2));
      expect(parsed.first.action, KeywordFilterAction.fold);
      expect(parsed.first.until, isNotNull);
    });

    test('encode round-trips through JSON storage', () {
      final keywords = [
        MutedKeyword(term: 'nft', action: KeywordFilterAction.fold),
        MutedKeyword(term: 'ads', until: DateTime.utc(2099, 1, 1)),
      ];

      final parsed = parseMutedKeywordsStored(encodeMutedKeywordsStored(keywords));

      expect(parsed.first.term, 'nft');
      expect(parsed.first.action, KeywordFilterAction.fold);
      expect(parsed.last.until, isNotNull);
    });
  });

  group('activeMutedKeywords', () {
    test('drops expired terms', () {
      final active = activeMutedKeywords([
        MutedKeyword(term: 'old', until: DateTime.utc(2020, 1, 1)),
      ], now: DateTime.utc(2025, 1, 1));

      expect(active, isEmpty);
    });
  });
}
