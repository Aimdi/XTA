import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/user.dart';

/// What a failed Bluesky read should say.
String blueskyErrorMessage(L10n l10n, Object error) {
  if (error is! BlueskyException) {
    return l10n.plugin_bluesky_error_network;
  }
  return switch (error.kind) {
    BlueskyErrorKind.network => l10n.plugin_bluesky_error_network,
    BlueskyErrorKind.notFound => l10n.plugin_bluesky_error_not_found,
    BlueskyErrorKind.rateLimited => l10n.plugin_bluesky_error_rate_limited,
    BlueskyErrorKind.badResponse => l10n.plugin_bluesky_error_response,
  };
}

const _kBannerAspect = 500 / 1500;
const _kAvatarSize = 80.0;
const _kAvatarOverlap = 48.0;

class _ProfileTabSpec {
  final PluginProfileFeedTab tab;
  final IconData icon;
  final String Function(L10n l10n) label;

  const _ProfileTabSpec(this.tab, this.icon, this.label);
}

const _profileTabs = [
  _ProfileTabSpec(
    PluginProfileFeedTab.posts,
    Icons.wysiwyg_outlined,
    _tweetsLabel,
  ),
  _ProfileTabSpec(
    PluginProfileFeedTab.replies,
    Icons.mode_comment_outlined,
    _repliesLabel,
  ),
  _ProfileTabSpec(
    PluginProfileFeedTab.media,
    Icons.smart_display_outlined,
    _mediaLabel,
  ),
  _ProfileTabSpec(
    PluginProfileFeedTab.saved,
    Icons.bookmark_border,
    _savedLabel,
  ),
];

String _tweetsLabel(L10n l10n) => l10n.tweets;
String _repliesLabel(L10n l10n) => l10n.plugin_profile_replies;
String _mediaLabel(L10n l10n) => l10n.media;
String _savedLabel(L10n l10n) => l10n.saved;

/// One Bluesky profile and a page of its posts, looked up on the public AppView.
class BlueskyProfileScreen extends StatefulWidget {
  final String actor;

  const BlueskyProfileScreen({super.key, required this.actor});

  @override
  State<BlueskyProfileScreen> createState() => _BlueskyProfileScreenState();
}

class _TabFeed {
  List<BlueskyPost> posts = const [];
  String? cursor;
  var loaded = false;
  var loading = false;
  var loadingMore = false;
  var loadMoreBackedOff = false;
}

