import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/custom_feed_rules.dart';
import 'package:xta/group/feed_rules.dart';

SubscriptionGroupGet _group({required bool custom}) => SubscriptionGroupGet(
  id: '1',
  name: 'Group',
  icon: 'rss_feed',
  subscriptions: const [],
  includeReplies: true,
  includeRetweets: true,
  popular: false,
  custom: custom,
  contentFilter: contentFilterSfw,
  minLikes: 10,
  minRetweets: 5,
  mutedKeywords: const [MutedKeyword(term: 'spoilers')],
);

void main() {
  group('feedRulesOf', () {
    test('applies the group\'s rules whatever the sort mode', () {
      // The rules used to hang off `custom`, which is a *sort mode* -- so
      // sorting by Recent silently unmuted everything the reader had muted.
      final rules = feedRulesOf(_group(custom: false));

      expect(rules.mutedKeywords.single.term, 'spoilers');
      expect(rules.minLikes, 10);
      expect(rules.minRetweets, 5);
      expect(rules.contentFilter, contentFilterSfw);
    });

    test('is unchanged when the sort mode is custom', () {
      expect(feedRulesOf(_group(custom: true)).cacheKey, feedRulesOf(_group(custom: false)).cacheKey);
    });

    test('a group with nothing configured filters nothing', () {
      final bare = SubscriptionGroupGet(
        id: '1',
        name: 'Group',
        icon: 'rss_feed',
        subscriptions: const [],
        includeReplies: true,
        includeRetweets: true,
        popular: false,
      );

      expect(feedRulesOf(bare).isEmpty, isTrue);
    });
  });
}
