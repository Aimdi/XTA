import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';

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

class _BlueskyProfileScreenState extends State<BlueskyProfileScreen> {
  BlueskyProfile? _profile;
  List<BlueskyPost> _posts = const [];
  String? _cursor;
  Object? _error;
  bool _loading = true;
  bool _loadingMore = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _cursor = null;
    });

    final client = context.read<BlueskyClient>();
    try {
      final profile = await client.getProfile(widget.actor);
      final feed = await client.getAuthorFeed(profile.did.isNotEmpty ? profile.did : profile.handle);
      if (mounted) {
        setState(() {
          _profile = profile;
          _posts = feed.posts;
          _cursor = feed.cursor;
          _loading = false;
        });
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

  Future<void> _loadMore() async {
    final profile = _profile;
    final cursor = _cursor;
    if (profile == null || cursor == null || _loadingMore) {
      return;
    }

    setState(() => _loadingMore = true);
    final client = context.read<BlueskyClient>();
    try {
      final feed = await client.getAuthorFeed(
        profile.did.isNotEmpty ? profile.did : profile.handle,
        cursor: cursor,
      );
      if (!mounted) return;
      final seen = _posts.map((p) => p.uri).toSet();
      setState(() {
        _posts = [
          ..._posts,
          for (final post in feed.posts)
            if (!seen.contains(post.uri)) post,
        ];
        _cursor = feed.cursor;
        _loadingMore = false;
      });
    } catch (_) {
      // Stop, don't strobe: the cursor stays set on failure, and the scroll
      // listener refires _loadMore on every notification — so one 429 became a
      // stream of failing requests against a rate-limited host, invisible to
      // the reader. Backing off until the next real scroll gesture ends the
      // loop; the next deliberate scroll tries again.
      if (mounted) {
        setState(() {
          _loadingMore = false;
          _loadMoreBackedOff = true;
        });
      }
    }
  }

  /// Set when the last next-page fetch failed, cleared by a fresh user scroll,
  /// so a failing endpoint is asked once per gesture rather than once per
  /// scroll notification.
  bool _loadMoreBackedOff = false;

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
    final following = context.read<BlueskyAccountsStore>().follows(profile.handle);
    final showMore = _cursor != null;

    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is UserScrollNotification) {
          _loadMoreBackedOff = false;
        }
        if (showMore &&
            !_loadingMore &&
            !_loadMoreBackedOff &&
            notification.metrics.pixels >= notification.metrics.maxScrollExtent - 400) {
          _loadMore();
        }
        return false;
      },
      child: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: BlueskyProfileCard(
              profile: profile,
              following: following,
              onFollowToggle: () => _toggleFollow(profile),
            ),
          ),
          for (final post in _posts) BlueskyPostCard(key: ValueKey(post.uri), post: post, showSourceBadge: false),
          if (_loadingMore)
            const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            ),
        ],
      ),
    );
  }
}

/// Face, name, bio, counts, and a local Follow / Unfollow control.
class BlueskyProfileCard extends StatelessWidget {
  final BlueskyProfile profile;
  final bool following;
  final VoidCallback? onFollowToggle;

  const BlueskyProfileCard({
    super.key,
    required this.profile,
    required this.following,
    this.onFollowToggle,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final numbers = NumberFormat.compact();
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
                      accent: theme.colorScheme.primary)
                  : ExtendedImage.network(avatar,
                      width: 64,
                      height: 64,
                      fit: BoxFit.cover,
                      cacheWidth: (64 * MediaQuery.devicePixelRatioOf(context)).ceil()),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(profile.displayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w700)),
                  Text('@${profile.handle}',
                      style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant)),
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
              numbers.format(profile.followersCount),
              l10n.followers,
              onTap: () => _openFollows(context, BlueskyFollowsKind.followers),
            ),
            _count(
              context,
              numbers.format(profile.followsCount),
              l10n.following,
              onTap: () => _openFollows(context, BlueskyFollowsKind.following),
            ),
            _count(context, numbers.format(profile.postsCount), l10n.tweets),
          ],
        ),
        if (onFollowToggle != null) ...[
          const SizedBox(height: 18),
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.tonalIcon(
              onPressed: onFollowToggle,
              icon: Icon(following ? Icons.person_remove_alt_1 : Icons.person_add_alt),
              label: Text(following ? l10n.plugin_bluesky_unfollow : l10n.plugin_bluesky_follow),
            ),
          ),
        ],
      ],
    );
  }

  Future<void> _openFollows(BuildContext context, BlueskyFollowsKind kind) async {
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

  Widget _count(BuildContext context, String value, String label, {VoidCallback? onTap}) {
    final theme = Theme.of(context);
    final text = Text.rich(
      TextSpan(children: [
        TextSpan(text: value, style: const TextStyle(fontWeight: FontWeight.w700)),
        TextSpan(text: ' $label', style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
      ]),
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
