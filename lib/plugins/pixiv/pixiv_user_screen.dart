import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_group.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/plugins/plugin_counts.dart';

/// One Pixiv user's profile and works in a staggered grid.
class PixivUserScreen extends StatefulWidget {
  final int userId;

  const PixivUserScreen({super.key, required this.userId});

  @override
  State<PixivUserScreen> createState() => _PixivUserScreenState();
}

class _PixivUserScreenState extends State<PixivUserScreen> {
  late final PixivUserStore _profile;
  late final PixivIllustListStore _works;
  PixivUser? get _user => _profile.state;

  @override
  void initState() {
    super.initState();
    final client = context.read<PixivClient>();
    _profile = PixivUserStore(client, widget.userId);
    _works = PixivIllustListStore(({nextUrl}) => client.userIllusts(widget.userId, nextUrl: nextUrl),
      filter: context.read<PixivMuteStore>().filter);
    _load();
  }

  @override
  void dispose() { _profile.destroy(); _works.destroy(); super.dispose(); }

  Future<void> _load() async { await Future.wait([_profile.load(), _works.refresh()]); }

  Future<void> _toggleFollow() async {
    final l10n = L10n.of(context);
    try { await _profile.toggleFollow(); }
    catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(pixivErrorMessage(l10n, error))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return TripleBuilder<PixivUserStore, PixivUser?>(store: _profile,
      builder: (_, profile) => TripleBuilder<PixivIllustListStore, List<PixivIllust>>(
        store: _works, builder: (_, works) => Scaffold(
      appBar: AppBar(
        title: Text(_user?.name ?? '${widget.userId}'),
        actions: [
          if (_user != null)
            IconButton(
              icon: const Icon(Icons.open_in_new),
              tooltip: l10n.plugin_pixiv_open_on_pixiv,
              onPressed: () =>
                  openUri(context, 'https://www.pixiv.net/users/${_user!.id}'),
            ),
        ],
      ),
      body: _body(context, profile.error ?? works.error),
    )));
  }

  Widget _body(BuildContext context, Object? error) {
    final l10n = L10n.of(context);

    if (error != null) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: pixivErrorMessage(l10n, error),
          onRetry: _load,
        ),
      );
    }

    if (_profile.isLoading || _user == null) return const Center(child: CircularProgressIndicator());
    final user = _user!;
    final theme = Theme.of(context);
    final avatar = user.avatarUrl;

    return NotificationListener<ScrollNotification>(
      onNotification: (n) {
        if (n.metrics.pixels > n.metrics.maxScrollExtent - 400) {
          _works.loadMore();
        }
        return false;
      },
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      ClipOval(
                        child: avatar == null
                            ? FallbackAvatar(
                                seed: '${user.id}',
                                displayName: user.name,
                                size: 64,
                                accent: theme.colorScheme.primary,
                              )
                            : SizedBox(
                                width: 64,
                                height: 64,
                                child: PixivNetworkImage(
                                  url: avatar,
                                  fit: BoxFit.cover,
                                  cacheWidth:
                                      (64 *
                                              MediaQuery.devicePixelRatioOf(
                                                context,
                                              ))
                                          .ceil(),
                                  cacheHeight:
                                      (64 *
                                              MediaQuery.devicePixelRatioOf(
                                                context,
                                              ))
                                          .ceil(),
                                ),
                              ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              user.name,
                              style: theme.textTheme.titleLarge!.copyWith(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            Text(
                              '@${user.account}',
                              style: theme.textTheme.bodyMedium!.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  if (user.comment.trim().isNotEmpty) ...[
                    const SizedBox(height: 14),
                    Text(
                      user.comment.trim(),
                      style: theme.textTheme.bodyMedium,
                    ),
                  ],
                  const SizedBox(height: 14),
                  Text(
                    '${compactCount(user.illustsCount)} ${l10n.tweets} · ${compactCount(user.followersCount)} ${l10n.followers}',
                    style: theme.textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: () => addPixivToGroup(context, user),
                    icon: const Icon(Icons.group_add_outlined),
                    label: Text(l10n.add_to_group),
                  ),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: FilledButton.tonalIcon(
                      onPressed: _profile.followBusy ? null : _toggleFollow,
                      icon: Icon(
                        user.isFollowed
                            ? Icons.person_remove_outlined
                            : Icons.person_add_alt_1_outlined,
                      ),
                      label: Text(
                        user.isFollowed
                            ? l10n.plugin_pixiv_unfollow
                            : l10n.plugin_pixiv_follow,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(4, 0, 4, 24),
            sliver: SliverMasonryGrid.count(
              crossAxisCount: 2,
              mainAxisSpacing: 4,
              crossAxisSpacing: 4,
              childCount: _works.state.length,
              itemBuilder: (context, index) =>
                  PixivIllustTile(illust: _works.state[index]),
            ),
          ),
          if (_works.loadingMore)
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
        ],
      ),
    );
  }
}
