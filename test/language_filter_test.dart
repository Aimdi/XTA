import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/language_filter.dart';

TweetChain _chain(String id, String? lang) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..lang = lang;
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  group('parseFeedLanguages', () {
    test('splits CSV prefixes', () {
      expect(parseFeedLanguages('en, ja ,fr'), ['en', 'ja', 'fr']);
    });
  });

  group('applyLanguageFilter', () {
    test('off passes everything through', () {
      final chains = [_chain('1', 'de')];
      final outcome = applyLanguageFilter(chains, allowedLanguages: const ['en'], action: LanguageFilterAction.off);

      expect(outcome.chains, chains);
    });

    test('hide drops non-matching languages', () {
      final chains = [_chain('1', 'en'), _chain('2', 'de')];
      final outcome = applyLanguageFilter(chains, allowedLanguages: const ['en'], action: LanguageFilterAction.hide);

      expect(outcome.chains.single.id, '1');
    });

    test('fold keeps chain with reason and merges prior folds', () {
      final chains = [_chain('2', 'de')];
      final outcome = applyLanguageFilter(
        chains,
        allowedLanguages: const ['en'],
        action: LanguageFilterAction.fold,
        priorFolds: const {'1': 'spoilers'},
      );

      expect(outcome.chains.single.id, '2');
      expect(outcome.foldReasons['1'], 'spoilers');
      expect(outcome.foldReasons['2'], 'de');
    });
  });
}
