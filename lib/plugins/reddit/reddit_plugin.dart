import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/settings/backup_rows.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/plugins/reddit/reddit_interleaved.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/plugins/reddit/reddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_feed_list.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart';
import 'package:xta/plugins/reddit/reddit_search_screen.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_settings_screen.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';

/// Reddit, read-only, in the spirit of Stealth.
///
/// Account-free by default and account-free for good if the reader wants it:
/// signing in is offered because it is the most reliable route Reddit leaves
/// open, never required, and asks for read scopes only. Nothing is ever written
/// back to Reddit — no posting, no voting, no subscribing.
///
/// A reimplementation against Reddit's documented API rather than a port of
/// Stealth itself, which is GPLv3 Kotlin — see docs/specs/reddit-plugin.md.
class RedditPlugin extends XtaPlugin with SubscriptionSource {
  RedditPlugin();

  @override
  String get id => pluginIdReddit;

  @override
  String get enabledPrefKey => optionPluginRedditEnabled;

  @override
  IconData get icon => Icons.reddit;

  @override
  PluginCategory get category => PluginCategory.communities;

  @override
  Color get brandColor => const Color(0xFFFF4500);

  @override
  String? get homeTabPrefKey => optionPluginRedditShowTab;

  @override
  String title(BuildContext context) => L10n.of(context).plugin_reddit_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_reddit_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdReddit,
      (c) => L10n.of(c).plugin_reddit_title,
      const Icon(Icons.reddit_outlined),
      const Icon(Icons.reddit),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return RedditScreen(scrollController: scrollController);
  }

  @override
  Widget feedStripScreen({required ScrollController scrollController}) {
    // Compact listing with Reddit-specific app-bar actions — not the full
    // bottom-tab shell that would nest another nav strip under Home.
    return RedditFeedList(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const RedditSettingsScreen();

  @override
  bool get supportsSearch => true;

  @override
  Future<void> openSearch(BuildContext context, {String? initialQuery}) {
    return Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => RedditSearchScreen(initialQuery: initialQuery),
      ),
    );
  }

  @override
  List<String> get tables => const [
    tableRedditSubscription,
    tableRedditLocalVote,
  ];

  @override
  String get subscriptionTable => tableRedditSubscription;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) =>
      RedditSubscription.fromMap(row);

  @override
  bool owns(Subscription subscription) => subscription is RedditSubscription;

  @override
  String subtitleFor(Subscription subscription) => 'r/${subscription.name}';

  /// A subreddit's picture is not a URL this app holds — it is fetched and
  /// cached separately, and drawn from the name.
  @override
  GroupMemberPreview previewOf(Subscription subscription) => GroupMemberPreview(
    id: subscription.id,
    name: subscription.name,
    subreddit: subscription.name,
  );

  @override
  Widget avatarFor(Subscription subscription, {double size = 40}) =>
      RedditAvatar(name: 'r/${subscription.name}', size: size);

  @override
  Widget Function()? destinationFor(Subscription subscription) =>
      () => RedditListingScreen.subreddit(subscription.name);

  @override
  Future<void> reloadFromDatabase(BuildContext context) =>
      context.read<RedditSubredditsStore>().load(force: true);

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) =>
      context.read<RedditSubredditsStore>().remove(subscription.name);

  @override
  Future<List<InterleavedItem>> interleavedPosts(
    BuildContext context,
    List<String> ids,
  ) => loadRedditInterleaved(context, ids);

  @override
  bool inHomeFeed(BuildContext context) =>
      redditInHomeFeed(PrefService.of(context, listen: false));

  @override
  List<String> homeFeedIds(BuildContext context) =>
      redditHomeSubreddits(context);

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'redditSubscriptions',
      table: tableRedditSubscription,
      category: BackupCategory.subreddits,
      fromMap: RedditSubscription.fromMap,
    ),
    PluginBackupSection(
      jsonKey: 'redditLocalVotes',
      table: tableRedditLocalVote,
      category: BackupCategory.upvotes,
      fromMap: RedditLocalVote.fromMap,
    ),
  ];

  @override
  List<String> get caches => const [redditIconsCacheName];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    // The sign-in and the client id go with everything else: leaving
    // credentials behind is the part of "uninstall" that would matter most.
    await prefs.set(optionPluginRedditSubreddits, '[]');
    await prefs.set(optionPluginRedditClientId, '');
    await prefs.set(optionPluginRedditRefreshToken, '');
    await prefs.set(optionPluginRedditSource, redditSourceAuto);
    await prefs.set(optionPluginRedditSort, redditSortHot);
    await prefs.set(optionPluginRedditTimeFilter, redditTimeFilterDay);
    await prefs.set(optionPluginRedditFeedMode, redditFeedModeFollowing);
    await prefs.set(optionPluginRedditSelectedSubreddit, '');
    await prefs.set(optionPluginRedditNsfwMode, redditNsfwModeTap);
    await prefs.set(optionPluginRedditSavedPosts, '[]');
    await prefs.set(optionPluginRedditInHomeFeed, false);
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    // Read before the await: afterwards this context may be gone, and these
    // stores outlive the screen either way.
    final client = context.read<RedditClient>();
    final votes = context.read<RedditVotesStore>();
    final saved = context.read<RedditSavedStore>();

    await context.read<RedditSubredditsStore>().load(force: true);
    client.forgetToken();
    // The rows are deleted by [tables], but the set that was read from them is
    // still in memory — an uninstall that left the arrows lit would look like
    // the votes had been kept.
    votes.update(const <String>{});
    saved.update(const <RedditPost>[]);
  }
}
