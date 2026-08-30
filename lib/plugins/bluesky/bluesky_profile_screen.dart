import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/user.dart';
import 'package:xta/plugins/plugin_counts.dart';

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

class _BlueskyProfileScreenState extends State<BlueskyProfileScreen> {
  BlueskyProfile? _profile;
  Object? _error;
  bool _loading = true;
  var _tab = PluginProfileFeedTab.posts;
  final _feeds = {
    for (final tab in PluginProfileFeedTab.values) tab: _TabFeed(),
  };

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
    _load();
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
      await feed.refresh();
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

  @override
  Widget build(BuildContext context) {
    final title = _profile?.handle ?? widget.actor;

    return Scaffold(
      appBar: AppBar(title: Text(title.startsWith('did:') ? title : '@$title')),
      body: _body(context),
    );
  }

  Widget _body(BuildContext context) {
    final l10n = L10n.of(context);

    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    final error = _error;
    if (error != null) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: blueskyErrorMessage(l10n, error),
          onRetry: _load,
        ),
      );
    }

    final profile = _profile!;
    final following = context.read<BlueskyAccountsStore>().follows(
      profile.handle,
    );
    final feed = _feeds[_tab]!;
    final posts = feed.posts;
    final showMore = feed.cursor != null;
    final empty = posts.isEmpty && !feed.loading && feed.loaded;

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
      child: FeedListView(
        padding: const EdgeInsets.only(bottom: 24),
        itemCount:
            2 +
            (empty ? 1 : posts.length) +
            (feed.loadingMore || (feed.loading && posts.isEmpty) ? 1 : 0),
        itemBuilder: (context, index) {
          if (index == 0) {
            return Padding(
              padding: const EdgeInsets.all(16),
              child: BlueskyProfileCard(
                profile: profile,
                following: following,
                onFollowToggle: () => _toggleFollow(profile),
                onAddToGroup: () => _addToGroup(profile),
              ),
            );
          }
          if (index == 1) {
            return PluginProfileTabBar(
              selected: _tab,
              onSelected: _selectTab,
              tabs: PluginProfileFeedTab.values,
            );
          }
          if (empty) {
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
          final postIndex = index - 2;
          if (postIndex < posts.length) {
            final post = posts[postIndex];
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
    );
  }
}

/// Face, name, bio, counts, and a local Follow / Unfollow control.
class BlueskyProfileCard extends StatelessWidget {
  final BlueskyProfile profile;
  final bool following;
  final VoidCallback? onFollowToggle;
  final VoidCallback? onAddToGroup;

  const BlueskyProfileCard({
    super.key,
    required this.profile,
    required this.following,
    this.onFollowToggle,
    this.onAddToGroup,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final avatar = profile.avatarUrl;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            ClipOval(
              child: avatar == null
                  ? FallbackAvatar(
                      seed: profile.handle,
                      displayName: profile.displayName,
                      size: 64,
                      accent: theme.colorScheme.primary,
                    )
                  : ExtendedImage.network(
                      avatar,
                      width: 64,
                      height: 64,
                      fit: BoxFit.cover,
                      cacheWidth: (64 * MediaQuery.devicePixelRatioOf(context))
                          .ceil(),
                    ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    profile.displayName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleLarge!.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  Text(
                    '@${profile.handle}',
                    style: theme.textTheme.bodyMedium!.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        if (profile.description.trim().isNotEmpty) ...[
          const SizedBox(height: 14),
          Text(profile.description.trim(), style: theme.textTheme.bodyMedium),
        ],
        const SizedBox(height: 14),
        Wrap(
          spacing: 18,
          runSpacing: 6,
          children: [
            _count(
              context,
              compactCount(profile.followersCount),
              l10n.followers,
              onTap: () => _openFollows(context, BlueskyFollowsKind.followers),
            ),
            _count(
              context,
              compactCount(profile.followsCount),
              l10n.following,
              onTap: () => _openFollows(context, BlueskyFollowsKind.following),
            ),
            _count(context, compactCount(profile.postsCount), l10n.tweets),
          ],
        ),
        if (onFollowToggle != null || onAddToGroup != null) ...[
          const SizedBox(height: 18),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (onFollowToggle != null)
                FilledButton.tonalIcon(
                  onPressed: onFollowToggle,
                  icon: Icon(
                    following
                        ? Icons.person_remove_alt_1
                        : Icons.person_add_alt,
                  ),
                  label: Text(
                    following
                        ? l10n.plugin_bluesky_unfollow
                        : l10n.plugin_bluesky_follow,
                  ),
                ),
              if (onAddToGroup != null)
                OutlinedButton.icon(
                  onPressed: onAddToGroup,
                  icon: const Icon(Icons.group_add, size: 18),
                  label: Text(l10n.add_to_group),
                ),
            ],
          ),
        ],
      ],
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
    final theme = Theme.of(context);
    final text = Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: value,
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          TextSpan(
            text: ' $label',
            style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
          ),
        ],
      ),
      style: theme.textTheme.bodyMedium,
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
