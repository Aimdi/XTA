import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_paged_feed.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';

typedef PixivRecommendedUsersStore = PixivPagedListStore<PixivUserPreview>;

/// Creators neither the reader nor Pixiv has muted.
List<PixivUserPreview> pixivUnmutedPreviews(List<PixivUserPreview> previews, PixivMuteState mute) => [
  for (final preview in previews)
    if (!preview.isMuted && !mute.authorIds.contains(preview.user.id)) preview,
];

PixivRecommendedUsersStore pixivRecommendedUsersStore(PixivDiscoveryApi api, PixivMuteStore mute) =>
    PixivPagedListStore(
      ({nextUrl}) => api.recommendedUsers(nextUrl: nextUrl),
      keyOf: (preview) => preview.user.id,
      filter: (previews) => pixivUnmutedPreviews(previews, mute.state),
    );

Future<void> openPixivRecommendedUsers(BuildContext context) =>
    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const PixivRecommendedUsersScreen()));

/// Every creator Pixiv suggests, paged, each with three works and a follow button.
class PixivRecommendedUsersScreen extends StatefulWidget {
  const PixivRecommendedUsersScreen({super.key});

  @override
  State<PixivRecommendedUsersScreen> createState() => _PixivRecommendedUsersScreenState();
}

class _PixivRecommendedUsersScreenState extends State<PixivRecommendedUsersScreen> {
  late final PixivRecommendedUsersStore _users;

  @override
  void initState() {
    super.initState();
    _users = pixivRecommendedUsersStore(PixivDiscoveryApi.of(context), context.read<PixivMuteStore>())..refresh();
  }

  @override
  void dispose() {
    _users.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_pixiv_recommended_users)),
      body: ScopedBuilder<PixivMuteStore, PixivMuteState>(
        store: context.read<PixivMuteStore>(),
        onState: (context, mute) => PixivPagedFeed<PixivUserPreview>(
          store: _users,
          emptyMessage: l10n.plugin_pixiv_recommended_users_empty,
          emptyIcon: Icons.person_search_outlined,
          padding: const EdgeInsets.all(12),
          sliver: (context, previews) {
            final shown = pixivUnmutedPreviews(previews, mute);
            return SliverList.separated(
              itemCount: shown.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (context, index) => PixivUserPreviewCard(preview: shown[index]),
            );
          },
        ),
      ),
    );
  }
}

/// A sideways strip of creators, each one tap from their profile: Home's
/// suggested creators, and the search landing's once it moves onto this.
class PixivRecommendedUsersStrip extends StatelessWidget {
  final List<PixivUser> users;
  final EdgeInsetsGeometry padding;

  const PixivRecommendedUsersStrip({
    super.key,
    required this.users,
    this.padding = const EdgeInsets.symmetric(horizontal: 12),
  });

  static const _avatar = 56.0;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final nameHeight = MediaQuery.textScalerOf(context).scale(theme.textTheme.labelSmall!.fontSize ?? 11) * 1.5;
    return SizedBox(
      height: _avatar + 6 + nameHeight + 8,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: padding,
        itemCount: users.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) => _creator(context, users[index]),
      ),
    );
  }

  Widget _creator(BuildContext context, PixivUser user) => InkWell(
    key: ValueKey('pixiv-recommended-user-${user.id}'),
    borderRadius: BorderRadius.circular(8),
    onTap: () => openPixivUser(context, user.id),
    child: SizedBox(
      width: 72,
      child: Column(
        children: [
          PixivAvatar.user(user, size: _avatar),
          const SizedBox(height: 6),
          Text(
            user.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.labelSmall,
          ),
        ],
      ),
    ),
  );
}
