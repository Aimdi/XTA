import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/plugins/plugin_registry.dart';
export 'package:xta/settings/backup_category.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/settings/backup_rows.dart';

/// The backup document: what QuaX carries between devices, and the header that
/// says what it is.
///
/// Left out on purpose: `feed_group_chunk`, `timeline_cache` and
/// `feed_group_cursor` are caches of what X already served and are dropped
/// after a week anyway, and `post_notification` was removed from the schema in
/// migration 30, so nothing has rows there to save.
///
/// Everything else is here. Followed stocks, followed Threads accounts,
/// device-local Reddit upvotes and Threads likes were missed until now because
/// none of them is one of the four tables `SubscriptionsModel` reads — the
/// upvotes and likes especially, since the networks are never told about them
/// and this file is their only copy.

/// The sections every installed plugin owns, in registry order.
///
/// Read rather than listed: the backup used to name each plugin's tables again
/// by hand, which is how rows that exist nowhere else went unsaved.
List<PluginBackupSection> pluginBackupSections() => [
  for (final plugin in builtInPlugins) ...plugin.backupSections,
];

/// Raised whenever an older build could misread a newer file. A reader that
/// meets a higher number refuses the file instead of applying the part of it
/// it happens to understand.
const int backupFormatVersion = 1;

/// A file written before the header existed. Every backup already on a reader's
/// phone is one of these, and it still imports exactly as it used to.
const int legacyBackupFormatVersion = 0;

bool isSupportedBackupVersion(int version) => version <= backupFormatVersion;


class SettingsData {
  final int formatVersion;
  final DateTime? exportedAt;
  final String? appVersion;
  final Map<String, dynamic>? settings;
  /// Rows belonging to plugins, keyed by the section's `jsonKey`.
  ///
  /// Held generically so that adding a plugin cannot forget the backup: the
  /// sections come from the plugin registry, not from a list in this file.
  final Map<String, List<ToMappable>>? pluginRows;

  final List<SearchSubscription>? searchSubscriptions;
  final List<UserSubscription>? userSubscriptions;
  final List<SubscriptionGroup>? subscriptionGroups;
  final List<SubscriptionGroupMember>? subscriptionGroupMembers;
  final List<SearchGroupMember>? searchGroupMembers;
  final List<SavedTweet>? tweets;
  final List<SavedTweetFolder>? savedTweetFolders;
  final List<LikedTweet>? likedTweets;
  final List<UserFeedFilter>? retweetFilters;
  final List<UserFeedFilter>? replyFilters;
  final List<FeedReadPositionRow>? feedReadPositions;
  final List<Account>? accounts;
  final List<ProfileNote>? profileNotes;
  final List<Antenna>? antennas;
  final List<LocalPost>? localPosts;

  SettingsData({
    this.formatVersion = backupFormatVersion,
    this.exportedAt,
    this.appVersion,
    this.settings,
    this.pluginRows,
    this.searchSubscriptions,
    this.userSubscriptions,
    this.subscriptionGroups,
    this.subscriptionGroupMembers,
    this.searchGroupMembers,
    this.tweets,
    this.savedTweetFolders,
    this.likedTweets,
    this.retweetFilters,
    this.replyFilters,
    this.feedReadPositions,
    this.accounts,
    this.profileNotes,
    this.antennas,
    this.localPosts,
  });

  factory SettingsData.fromJson(Map<String, dynamic> json) {
    return SettingsData(
      formatVersion: (json['formatVersion'] as int?) ?? legacyBackupFormatVersion,
      exportedAt: DateTime.tryParse((json['exportedAt'] as String?) ?? ''),
      appVersion: json['appVersion'] as String?,
      settings: json['settings'] as Map<String, dynamic>?,
      pluginRows: {
        for (final section in pluginBackupSections())
          if (_rows(json[section.jsonKey], section.fromMap) case final rows?) section.jsonKey: rows,
      },
      searchSubscriptions: _rows(json['searchSubscriptions'], SearchSubscription.fromMap),
      userSubscriptions: _rows(json['subscriptions'], UserSubscription.fromMap),
      subscriptionGroups: _rows(json['subscriptionGroups'], SubscriptionGroup.fromMap),
      subscriptionGroupMembers: _rows(json['subscriptionGroupMembers'], SubscriptionGroupMember.fromMap),
      searchGroupMembers: _rows(json['searchGroupMembers'], SearchGroupMember.fromMap),
      tweets: _rows(json['tweets'], SavedTweet.fromMap),
      savedTweetFolders: _rows(json['savedTweetFolders'], SavedTweetFolder.fromMap),
      likedTweets: _rows(json['likedTweets'], LikedTweet.fromMap),
      retweetFilters: _rows(json['retweetFilters'], UserFeedFilter.fromMap),
      replyFilters: _rows(json['replyFilters'], UserFeedFilter.fromMap),
      feedReadPositions: _rows(json['feedReadPositions'], FeedReadPositionRow.fromMap),
      accounts: _rows(json['accounts'], Account.fromMap),
      profileNotes: _rows(json['profileNotes'], ProfileNote.fromMap),
      antennas: _rows(json['antennas'], Antenna.fromMap),
      localPosts: _rows(json['localPosts'], LocalPost.fromMap),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'formatVersion': formatVersion,
      'exportedAt': exportedAt?.toIso8601String(),
      'appVersion': appVersion,
      'settings': settings,
      for (final section in pluginBackupSections()) section.jsonKey: _maps(pluginRows?[section.jsonKey]),
      'searchSubscriptions': _maps(searchSubscriptions),
      'subscriptions': _maps(userSubscriptions),
      'subscriptionGroups': _maps(subscriptionGroups),
      'subscriptionGroupMembers': _maps(subscriptionGroupMembers),
      'searchGroupMembers': _maps(searchGroupMembers),
      'tweets': _maps(tweets),
      'savedTweetFolders': _maps(savedTweetFolders),
      'likedTweets': _maps(likedTweets),
      'retweetFilters': _maps(retweetFilters),
      'replyFilters': _maps(replyFilters),
      'feedReadPositions': _maps(feedReadPositions),
      'accounts': _maps(accounts),
      'profileNotes': _maps(profileNotes),
      'antennas': _maps(antennas),
      'localPosts': _maps(localPosts),
    };
  }
}

