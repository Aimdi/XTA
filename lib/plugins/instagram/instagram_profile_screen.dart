import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_errors.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_post_card.dart';
import 'package:xta/plugins/instagram/instagram_store.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/plugins/plugin_counts.dart';

class InstagramProfileScreen extends StatefulWidget {
  final String handle;

  const InstagramProfileScreen({super.key, required this.handle});

  @override
  State<InstagramProfileScreen> createState() => _InstagramProfileScreenState();
}

class _InstagramProfileScreenState extends State<InstagramProfileScreen> {
  late final InstagramProfileStore _profileStore;
  late final InstagramFeedStore _feed;

  @override
  void initState() {
    super.initState();
    final client = context.read<InstagramClient>();
    _profileStore = InstagramProfileStore(client, widget.handle);
    _feed = InstagramFeedStore(({cursor}) async {
      if (cursor == null) {
        final page = await client.profileMedia(widget.handle);
        if (!client.hasSession) {
          return InstagramItemPage(posts: page.posts, hasMore: false);
        }
        return page;
      }
      if (!client.hasSession) {
        return const InstagramItemPage(posts: [], hasMore: false);
      }
      final profile =
          _profileStore.state ?? await client.profile(widget.handle);
      return client.userFeed(pk: profile.id, cursor: cursor);
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    await _profileStore.load();
    if (!mounted) return;
    final profile = _profileStore.state;
    if (profile == null) return;
    await context.read<InstagramSearchHistoryStore>().remember(
      profile.username,
    );
    if (!mounted) return;
    if (!profile.isPrivate) {
      await _feed.refresh();
    }
  }

  @override
  void dispose() {
    _profileStore.destroy();
    _feed.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text('@${widget.handle}'),
        actions: [
          IconButton(
            tooltip: l10n.plugin_instagram_open_on_site,
            icon: const Icon(Icons.open_in_new),
            onPressed: () =>
                openUri(context, 'https://www.instagram.com/${widget.handle}/'),
          ),
        ],
      ),
      body: ScopedBuilder<InstagramProfileStore, InstagramProfile?>(
        store: _profileStore,
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: instagramErrorMessage(l10n, error),
          onRetry: _load,
        ),
        onState: (context, profile) {
          if (profile == null) {
            return const Center(child: CircularProgressIndicator());
          }
          return _ProfileBody(profile: profile, feed: _feed, onRefresh: _load);
        },
      ),
    );
  }
}

class _ProfileBody extends StatelessWidget {
  final InstagramProfile profile;
  final InstagramFeedStore feed;
  final Future<void> Function() onRefresh;

  const _ProfileBody({
    required this.profile,
    required this.feed,
    required this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          if (!profile.isPrivate && notification.metrics.extentAfter < 800) {
            feed.loadMore();
          }
          return false;
        },
        child: ListView(
          children: [
            _Header(profile: profile),
            if (profile.isPrivate)
              Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  L10n.of(context).plugin_instagram_profile_private,
                  textAlign: TextAlign.center,
                ),
              )
            else
              ScopedBuilder<InstagramFeedStore, List<InstagramPost>>(
                store: feed,
                onLoading: (_) => const Padding(
                  padding: EdgeInsets.all(32),
                  child: Center(child: CircularProgressIndicator()),
                ),
                onError: (_, error) => Padding(
                  padding: const EdgeInsets.all(24),
                  child: FullPageErrorWidget(
                    error: error,
                    stackTrace: null,
                    prefix: instagramErrorMessage(L10n.of(context), error),
                    onRetry: onRefresh,
                  ),
                ),
                onState: (context, posts) {
                  if (posts.isEmpty) {
                    return Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(
                        L10n.of(context).plugin_instagram_empty_posts,
                        textAlign: TextAlign.center,
                      ),
                    );
                  }
                  final guest = !context.read<InstagramClient>().hasSession;
                  return Column(
                    children: [
                      for (final post in posts)
                        InstagramPostCard(post: post, openAuthor: false),
                      if (feed.loadingMore)
                        const Padding(
                          padding: EdgeInsets.all(16),
                          child: Center(child: CircularProgressIndicator()),
                        )
                      else if (guest)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
                          child: Text(
                            L10n.of(
                              context,
                            ).plugin_instagram_more_needs_session,
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(
                                  color: Theme.of(
                                    context,
                                  ).colorScheme.onSurfaceVariant,
                                ),
                          ),
                        ),
                    ],
                  );
                },
              ),
          ],
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  final InstagramProfile profile;

  const _Header({required this.profile});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final follows = context.read<InstagramFollowsStore>();
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              InstagramAvatar(
                url: profile.avatarUrl,
                seed: profile.username,
                name: profile.displayName,
                size: 72,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            profile.displayName,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                        if (profile.isVerified) ...[
                          const SizedBox(width: 6),
                          Icon(
                            Icons.verified,
                            size: 20,
                            color: theme.colorScheme.primary,
                          ),
                        ],
                      ],
                    ),
                    Text('@${profile.username}'),
                  ],
                ),
              ),
            ],
          ),
          if ((profile.biography ?? '').trim().isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(profile.biography!.trim()),
          ],
          const SizedBox(height: 16),
          Row(
            children: [
              _stat(
                l10n.plugin_instagram_stat_followers,
                profile.followerCount,
              ),
              _stat(
                l10n.plugin_instagram_stat_following,
                profile.followingCount,
              ),
              _stat(l10n.plugin_instagram_stat_posts, profile.mediaCount),
            ],
          ),
          const SizedBox(height: 16),
          ScopedBuilder<InstagramFollowsStore, List<InstagramFollow>>(
            store: follows,
            onState: (context, list) {
              final following = list.any(
                (f) => f.id == profile.username.toLowerCase(),
              );
              return SizedBox(
                width: double.infinity,
                child: FilledButton.tonalIcon(
                  onPressed: () async {
                    if (following) {
                      await follows.unfollow(profile.username);
                    } else {
                      await follows.follow(profile);
                    }
                  },
                  icon: Icon(
                    following
                        ? Icons.person_remove_alt_1
                        : Icons.person_add_alt,
                  ),
                  label: Text(
                    following
                        ? l10n.plugin_instagram_unfollow
                        : l10n.plugin_instagram_follow,
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _stat(String label, int value) {
    return Expanded(
      child: Column(
        children: [
          Text(
            compactCount(value),
            style: const TextStyle(fontWeight: FontWeight.w800),
          ),
          Text(label),
        ],
      ),
    );
  }
}
