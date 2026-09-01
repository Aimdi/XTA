import 'package:xta/database/entities.dart';
import 'package:xta/group/custom_feed_rules.dart';

/// The rules a group's feed filters with.
///
/// Deliberately not [SubscriptionGroupGet.customRules], which hands back
/// nothing unless the group's sort mode happens to be "custom". Muted words, a
/// content filter and engagement floors are what the reader asked this feed to
/// hide; they stopped applying the moment the feed was sorted by Recent or
/// Popular, which is not something anyone sets a mute list expecting.
CustomFeedRules feedRulesOf(SubscriptionGroupGet group) => CustomFeedRules(
  contentFilter: group.contentFilter,
  minLikes: group.minLikes,
  minRetweets: group.minRetweets,
  mutedKeywords: group.mutedKeywords,
);
