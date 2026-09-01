import 'package:flutter/material.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quax/client/client.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/repository.dart';
import 'package:quax/database/timeline_cache.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/profile/profile.dart';
import 'package:quax/tweet/paginated_tweet_list.dart';
import 'package:quax/ui/errors.dart';
import 'package:quax/user.dart';

class ProfileTweets extends StatefulWidget {
  final UserWithExtra user;
  final String type;
  final bool includeReplies;
  final List<String> pinnedTweets;
  final BasePrefService pref;

  const ProfileTweets({
    super.key,
    required this.user,
    required this.type,
    required this.includeReplies,
    required this.pinnedTweets,
    required this.pref,
  });

  @override
  State<ProfileTweets> createState() => _ProfileTweetsState();
}

class _ProfileTweetsState extends State<ProfileTweets>
    with AutomaticKeepAliveClientMixin<ProfileTweets> {
  static final log = Logger('ProfileTweets');
  static const int pageSize = 20;

  final TweetFeedController _feed = TweetFeedController();
  int _loadTweetsCounter = 0;
  bool _bypassCache = false;

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _feed.dispose();
    super.dispose();
  }

  Future<TweetStatus> _load(String? cursor) => Twitter.getTweets(
    widget.user.idStr!,
    widget.type,
    widget.pinnedTweets,
    cursor: cursor,
    count: pageSize,
    includeReplies: widget.includeReplies,
    getTweetsCounter: () => _loadTweetsCounter,
    incrementTweetsCounter: () => ++_loadTweetsCounter,
  );

  Future<TweetStatus> _loadFirstPage() async {
    final key = TimelineCache.profileKey(
      widget.user.idStr!,
      widget.type,
      includeReplies: widget.includeReplies,
    );
    final cache = TimelineCache(await Repository.writable());
    if (!_bypassCache) {
      final cached = await cache.read(key, maxAge: profileCacheMaxAge);
      if (cached != null) return cached;
    }
    _bypassCache = false;

    try {
      final result = await _load(null);
      await cache.write(key, result);
      return result;
    } catch (error) {
      final stale = await cache.readStale(key);
      if (stale == null) rethrow;
      log.info(
        'Showing the cached profile timeline for ${widget.user.idStr} after $error',
      );
      return stale;
    }
  }

  Future<TweetPageResult> _fetchPage(String? cursor) async {
    final result = cursor == null
        ? await _loadFirstPage()
        : await _load(cursor);
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Consumer<TweetContextState>(
      builder: (context, model, child) {
        if (model.hideSensitive && (widget.user.possiblySensitive ?? false)) {
          return EmojiErrorWidget(
            emoji: '🍆🙈🍆',
            message: L10n.current.possibly_sensitive,
            errorMessage: L10n.current.possibly_sensitive_profile,
            onRetry: () async => model.setHideSensitive(false),
            retryText: L10n.current.yes_please,
          );
        }

        return PaginatedTweetList(
          feed: _feed,
          loadPage: _fetchPage,
          username: widget.user.screenName,
          onRefresh: () async => _bypassCache = true,
          firstPageErrorPrefix: L10n.of(context).unable_to_load_the_tweets,
          newPageErrorPrefix: L10n.of(
            context,
          ).unable_to_load_the_next_page_of_tweets,
          emptyMessage: L10n.of(context).could_not_find_any_tweets_by_this_user,
        );
      },
    );
  }
}
