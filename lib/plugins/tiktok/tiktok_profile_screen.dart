import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_errors.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/plugins/tiktok/tiktok_post_card.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';

final NumberFormat _count = NumberFormat.compact(locale: 'en_US');

class TikTokProfileScreen extends StatefulWidget {
  final String handle;

  const TikTokProfileScreen({super.key, required this.handle});

  @override
  State<TikTokProfileScreen> createState() => _TikTokProfileScreenState();
}

class _TikTokProfileScreenState extends State<TikTokProfileScreen> {
  late final TikTokProfileStore _profileStore;
  late final TikTokFeedStore _feed;

  @override
  void initState() {
    super.initState();
    final client = context.read<TikTokClient>();
    _profileStore = TikTokProfileStore(client, widget.handle);
    _feed = TikTokFeedStore(({cursor}) async {
      final profile =
          _profileStore.state ?? await client.profile(widget.handle);
      return client.creatorItems(secUid: profile.secUid, cursor: cursor);
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    await _profileStore.load();
    if (!mounted) return;
    final profile = _profileStore.state;
    if (profile == null) return;
    await context.read<TikTokSearchHistoryStore>().remember(profile.uniqueId);
    if (!mounted) return;
    if (!profile.privateAccount) {
      await _feed.refresh();
      if (!mounted) return;
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
        title: ScopedBuilder<TikTokProfileStore, TikTokProfile?>(
          store: _profileStore,
          onLoading: (_) => Text('@${widget.handle}'),
          onError: (context, error) => Text('@${widget.handle}'),
          onState: (_, profile) => Text(
            profile?.displayName ?? '@${widget.handle}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        actions: [
          ScopedBuilder<TikTokProfileStore, TikTokProfile?>(
            store: _profileStore,
            onState: (context, profile) => profile == null
                ? const SizedBox.shrink()
                : IconButton(
                    tooltip: l10n.plugin_tiktok_open_on_site,
                    icon: const Icon(Icons.open_in_new),
                    onPressed: () =>
                        openUri(context, profile.profileUri().toString()),
                  ),
          ),
        ],
      ),
      body: ScopedBuilder<TikTokProfileStore, TikTokProfile?>(
        store: _profileStore,
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: tiktokErrorMessage(l10n, error),
          onRetry: _load,
        ),
        onState: (context, profile) {
          if (profile == null) {
            return const Center(child: CircularProgressIndicator());
          }
          return _ProfileContent(
            profile: profile,
            feed: _feed,
            onRefresh: _load,
          );
        },
      ),
    );
  }
}

class _ProfileContent extends StatelessWidget {
  final TikTokProfile profile;
  final TikTokFeedStore feed;
  final Future<void> Function() onRefresh;

  const _ProfileContent({
    required this.profile,
    required this.feed,
    required this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async {
        await onRefresh();
        if (!context.mounted) return;
      },
      child: NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          if (!profile.privateAccount &&
              notification.metrics.extentAfter < 800) {
            feed.loadMore();
          }
          return false;
        },
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(child: _Header(profile: profile)),
            if (!profile.privateAccount) _FeedSliver(store: feed),
          ],
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  final TikTokProfile profile;

  const _Header({required this.profile});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final follows = context.read<TikTokFollowsStore>();

    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              TikTokAvatar(
                url: profile.avatarUrl,
                seed: profile.uniqueId,
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
                        if (profile.verified) ...[
                          const SizedBox(width: 6),
                          Icon(
                            Icons.verified,
                            size: 20,
                            color: theme.colorScheme.primary,
                          ),
                        ],
                        if (profile.privateAccount) ...[
                          const SizedBox(width: 6),
                          Icon(Icons.lock, size: 18, color: muted),
                        ],
                      ],
                    ),
                    Text(
                      '@${profile.uniqueId}',
                      style: theme.textTheme.bodyMedium?.copyWith(color: muted),
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (profile.signature != null &&
              profile.signature!.trim().isNotEmpty) ...[
            const SizedBox(height: 14),
            Text(profile.signature!.trim()),
          ],
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: _Stat(
                  label: l10n.plugin_tiktok_stat_followers,
                  value: profile.followerCount,
                ),
              ),
              Expanded(
                child: _Stat(
                  label: l10n.plugin_tiktok_stat_videos,
                  value: profile.videoCount,
                ),
              ),
              Expanded(
                child: _Stat(
                  label: l10n.plugin_tiktok_stat_likes,
                  value: profile.heartCount,
                ),
              ),
            ],
          ),
          if (!profile.privateAccount) ...[
            const SizedBox(height: 16),
            ScopedBuilder<TikTokFollowsStore, List<TikTokFollow>>(
              store: follows,
              onState: (context, list) {
                final following = list.any(
                  (f) => f.id == profile.uniqueId.toLowerCase(),
                );
                return SizedBox(
                  width: double.infinity,
                  child: FilledButton.tonalIcon(
                    onPressed: () async {
                      if (following) {
                        await follows.unfollow(profile.uniqueId);
                      } else {
                        await follows.follow(profile);
                      }
                      if (!context.mounted) return;
                    },
                    icon: Icon(
                      following
                          ? Icons.person_remove_alt_1
                          : Icons.person_add_alt,
                    ),
                    label: Text(
                      following
                          ? l10n.plugin_tiktok_unfollow
                          : l10n.plugin_tiktok_follow,
                    ),
                  ),
                );
              },
            ),
          ],
          if (profile.privateAccount) ...[
            const SizedBox(height: 16),
            Text(
              l10n.plugin_tiktok_profile_private,
              style: theme.textTheme.bodySmall?.copyWith(color: muted),
            ),
          ],
        ],
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  final String label;
  final int value;

  const _Stat({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          _count.format(value),
          style: Theme.of(
            context,
          ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
        ),
        Text(
          label,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

class _FeedSliver extends StatelessWidget {
  final TikTokFeedStore store;

  const _FeedSliver({required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<TikTokFeedStore, List<TikTokPost>>(
      store: store,
      onLoading: (_) => const SliverFillRemaining(
        child: Center(child: CircularProgressIndicator()),
      ),
      onError: (_, error) => SliverFillRemaining(
        child: FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: tiktokErrorMessage(l10n, error),
          onRetry: store.refresh,
        ),
      ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return SliverFillRemaining(
            child: Center(child: Text(l10n.plugin_tiktok_empty_posts)),
          );
        }
        return SliverList.builder(
          itemCount: posts.length + (store.loadingMore ? 1 : 0),
          itemBuilder: (context, index) {
            if (index >= posts.length) {
              return const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              );
            }
            return TikTokPostCard(post: posts[index], openAuthor: false);
          },
        );
      },
    );
  }
}
