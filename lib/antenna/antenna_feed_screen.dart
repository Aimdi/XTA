import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/antenna/antenna_query.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet_context_scope.dart';

class AntennaFeedArguments {
  final Antenna antenna;

  AntennaFeedArguments(this.antenna);
}

class AntennaFeedScreen extends StatefulWidget {
  const AntennaFeedScreen({super.key});

  @override
  State<AntennaFeedScreen> createState() => _AntennaFeedScreenState();
}

class _AntennaFeedScreenState extends State<AntennaFeedScreen> {
  Antenna? _antenna;
  late final TweetFeedController _feed;
  Set<String> _followedIds = const {};

  @override
  void initState() {
    super.initState();
    _feed = TweetFeedController();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final antenna = (ModalRoute.of(context)!.settings.arguments as AntennaFeedArguments).antenna;
    if (_antenna?.id == antenna.id) {
      return;
    }
    _antenna = antenna;
    if (antenna.scope == 'following') {
      _followedIds = context.read<SubscriptionsModel>().state.whereType<UserSubscription>().map((e) => e.id).toSet();
    }
    _feed.controller.refresh();
  }

  @override
  void dispose() {
    _feed.dispose();
    super.dispose();
  }

  Future<TweetPageResult> _loadPage(String? cursor) async {
    final antenna = _antenna!;
    final query = buildAntennaSearchQuery(antenna);
    if (query.isEmpty) {
      return (chains: <TweetChain>[], nextCursor: null);
    }

    final result = await Twitter.searchTweets(query, true, product: 'Latest', cursor: cursor);
    var chains = result.chains;
    if (antenna.scope == 'following') {
      chains = filterAntennaFollowingScope(chains, _followedIds);
    }
    return (chains: chains, nextCursor: result.cursorBottom);
  }

  @override
  Widget build(BuildContext context) {
    final antenna = _antenna;
    final l10n = L10n.of(context);

    if (antenna == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      appBar: AppBar(title: Text(antenna.name)),
      body: TweetContextScope(
        child: PaginatedTweetList(
          feed: _feed,
          loadPage: _loadPage,
          username: null,
          firstPageErrorPrefix: l10n.unable_to_load_the_tweets_for_the_feed,
          newPageErrorPrefix: l10n.unable_to_load_the_next_page_of_tweets,
          emptyMessage: l10n.no_posts_match_your_search,
        ),
      ),
    );
  }
}