class _BlueskyProfileScreenState extends State<BlueskyProfileScreen>
    with TickerProviderStateMixin {
  BlueskyProfile? _profile;
  Object? _error;
  bool _loading = true;
  var _tab = PluginProfileFeedTab.posts;
  final _feeds = {
    for (final tab in PluginProfileFeedTab.values) tab: _TabFeed(),
  };
  final _nestedKey = GlobalKey<NestedScrollViewState>();
  late final TabController _tabController;

  String get _actor {
    final profile = _profile;
    if (profile == null) {
      return widget.actor;
    }
    return profile.did.isNotEmpty ? profile.did : profile.handle;
  }

  String _filterFor(PluginProfileFeedTab tab) => switch (tab) {
    PluginProfileFeedTab.posts => kBlueskyAuthorFeedPosts,
    PluginProfileFeedTab.replies => kBlueskyAuthorFeedReplies,
    PluginProfileFeedTab.media => kBlueskyAuthorFeedMedia,
    PluginProfileFeedTab.saved => kBlueskyAuthorFeedPosts,
  };

  List<BlueskyPost> _applyTabFilter(
    PluginProfileFeedTab tab,
    List<BlueskyPost> posts,
  ) {
    if (tab == PluginProfileFeedTab.replies) {
      return [
        for (final post in posts)
          if (post.isReply) post,
      ];
    }
    return posts;
  }

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _profileTabs.length, vsync: this);
    _tabController.addListener(_onTabController);
    _load();
  }

  @override
  void dispose() {
    _tabController.removeListener(_onTabController);
    _tabController.dispose();
    super.dispose();
  }

  void _onTabController() {
    if (_tabController.indexIsChanging) {
      return;
    }
    _selectTab(_profileTabs[_tabController.index].tab);
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      for (final feed in _feeds.values) {
        feed.posts = const [];
        feed.cursor = null;
        feed.loaded = false;
        feed.loading = false;
        feed.loadingMore = false;
        feed.loadMoreBackedOff = false;
      }
    });

    final client = context.read<BlueskyClient>();
    try {
      final profile = await client.getProfile(widget.actor);
      if (!mounted) {
        return;
      }
      _profile = profile;
      await _loadTab(PluginProfileFeedTab.posts, reset: true);
      if (mounted) {
        setState(() => _loading = false);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  Future<void> _selectTab(PluginProfileFeedTab tab) async {
    if (_tab == tab) {
      return;
    }
    setState(() => _tab = tab);
    if (tab == PluginProfileFeedTab.saved) {
      await _loadSaved();
      return;
    }
    final feed = _feeds[tab]!;
    if (!feed.loaded && !feed.loading) {
      await _loadTab(tab, reset: true);
    }
  }

  /// Device likes by this author. Cheap to rebuild from the in-memory store.
  Future<void> _loadSaved() async {
    final feed = _feeds[PluginProfileFeedTab.saved]!;
    setState(() => feed.loading = true);
    final likes = context.read<BlueskyLikesStore>();
    if (likes.state.isEmpty) {
      await likes.load();
    }
    if (!mounted) {
      return;
    }
    final profile = _profile;
    final posts = profile == null
        ? const <BlueskyPost>[]
        : blueskyLikesByAuthor(
            likes.likedPosts,
            did: profile.did,
            handle: profile.handle,
          );
    setState(() {
      feed.posts = posts;
      feed.cursor = null;
      feed.loaded = true;
      feed.loading = false;
    });
  }

  Future<void> _loadTab(PluginProfileFeedTab tab, {required bool reset}) async {
    if (tab == PluginProfileFeedTab.saved) {
      await _loadSaved();
      return;
    }
    final feed = _feeds[tab]!;
    if (feed.loading) {
      return;
    }
    setState(() => feed.loading = true);
    final client = context.read<BlueskyClient>();
    try {
      final page = await client.getAuthorFeed(_actor, filter: _filterFor(tab));
      if (!mounted) {
        return;
      }
      setState(() {
        feed.posts = _applyTabFilter(tab, page.posts);
        feed.cursor = page.cursor;
        feed.loaded = true;
        feed.loading = false;
      });
    } catch (e) {
      if (!mounted) {
        return;
      }
      setState(() {
        feed.loading = false;
        if (reset && !feed.loaded) {
          _error = e;
        }
      });
    }
  }

  Future<void> _loadMore() async {
    final feed = _feeds[_tab]!;
    final cursor = feed.cursor;
    if (cursor == null || feed.loadingMore) {
      return;
    }

    setState(() => feed.loadingMore = true);
    final client = context.read<BlueskyClient>();
    try {
      final page = await client.getAuthorFeed(
        _actor,
        cursor: cursor,
        filter: _filterFor(_tab),
      );
      if (!mounted) {
        return;
      }
      final seen = feed.posts.map((p) => p.uri).toSet();
      final extra = _applyTabFilter(_tab, page.posts);
      setState(() {
        feed.posts = [
          ...feed.posts,
          for (final post in extra)
            if (!seen.contains(post.uri)) post,
        ];
        feed.cursor = page.cursor;
        feed.loadingMore = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          feed.loadingMore = false;
          feed.loadMoreBackedOff = true;
        });
      }
    }
  }

  Future<void> _toggleFollow(BlueskyProfile profile) async {
    final accounts = context.read<BlueskyAccountsStore>();
    final feed = context.read<BlueskyFeedStore>();
    final subscriptions = context.read<SubscriptionsModel>();

    if (accounts.follows(profile.handle)) {
      await accounts.remove(profile.handle);
    } else {
      await accounts.add(profile.toAccount());
    }
    await subscriptions.reloadSubscriptions();
    if (mounted) {
      await feed.refresh(force: true);
      setState(() {});
    }
  }

  Future<void> _addToGroup(BlueskyProfile profile) async {
    final accounts = context.read<BlueskyAccountsStore>();
    final subscriptions = context.read<SubscriptionsModel>();
    final groupsModel = context.read<GroupsModel>();

    if (!accounts.follows(profile.handle)) {
      await accounts.add(profile.toAccount());
      await subscriptions.reloadSubscriptions();
    }
    if (!mounted) {
      return;
    }

    final user = subscriptionOf(profile.toAccount());
    final groups = await groupsModel.listGroupsForUser(user.id);
    if (!mounted) {
      return;
    }
    await pickUserGroups(
      context,
      user: user,
      followed: true,
      groupsForUser: groups,
    );
    if (mounted) {
      setState(() {});
    }
  }

  void _openMedia(String url) {
    final handle = _profile?.handle ?? widget.actor;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => TweetMediaView(
          initialIndex: 0,
          media: [createMediaFromUrl(url, null)],
          username: handle,
          tweetMedia: false,
        ),
      ),
    );
  }

  void _scrollToTop() {
    final nested = _nestedKey.currentState;
    final controller = nested?.outerController;
    if (controller == null || !controller.hasClients) {
      return;
    }
    controller.jumpTo(0);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    final error = _error;
    if (error != null) {
      final l10n = L10n.of(context);
      return Scaffold(
        appBar: AppBar(
          title: Text(_appBarTitle(widget.actor)),
        ),
        body: Padding(
          padding: const EdgeInsets.all(24),
          child: FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: blueskyErrorMessage(l10n, error),
            onRetry: _load,
          ),
        ),
      );
    }

    return Scaffold(body: _loadedBody(context, _profile!));
  }

  Widget _loadedBody(BuildContext context, BlueskyProfile profile) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final media = MediaQuery.of(context);
    final bannerHeight = media.size.width * _kBannerAspect;
    final following = context.read<BlueskyAccountsStore>().follows(
      profile.handle,
    );
    final feed = _feeds[_tab]!;
    final posts = feed.posts;
    final showMore = feed.cursor != null;
    final empty = posts.isEmpty && !feed.loading && feed.loaded;
    final busy = feed.loadingMore || (feed.loading && posts.isEmpty);

    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is UserScrollNotification) {
          feed.loadMoreBackedOff = false;
        }
        if (showMore &&
            !feed.loadingMore &&
            !feed.loadMoreBackedOff &&
            notification.metrics.pixels >=
                notification.metrics.maxScrollExtent - 400) {
          _loadMore();
        }
        return false;
      },
      child: NestedScrollView(
        key: _nestedKey,
        headerSliverBuilder: (context, innerBoxIsScrolled) {
          return [
            SliverAppBar(
              pinned: true,
              stretch: true,
              forceElevated: innerBoxIsScrolled,
              expandedHeight: bannerHeight + _kAvatarOverlap,
              backgroundColor: theme.colorScheme.surface,
              surfaceTintColor: Colors.transparent,
              centerTitle: false,
              automaticallyImplyLeading: false,
              leadingWidth: 56,
              leading: Center(
                child: _BannerButton(
                  icon: Icons.arrow_back,
                  tooltip: MaterialLocalizations.of(context).backButtonTooltip,
                  onPressed: () => Navigator.maybePop(context),
                ),
              ),
              title: innerBoxIsScrolled
                  ? Text(
                      profile.displayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    )
                  : null,
              actions: [
                Center(
                  child: _BannerButton(
                    icon: Icons.share,
                    tooltip: l10n.share_link,
                    onPressed: () => Share.share(
                      'https://bsky.app/profile/${profile.handle}',
                    ),
                  ),
                ),
                const SizedBox(width: 8),
              ],
              flexibleSpace: FlexibleSpaceBar(
                collapseMode: CollapseMode.pin,
                background: _ProfileBanner(
                  profile: profile,
                  bannerHeight: bannerHeight,
                  following: following,
                  onOpenBanner: profile.bannerUrl == null
                      ? null
                      : () => _openMedia(profile.bannerUrl!),
                  onOpenAvatar: profile.avatarUrl == null
                      ? null
                      : () => _openMedia(profile.avatarUrl!),
                  onFollowToggle: () => _toggleFollow(profile),
                  onAddToGroup: () => _addToGroup(profile),
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: BlueskyProfileCard(profile: profile),
            ),
            SliverPersistentHeader(
              pinned: true,
              delegate: _TabBarDelegate(
                tabBar: AnimatedBuilder(
                  animation: _tabController,
                  builder: (context, _) => TabBar(
                    controller: _tabController,
                    indicator: UnderlineTabIndicator(
                      borderRadius: const BorderRadius.vertical(
                        top: Radius.circular(2),
                      ),
                      borderSide: BorderSide(
                        width: 3,
                        color: theme.colorScheme.onSurface,
                      ),
                    ),
                    indicatorSize: TabBarIndicatorSize.label,
                    labelColor: theme.colorScheme.onSurface,
                    unselectedLabelColor: theme.colorScheme.onSurfaceVariant,
                    dividerColor: theme.colorScheme.surfaceBright.withAlpha(
                      150,
                    ),
                    onTap: (index) {
                      if (index == _tabController.index) {
                        _scrollToTop();
                      }
                    },
                    tabs: [
                      for (final (i, spec) in _profileTabs.indexed)
                        Tab(
                          child: _ProfileTabLabel(
                            icon: spec.icon,
                            label: spec.label(l10n),
                            selected: _tabController.index == i,
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ];
        },
        body: FeedListView(
          padding: const EdgeInsets.only(bottom: 24),
          physics: const AlwaysScrollableScrollPhysics(),
          itemCount: (empty ? 1 : posts.length) + (busy ? 1 : 0),
          itemBuilder: (context, index) {
            if (empty && index == 0) {
              return Padding(
                padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
                child: Text(
                  _tab == PluginProfileFeedTab.saved
                      ? l10n.plugin_bluesky_liked_empty
                      : l10n.plugin_bluesky_no_posts,
                  textAlign: TextAlign.center,
                ),
              );
            }
            if (index < posts.length) {
              final post = posts[index];
              return BlueskyPostCard(
                key: ValueKey(post.uri),
                post: post,
                showSourceBadge: false,
              );
            }
            return const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            );
          },
        ),
      ),
    );
  }
}

String _appBarTitle(String actor) => actor.startsWith('did:') ? actor : '@$actor';

/// Banner, overlapping avatar, and the follow / add-to-group row X puts
/// next to the photo — the identity text lives in [BlueskyProfileCard] below.
class _ProfileBanner extends StatelessWidget {
  final BlueskyProfile profile;
  final double bannerHeight;
  final bool following;
  final VoidCallback? onOpenBanner;
  final VoidCallback? onOpenAvatar;
  final VoidCallback onFollowToggle;
  final VoidCallback onAddToGroup;

  const _ProfileBanner({
    required this.profile,
    required this.bannerHeight,
    required this.following,
    required this.onOpenBanner,
    required this.onOpenAvatar,
    required this.onFollowToggle,
    required this.onAddToGroup,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final topPad = MediaQuery.paddingOf(context).top;
    final banner = profile.bannerUrl;

    return Stack(
      fit: StackFit.expand,
      children: [
        Column(
          children: [
            SizedBox(
              height: bannerHeight + topPad,
              width: double.infinity,
              child: _bannerImage(context, banner, theme),
            ),
            Expanded(child: ColoredBox(color: theme.colorScheme.surface)),
          ],
        ),
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          height: topPad + 56,
          child: const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Colors.black54, Colors.transparent],
              ),
            ),
          ),
        ),
        Positioned(
          left: 16,
          bottom: 0,
          child: _AvatarRing(
            profile: profile,
            onTap: onOpenAvatar,
          ),
        ),
        Positioned(
          left: 16 + _kAvatarSize + 24,
          right: 16,
          bottom: 4,
          child: Align(
            alignment: Alignment.centerRight,
            child: _FollowActions(
              following: following,
              followLabel: following
                  ? l10n.plugin_bluesky_unfollow
                  : l10n.plugin_bluesky_follow,
              groupLabel: l10n.add_to_group,
              onFollowToggle: onFollowToggle,
              onAddToGroup: onAddToGroup,
            ),
          ),
        ),
      ],
    );
  }

  Widget _bannerImage(BuildContext context, String? banner, ThemeData theme) {
    final fallback = ColoredBox(
      color: theme.colorScheme.primary.withValues(alpha: 0.35),
    );
    if (banner == null) {
      return fallback;
    }
    final image = ExtendedImage.network(
      banner,
      fit: BoxFit.cover,
      width: double.infinity,
      height: double.infinity,
      cacheWidth: (MediaQuery.sizeOf(context).width *
              MediaQuery.devicePixelRatioOf(context))
          .ceil(),
    );
    if (onOpenBanner == null) {
      return image;
    }
    return GestureDetector(onTap: onOpenBanner, child: image);
  }
}

class _AvatarRing extends StatelessWidget {
  final BlueskyProfile profile;
  final VoidCallback? onTap;

  const _AvatarRing({required this.profile, this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final avatar = profile.avatarUrl;
    final face = avatar == null
        ? FallbackAvatar(
            seed: profile.handle,
            displayName: profile.displayName,
            size: _kAvatarSize,
            accent: theme.colorScheme.primary,
          )
        : ExtendedImage.network(
            avatar,
            width: _kAvatarSize,
            height: _kAvatarSize,
            fit: BoxFit.cover,
            cacheWidth: (_kAvatarSize * MediaQuery.devicePixelRatioOf(context))
                .ceil(),
          );

    final ring = CircleAvatar(
      radius: _kAvatarSize / 2 + 4,
      backgroundColor: theme.colorScheme.surface,
      child: ClipOval(child: face),
    );

    if (onTap == null) {
      return ring;
    }
    return GestureDetector(onTap: onTap, child: ring);
  }
}

class _FollowActions extends StatelessWidget {
  final bool following;
  final String followLabel;
  final String groupLabel;
  final VoidCallback onFollowToggle;
  final VoidCallback onAddToGroup;

  const _FollowActions({
    required this.following,
    required this.followLabel,
    required this.groupLabel,
    required this.onFollowToggle,
    required this.onAddToGroup,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return FittedBox(
      fit: BoxFit.scaleDown,
      alignment: Alignment.centerRight,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton.outlined(
            onPressed: onAddToGroup,
            tooltip: groupLabel,
            visualDensity: VisualDensity.compact,
            icon: const Icon(Icons.group_add, size: 18),
          ),
          const SizedBox(width: 8),
          following
              ? OutlinedButton(
                  onPressed: onFollowToggle,
                  style: OutlinedButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    shape: const StadiumBorder(),
                  ),
                  child: Text(followLabel),
                )
              : FilledButton(
                  onPressed: onFollowToggle,
                  style: FilledButton.styleFrom(
                    backgroundColor: theme.colorScheme.onSurface,
                    foregroundColor: theme.colorScheme.surface,
                    visualDensity: VisualDensity.compact,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    shape: const StadiumBorder(),
                  ),
                  child: Text(followLabel),
                ),
        ],
      ),
    );
  }
}

