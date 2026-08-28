import 'package:flutter_test/flutter_test.dart';
import 'package:xta/settings/backup_data.dart';

/// The exact top-level keys a backup file carries.
///
/// Pinned rather than derived: a reader's existing backup is only importable
/// while these names stay put, so a rename has to be a deliberate edit to this
/// list and not a side effect of moving code around.
const _keys = {
  'formatVersion',
  'exportedAt',
  'appVersion',
  'settings',
  'searchSubscriptions',
  'subscriptions',
  'substackSubscriptions',
  'redditSubscriptions',
  'stockSubscriptions',
  'threadsSubscriptions',
  'blueskySubscriptions',
  'mastodonSubscriptions',
  'redditLocalVotes',
  'threadsLocalLikes',
  'blueskyLocalLikes',
  'subscriptionGroups',
  'subscriptionGroupMembers',
  'searchGroupMembers',
  'tweets',
  'savedTweetFolders',
  'likedTweets',
  'retweetFilters',
  'replyFilters',
  'feedReadPositions',
  'accounts',
  'profileNotes',
  'antennas',
  'localPosts',
  'booruSubscriptions',
  'ehFavorites',
  'ehHistory',
  'tiktokSubscriptions',
  'instagramSubscriptions',
  'rssSubscriptions',
};

void main() {
  group('the backup file format', () {
    test('writes exactly the keys older builds read', () {
      final json = SettingsData(
        exportedAt: DateTime.utc(2026),
        appVersion: 'test',
      ).toJson();

      expect(json.keys.toSet(), _keys);
    });

    test('a file that carries nothing restores nothing', () {
      final data = SettingsData.fromJson({});

      expect(backupCounts(data), isEmpty);
      expect(backupTables(data, includeReadPositions: true), isEmpty);
    });

    test('a header-less file is read as the legacy version', () {
      expect(
        SettingsData.fromJson({}).formatVersion,
        legacyBackupFormatVersion,
      );
    });

    test('an empty section is not the same as a missing one', () {
      final missing = SettingsData.fromJson({});
      final empty = SettingsData.fromJson({'redditSubscriptions': <Object>[]});

      expect(backupTables(missing, includeReadPositions: true).keys, isEmpty);
      expect(backupTables(empty, includeReadPositions: true).keys, isNotEmpty);
    });
  });
}
