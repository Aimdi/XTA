import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
import 'package:xta/plugins/reddit/reddit_read_session.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/reader_source_text.dart';

MixedEntry redditMixedEntry(RedditPost post) =>
    MixedEntry(identity: 'reddit:${post.id}', item: post, date: post.createdAt);

/// A followed subreddit, or Popular or All, sorted the way the Reddit tab is.
class RedditSubredditMixedSource extends MixedSourceKind {
  const RedditSubredditMixedSource();

  @override
  String get id => 'reddit.subreddit';

  @override
  String get pluginId => pluginIdReddit;

  @override
  IconData get icon => Icons.forum_outlined;

  @override
  MixedSourceInput get input => MixedSourceInput.choice;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_subreddit;

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async {
    final l10n = L10n.of(context);
    return [
      for (final name in context.read<RedditSubredditsStore>().state)
        MixedFeedSource(kind: id, value: name, label: 'r/$name'),
      MixedFeedSource(kind: id, value: 'popular', label: l10n.plugin_reddit_feed_popular),
      MixedFeedSource(kind: id, value: 'all', label: l10n.plugin_reddit_feed_all),
    ];
  }

  /// The listing settings and whether the reader is signed in, never the token itself.
  @override
  String signature(BuildContext context, MixedFeedSource source) {
    final prefs = PrefService.of(context, listen: false);
    return [
      prefs.get<String>(optionPluginRedditSource) ?? '',
      (prefs.get<String>(optionPluginRedditRefreshToken) ?? '').isNotEmpty,
      storedRedditSort(prefs).name,
      storedRedditTimeFilter(prefs).name,
      storedRedditNsfwMode(prefs).name,
    ].join('\n');
  }

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final prefs = PrefService.of(context, listen: false);
    final client = context.read<RedditClient>();
    final auth = context.read<RedditAuth>();
    // One session per source: resolving can mint a token, so it is not repeated for every page.
    RedditReadSession? session;
    return MixedFunctionReader((cursor) async {
      session ??= await RedditReadSession.resolve(prefs: prefs, auth: auth);
      final listing = await session!.fetchSubreddit(
        client,
        source.value,
        sort: storedRedditSort(prefs),
        timeFilter: storedRedditTimeFilter(prefs),
        after: cursor as String?,
      );
      final posts = filterRedditPosts(listing.posts, nsfwMode: storedRedditNsfwMode(prefs));
      final next = listing.after;
      return MixedPage([
        for (final post in posts)
          if (!post.stickied) redditMixedEntry(post),
      ], next: next == null || next.isEmpty || next == cursor ? null : next);
    });
  }

  @override
  Widget card(BuildContext context, MixedEntry entry) => RedditPostCard(post: entry.item as RedditPost);

  @override
  String filterText(MixedEntry entry) => redditFilterText(entry.item as RedditPost);
}

const redditMixedSourceKinds = <MixedSourceKind>[RedditSubredditMixedSource()];