/// Name, handle, bio, joined date, and tappable follow counts.
class BlueskyProfileCard extends StatelessWidget {
  final BlueskyProfile profile;

  const BlueskyProfileCard({super.key, required this.profile});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final createdAt = profile.createdAt;
    final bio = profile.description.trim();
    const metadataStyle = TextStyle(fontSize: 12.5);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            profile.displayName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
          ),
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              '@${profile.handle}',
              style: TextStyle(
                fontSize: 14,
                color: theme.brightness == Brightness.dark
                    ? Colors.white70
                    : Colors.black54,
              ),
            ),
          ),
          if (bio.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(bio, style: theme.textTheme.bodyMedium),
            ),
          if (createdAt != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                children: [
                  Icon(
                    Icons.calendar_today_outlined,
                    size: 14,
                    color: theme.hintColor,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    l10n.joined(DateFormat('MMMM yyyy').format(createdAt)),
                    style: metadataStyle,
                  ),
                ],
              ),
            ),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _count(
                  context,
                  compactCount(profile.followsCount),
                  l10n.following.toLowerCase(),
                  onTap: () => _openFollows(context, BlueskyFollowsKind.following),
                ),
                const SizedBox(width: 8),
                _count(
                  context,
                  compactCount(profile.followersCount),
                  l10n.followers.toLowerCase(),
                  onTap: () => _openFollows(context, BlueskyFollowsKind.followers),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _openFollows(
    BuildContext context,
    BlueskyFollowsKind kind,
  ) async {
    final actor = profile.did.isNotEmpty ? profile.did : profile.handle;
    if (actor.isEmpty) {
      return;
    }
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => BlueskyFollowsScreen(actor: actor, kind: kind),
      ),
    );
  }

  Widget _count(
    BuildContext context,
    String value,
    String label, {
    VoidCallback? onTap,
  }) {
    const metadataStyle = TextStyle(fontSize: 12.5);
    final text = Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: value,
            style: metadataStyle.copyWith(fontWeight: FontWeight.w700),
          ),
          TextSpan(text: ' $label', style: metadataStyle),
        ],
      ),
    );

    if (onTap == null) {
      return text;
    }

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(4),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: text,
      ),
    );
  }
}