/// A section the file does not carry stays null, which is how the import tells
/// "restore nothing here" from "restore an empty table".
List<T>? _rows<T>(Object? json, T Function(Map<String, Object?>) fromMap) {
  if (json is! List) {
    return null;
  }

  return json.whereType<Map>().map((row) => fromMap(Map<String, Object?>.from(row))).toList();
}

List<Map<String, dynamic>>? _maps(List<ToMappable>? rows) => rows?.map((row) => row.toMap()).toList();

/// What the file would restore, for the preview shown before anything is
/// written. Categories the file is silent about, and empty ones, are left out:
/// an empty result means there is nothing worth importing.
Map<BackupCategory, int> backupCounts(SettingsData data) {
  final counts = <BackupCategory, int?>{
    BackupCategory.settings: data.settings?.length,
    BackupCategory.subscriptions: _total([data.userSubscriptions, data.searchSubscriptions]),
    BackupCategory.groups: data.subscriptionGroups?.length,
    BackupCategory.groupMembers: _total([data.subscriptionGroupMembers, data.searchGroupMembers]),
    BackupCategory.savedPosts: data.tweets?.length,
    BackupCategory.folders: data.savedTweetFolders?.length,
    BackupCategory.likedPosts: data.likedTweets?.length,
    BackupCategory.filters: _total([data.retweetFilters, data.replyFilters]),
    BackupCategory.readPositions: data.feedReadPositions?.length,
    BackupCategory.accounts: data.accounts?.length,
    BackupCategory.profileNotes: data.profileNotes?.length,
    BackupCategory.antennas: data.antennas?.length,
    BackupCategory.localPosts: data.localPosts?.length,
    for (final section in pluginBackupSections()) section.category: data.pluginRows?[section.jsonKey]?.length,
  };

  return Map.fromEntries(
    counts.entries.where((entry) => (entry.value ?? 0) > 0).map((entry) => MapEntry(entry.key, entry.value!)),
  );
}

int? _total(List<List<Object>?> sections) {
  if (sections.every((rows) => rows == null)) {
    return null;
  }

  return sections.fold<int>(0, (total, rows) => total + (rows?.length ?? 0));
}

/// The rows to write, keyed by the table they belong in.
///
/// Reading positions are separate because restoring them is not obviously
/// harmless: a position from another device marks posts as already seen that
/// this reader never saw.
Map<String, List<ToMappable>> backupTables(SettingsData data, {required bool includeReadPositions}) {
  final sections = <String, List<ToMappable>?>{
    tableSearchSubscription: data.searchSubscriptions,
    tableSubscription: data.userSubscriptions,
    tableSubscriptionGroup: data.subscriptionGroups,
    tableSubscriptionGroupMember: data.subscriptionGroupMembers,
    tableSearchSubscriptionGroupMember: data.searchGroupMembers,
    tableSavedTweet: data.tweets,
    tableSavedTweetFolder: data.savedTweetFolders,
    tableLikedTweet: data.likedTweets,
    tableRetweetFilter: data.retweetFilters,
    tableReplyFilter: data.replyFilters,
    tableAccounts: data.accounts,
    tableProfileNote: data.profileNotes,
    tableAntenna: data.antennas,
    tableLocalPost: data.localPosts,
    if (includeReadPositions) tableFeedReadPosition: data.feedReadPositions,
    for (final section in pluginBackupSections()) section.table: data.pluginRows?[section.jsonKey],
  };

  return Map.fromEntries(
    sections.entries.where((entry) => entry.value != null).map((entry) => MapEntry(entry.key, entry.value!)),
  );
}
