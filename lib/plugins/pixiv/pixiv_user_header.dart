import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_group.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_post_media.dart';

const _avatarSize = 72.0;

/// Saves a profile picture or header image like any other plugin image.
Future<void> savePixivProfileImage(BuildContext context, String url) =>
    downloadPluginMediaItem(context, PluginMediaItem(url: url), sourceName: pixivDownloadSource);

/// The top of a profile: header image, avatar, names, follow and group
/// buttons, the counts and the bio.
class PixivUserHeader extends StatelessWidget {
  final PixivUserProfile profile;

  /// Shows the Works tab, for the works count.
  final VoidCallback? onWorks;

  const PixivUserHeader({super.key, required this.profile, this.onWorks});

  @override
  Widget build(BuildContext context) {
    final bio = profile.user.comment.trim();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (profile.backgroundUrl case final url?) _Banner(url: url),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              PixivFollowHeader(
                user: profile.user,
                identityChrome: _avatarSize + 14 + 8,
                identity: _identity(context),
                actions: [
                  IconButton(
                    key: const ValueKey('pixiv-profile-group'),
                    tooltip: L10n.of(context).add_to_group,
                    icon: const Icon(Icons.group_add_outlined),
                    onPressed: () => addPixivToGroup(context, profile.user),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              _counts(context),
              if (bio.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(bio, style: Theme.of(context).textTheme.bodyMedium),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _identity(BuildContext context) {
    final theme = Theme.of(context);
    final user = profile.user;
    return Row(
      children: [
        _Avatar(profile: profile),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                user.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w700),
              ),
              Text(
                '@${user.account}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              if (profile.isPremium) ...[const SizedBox(height: 4), _premium(context)],
            ],
          ),
        ),
      ],
    );
  }

  Widget _premium(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(color: scheme.tertiaryContainer, borderRadius: BorderRadius.circular(6)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        child: Text(
          L10n.of(context).plugin_pixiv_profile_premium,
          style: Theme.of(context).textTheme.labelSmall!.copyWith(color: scheme.onTertiaryContainer),
        ),
      ),
    );
  }

  /// The works count shows the Works tab and the following count opens the list.
  Widget _counts(BuildContext context) {
    final l10n = L10n.of(context);
    final user = profile.user;
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        _Count(
          key: const ValueKey('pixiv-profile-works-count'),
          label: l10n.plugin_pixiv_works_count(user.worksCount, compactCount(user.worksCount)),
          onTap: onWorks,
        ),
        _Count(
          key: const ValueKey('pixiv-profile-following-count'),
          label: l10n.plugin_pixiv_following_count(user.followingCount, compactCount(user.followingCount)),
          onTap: () => openPixivUserList(context, PixivUserListKind.following, userId: profile.id),
        ),
        _Count(label: l10n.plugin_pixiv_mypixiv_count(user.mypixivCount, compactCount(user.mypixivCount))),
      ],
    );
  }
}

/// One count in the header, a 48dp target when it leads somewhere.
class _Count extends StatelessWidget {
  final String label;
  final VoidCallback? onTap;

  const _Count({super.key, required this.label, this.onTap});

  @override
  Widget build(BuildContext context) {
    final text = ConstrainedBox(
      constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Align(
          widthFactor: 1,
          heightFactor: 1,
          child: Text(label, style: Theme.of(context).textTheme.bodyMedium),
        ),
      ),
    );
    if (onTap == null) return Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: text);
    return InkWell(borderRadius: BorderRadius.circular(8), onTap: onTap, child: text);
  }
}

/// The header image; a long press saves it.
class _Banner extends StatelessWidget {
  final String url;

  const _Banner({required this.url});

  @override
  Widget build(BuildContext context) {
    final height = (MediaQuery.sizeOf(context).width / 3).clamp(120.0, 200.0);
    void save() => savePixivProfileImage(context, url);
    return Semantics(
      image: true,
      onLongPress: save,
      onLongPressHint: L10n.of(context).plugin_pixiv_profile_save_background,
      child: GestureDetector(
        key: const ValueKey('pixiv-profile-banner'),
        onLongPress: save,
        child: SizedBox(
          height: height,
          child: ColoredBox(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: PixivNetworkImage(url: url, fit: BoxFit.cover),
          ),
        ),
      ),
    );
  }
}

/// The profile picture; a tap saves it.
class _Avatar extends StatelessWidget {
  final PixivUserProfile profile;

  const _Avatar({required this.profile});

  @override
  Widget build(BuildContext context) {
    final avatar = PixivAvatar.user(profile.user, size: _avatarSize);
    final url = profile.user.avatarUrl;
    if (url == null) return avatar;
    final label = L10n.of(context).plugin_pixiv_profile_save_avatar;
    return Tooltip(
      message: label,
      child: Semantics(
        button: true,
        label: label,
        excludeSemantics: true,
        child: InkWell(
          key: const ValueKey('pixiv-profile-avatar'),
          customBorder: const CircleBorder(),
          onTap: () => savePixivProfileImage(context, url),
          child: avatar,
        ),
      ),
    );
  }
}
