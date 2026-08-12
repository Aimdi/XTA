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
  late final TikTokFeedStore _feed;
  TikTokProfile? _profile;
  Object? _error;
  var _loading = true;

  @override
  void initState() {
    super.initState();
    final client = context.read<TikTokClient>();
    _feed = TikTokFeedStore(({cursor}) async {
      final profile = _profile ?? await client.profile(widget.handle);
      _profile = profile;
      return client.creatorItems(secUid: profile.secUid, cursor: cursor);
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final profile = await context.read<TikTokClient>().profile(widget.handle);
      if (!mounted) return;
      _profile = profile;
      await context.read<TikTokSearchHistoryStore>().remember(profile.uniqueId);
      if (!mounted) return;
      await _feed.refresh();
    } catch (e) {
      _error = e;
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void dispose() {
    _feed.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final profile = _profile;

    return Scaffold(
      appBar: AppBar(
        title: Text(profile?.displayName ?? '@${widget.handle}'),
        actions: [
          if (profile != null)
            IconButton(
              tooltip: l10n.plugin_tiktok_open_on_site,
              icon: const Icon(Icons.open_in_new),
              onPressed: () =>
                  openUri(context, profile.profileUri().toString()),
            ),
        ],
      ),
      body: _loading && profile == null
          ? const Center(child: CircularProgressIndicator())
          : _error != null && profile == null
          ? FullPageErrorWidget(
              error: _error,
              stackTrace: null,
              prefix: tiktokErrorMessage(l10n, _error),
              onRetry: _load,
            )
          : RefreshIndicator(
              onRefresh: _load,
              child: NotificationListener<ScrollNotification>(
                onNotification: (notification) {
                  if (notification.metrics.extentAfter < 800) {
                    _feed.loadMore();
                  }
                  return false;
                },
                child: CustomScrollView(
                  slivers: [
                    if (profile != null)
                      SliverToBoxAdapter(child: _Header(profile: profile)),
                    _FeedSliver(store: _feed),
                  ],
                ),
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

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
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
              const SizedBox(width: 16),
              Expanded(
                child: Wrap(
                  spacing: 16,
                  runSpacing: 8,
                  children: [
                    _Stat(
                      label: l10n.plugin_tiktok_stat_followers,
                      value: profile.followerCount,
                    ),
                    _Stat(
                      label: l10n.plugin_tiktok_stat_videos,
                      value: profile.videoCount,
                    ),
                    _Stat(
                      label: l10n.plugin_tiktok_stat_likes,
                      value: profile.heartCount,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            profile.displayName,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          Text('@${profile.uniqueId}'),
          if (profile.signature != null && profile.signature!.trim().isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(profile.signature!),
            ),
          const SizedBox(height: 12),
          ScopedBuilder<TikTokFollowsStore, List<TikTokFollow>>(
            store: follows,
            onState: (context, list) {
              final following = list.any(
                (f) => f.id == profile.uniqueId.toLowerCase(),
              );
              return FilledButton.tonal(
                onPressed: () async {
                  if (following) {
                    await follows.unfollow(profile.uniqueId);
                  } else {
                    await follows.follow(profile);
                  }
                },
                child: Text(
                  following
                      ? l10n.plugin_tiktok_unfollow
                      : l10n.plugin_tiktok_follow,
                ),
              );
            },
          ),
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
          style: Theme.of(context).textTheme.titleMedium,
        ),
        Text(label, style: Theme.of(context).textTheme.bodySmall),
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
