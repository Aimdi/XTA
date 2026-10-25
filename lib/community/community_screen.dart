import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:xta/client/client.dart';
import 'package:xta/community/community_members_screen.dart';
import 'package:xta/community/community_store.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/ui/reader_tab_view.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/formatting_locale.dart';

class CommunityScreenArguments {
  final String id;

  /// Opens on the About tab, as an `/about` link asks.
  final bool about;

  const CommunityScreenArguments(this.id, {this.about = false});

  @override
  String toString() => 'CommunityScreenArguments{id: $id, about: $about}';
}

class CommunityScreen extends StatelessWidget {
  const CommunityScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final args = ModalRoute.of(context)!.settings.arguments as CommunityScreenArguments;
    return CommunityPage(id: args.id, about: args.about);
  }
}

/// An X community, read-only: its header, its Top posts and its About tab.
class CommunityPage extends StatefulWidget {
  final String id;
  final bool about;

  /// Stand-ins for X, for tests.
  final Future<Community> Function(String id)? readCommunity;
  final Future<TweetStatus> Function(String id, String? cursor)? readPosts;

  const CommunityPage({super.key, required this.id, this.about = false, this.readCommunity, this.readPosts});

  @override
  State<CommunityPage> createState() => _CommunityPageState();
}

class _CommunityPageState extends State<CommunityPage> {
  late final CommunityStore _store;
  final TweetFeedController _feed = TweetFeedController();

  @override
  void initState() {
    super.initState();
    _store = CommunityStore(widget.id, read: widget.readCommunity)..load();
  }

  @override
  void dispose() {
    _store.destroy();
    _feed.dispose();
    super.dispose();
  }

  Future<TweetPageResult> _loadPosts(String? cursor) async {
    final read = widget.readPosts ?? (id, cursor) => Twitter.getCommunityTweets(id, cursor: cursor);
    final page = await read(widget.id, cursor);
    return (chains: page.chains, nextCursor: page.cursorBottom);
  }

  @override
  Widget build(BuildContext context) {
    return XtaSystemBars(
      child: TripleBuilder<CommunityStore, Community>(
        store: _store,
        builder: (context, triple) {
          if (triple.isLoading) return const ProfileLoadingSkeleton();
          if (triple.error != null) {
            return Scaffold(
              appBar: AppBar(),
              body: ReaderFailureNotice(
                error: triple.error,
                contextMessage: L10n.of(context).unable_to_load_the_community,
                recoverAutomatically: false,
                onRetry: _store.load,
              ),
            );
          }
          return _CommunityBody(community: triple.state, about: widget.about, feed: _feed, loadPosts: _loadPosts);
        },
      ),
    );
  }
}

class _CommunityBody extends StatefulWidget {
  final Community community;
  final bool about;
  final TweetFeedController feed;
  final TweetPageLoader loadPosts;

  const _CommunityBody({required this.community, required this.about, required this.feed, required this.loadPosts});

  @override
  State<_CommunityBody> createState() => _CommunityBodyState();
}

class _CommunityBodyState extends State<_CommunityBody> with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 2, vsync: this, initialIndex: widget.about ? 1 : 0);

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final community = widget.community;
    return Scaffold(
      body: NestedScrollView(
        headerSliverBuilder: (context, _) => [
          SliverAppBar(pinned: true, title: Text(community.name, maxLines: 1, overflow: TextOverflow.ellipsis)),
          SliverToBoxAdapter(child: CommunityHeader(community: community)),
          SliverPersistentHeader(
            pinned: true,
            delegate: ProfileTabsDelegate(
              ProfileTabsBar(
                controller: _tabs,
                tabs: [
                  Tab(text: l10n.popular),
                  Tab(text: l10n.community_about),
                ],
              ),
            ),
          ),
        ],
        body: TweetContextScope(
          child: ReaderTabView(
            controller: _tabs,
            children: [
              PaginatedTweetList(
                feed: widget.feed,
                loadPage: widget.loadPosts,
                username: null,
                firstPageErrorPrefix: l10n.unable_to_load_the_tweets,
                newPageErrorPrefix: l10n.unable_to_load_the_next_page_of_tweets,
                emptyMessage: l10n.no_results,
              ),
              CommunityAbout(community: community),
            ],
          ),
        ),
      ),
    );
  }
}

/// Banner, name, topic and the member count, which opens the members.
class CommunityHeader extends StatelessWidget {
  final Community community;

  const CommunityHeader({super.key, required this.community});

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final members = L10n.of(context).group_member_count(community.memberCount);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (community.bannerUrl case final banner?)
          ProfileBanner(
            uri: banner,
            height: (width / 3).clamp(96.0, kProfileBannerHeight),
            semanticLabel: community.name,
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(kTweetSpace4, kTweetSpace3, kTweetSpace4, kTweetSpace2),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(community.name, style: Theme.of(context).textTheme.titleLarge),
              if (community.topic case final topic?) Text(topic, style: tweetMetadataStyle(context)),
              if (community.description case final description? when description.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: kTweetSpace2),
                  child: Text(description, maxLines: 3, overflow: TextOverflow.ellipsis),
                ),
            ],
          ),
        ),
        InkWell(
          onTap: () =>
              Navigator.push(context, MaterialPageRoute(builder: (_) => CommunityMembersScreen(community: community))),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: kTweetSpace4, vertical: kTweetSpace2),
            child: Row(
              children: [
                for (final url in community.memberAvatarUrls.take(5))
                  Padding(
                    padding: const EdgeInsetsDirectional.only(end: kTweetSpace1),
                    child: UserAvatar(uri: url, size: 24),
                  ),
                if (community.memberAvatarUrls.isNotEmpty) const SizedBox(width: kTweetSpace1),
                Expanded(child: Text(members, style: tweetMetadataStyle(context))),
                Icon(Icons.chevron_right, color: tweetSecondaryColor(context)),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// The description in full, the rules and when the community was created.
class CommunityAbout extends StatelessWidget {
  final Community community;

  const CommunityAbout({super.key, required this.community});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final createdAt = community.createdAt;
    return ListView(
      padding: const EdgeInsets.all(kTweetSpace4),
      children: [
        if (community.description case final description? when description.isNotEmpty) SelectableText(description),
        if (community.rules.isNotEmpty) ...[
          const SizedBox(height: kTweetSpace6),
          Text(l10n.community_rules, style: Theme.of(context).textTheme.titleMedium),
          for (final (index, rule) in community.rules.indexed)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Text('${index + 1}', style: Theme.of(context).textTheme.titleMedium),
              title: Text(rule),
            ),
        ],
        if (createdAt != null) ...[
          const SizedBox(height: kTweetSpace4),
          Text(
            l10n.community_created(DateFormat.yMMMd(formattingLocale(context)).format(createdAt)),
            style: tweetMetadataStyle(context),
          ),
        ],
      ],
    );
  }
}