/// Circular translucent control over the banner, matching X's profile chrome.
class _BannerButton extends StatelessWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;

  const _BannerButton({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.black38,
      shape: const CircleBorder(),
      clipBehavior: Clip.antiAlias,
      child: IconButton(
        icon: Icon(icon, size: 20, color: Colors.white),
        tooltip: tooltip,
        onPressed: onPressed,
        style: IconButton.styleFrom(
          minimumSize: const Size(40, 40),
          maximumSize: const Size(40, 40),
          padding: EdgeInsets.zero,
          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        ),
      ),
    );
  }
}

/// X-style profile tab: symbol always, label only while selected.
class _ProfileTabLabel extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;

  const _ProfileTabLabel({
    required this.icon,
    required this.label,
    required this.selected,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 22),
        if (selected) ...[
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              label,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ],
    );
  }
}

class _TabBarDelegate extends SliverPersistentHeaderDelegate {
  final Widget tabBar;

  _TabBarDelegate({required this.tabBar});

  @override
  double get minExtent => 48;

  @override
  double get maxExtent => 48;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) {
    return Material(
      color: Theme.of(context).colorScheme.surface,
      child: tabBar,
    );
  }

  @override
  bool shouldRebuild(covariant _TabBarDelegate oldDelegate) =>
      tabBar != oldDelegate.tabBar;
}
