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
  PixivUser? _user;
  List<PixivIllust> _illusts = const [];
  String? _nextUrl;
  Object? _error;
  var _loading = true;
  var _loadingMore = false;
  var _followBusy = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    final client = context.read<PixivClient>();
    final mute = context.read<PixivMuteStore>();
    try {
      final results = await Future.wait([
        client.userDetail(widget.userId),
        client.userIllusts(widget.userId),
      ]);
      if (mounted) {
        final user = results[0] as PixivUser;
        final page = results[1] as PixivIllustPage;
        setState(() {
          _user = user;
          _illusts = mute.filter(page.illusts);
          _nextUrl = page.nextUrl;
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
    if (_loadingMore || _nextUrl == null || _nextUrl!.isEmpty) {
      return;
    }
    setState(() => _loadingMore = true);
    final client = context.read<PixivClient>();
    final mute = context.read<PixivMuteStore>();
    try {
      final page = await client.userIllusts(widget.userId, nextUrl: _nextUrl);
      if (mounted) {
        setState(() {
          _illusts = [..._illusts, ...mute.filter(page.illusts)];
          _nextUrl = page.nextUrl;
          _loadingMore = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  Future<void> _toggleFollow() async {
    final user = _user;
    if (user == null || _followBusy) {
      return;
    }

    final client = context.read<PixivClient>();
    final l10n = L10n.of(context);
    setState(() => _followBusy = true);
    try {
      if (user.isFollowed) {
        await client.unfollowUser(user.id);
        if (mounted) {
          setState(() {
            _user = user.copyWith(
              isFollowed: false,
              followersCount: (user.followersCount - 1).clamp(0, 1 << 30),
            );
            _followBusy = false;
          });
        }
      } else {
        await client.followUser(user.id);
        if (mounted) {
          setState(() {
            _user = user.copyWith(
              isFollowed: true,
              followersCount: user.followersCount + 1,
            );
            _followBusy = false;
          });
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() => _followBusy = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, e))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final title = _user?.name ?? '${widget.userId}';

    return Scaffold(
      appBar: AppBar(
        title: Text(title),
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
          prefix: pixivErrorMessage(l10n, error),
          onRetry: _load,
        ),
      );
    }

    final user = _user!;
    final theme = Theme.of(context);
    final avatar = user.avatarUrl;

    return NotificationListener<ScrollNotification>(
      onNotification: (n) {
        if (n.metrics.pixels > n.metrics.maxScrollExtent - 400) {
          _loadMore();
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
                  Align(
                    alignment: Alignment.centerLeft,
                    child: FilledButton.tonalIcon(
                      onPressed: _followBusy ? null : _toggleFollow,
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
              childCount: _illusts.length,
              itemBuilder: (context, index) =>
                  PixivIllustTile(illust: _illusts[index]),
            ),
          ),
          if (_loadingMore)
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
