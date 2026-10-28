import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_group.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/utils/formatting_locale.dart';

const _avatarSize = 72.0;
const _buttonGap = 8.0;

/// The top of a profile in the Kanten style: header image, avatar and names,
/// the follow and group buttons on a row of their own, the counts and the bio,
/// closed by a hairline above the tabs.
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
              _identity(context),
              const SizedBox(height: 14),
              _ProfileButtons(user: profile.user),
              _counts(context),
              if (bio.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(bio, style: Theme.of(context).textTheme.bodyMedium),
              ],
            ],
          ),
        ),
        const Divider(height: 1),
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
                style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w800),
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

  /// Premium as an outlined badge in the accent.
  Widget _premium(BuildContext context) {
    final accent = Theme.of(context).colorScheme.primary;
    return DecoratedBox(
      decoration: BoxDecoration(border: Border.all(color: accent), borderRadius: BorderRadius.circular(4)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
        child: Text(
          L10n.of(context).plugin_pixiv_profile_premium,
          style: Theme.of(context).textTheme.labelSmall!.copyWith(color: accent, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }

  /// The works count shows the Works tab and the following count opens the list.
  Widget _counts(BuildContext context) {
    final l10n = L10n.of(context);
    final user = profile.user;
    final locale = currentFormattingLocale();
    final works = profileCount(user.worksCount, locale);
    final following = profileCount(user.followingCount, locale);
    final friends = profileCount(user.mypixivCount, locale);
    return Wrap(
      spacing: 18,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        _Count(
          key: const ValueKey('pixiv-profile-works-count'),
          label: l10n.plugin_pixiv_works_count(user.worksCount, works),
          number: works,
          onTap: onWorks,
        ),
        _Count(
          key: const ValueKey('pixiv-profile-following-count'),
          label: l10n.plugin_pixiv_following_count(user.followingCount, following),
          number: following,
          onTap: () => openPixivUserList(context, PixivUserListKind.following, userId: profile.id),
        ),
        _Count(label: l10n.plugin_pixiv_mypixiv_count(user.mypixivCount, friends), number: friends),
      ],
    );
  }
}

/// [label] in the secondary colour with [number], where the label has it, in
/// bold in the text colour. Each language keeps its own word order, and one
/// that spells a small count out stays as it is.
TextSpan pixivCountSpan(String label, String number, {required Color numberColor, required Color restColor}) {
  final at = label.indexOf(number);
  final rest = TextStyle(color: restColor);
  if (at < 0) return TextSpan(text: label, style: rest);
  return TextSpan(
    style: rest,
    children: [
      if (at > 0) TextSpan(text: label.substring(0, at)),
      TextSpan(
        text: number,
        style: TextStyle(color: numberColor, fontWeight: FontWeight.w700),
      ),
      if (at + number.length < label.length) TextSpan(text: label.substring(at + number.length)),
    ],
  );
}

/// One count in the header, a 48dp target when it leads somewhere. The text
/// starts flush with the names above it.
class _Count extends StatelessWidget {
  final String label;
  final String number;
  final VoidCallback? onTap;

  const _Count({super.key, required this.label, required this.number, this.onTap});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = ConstrainedBox(
      constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
      child: Align(
        alignment: AlignmentDirectional.centerStart,
        widthFactor: 1,
        heightFactor: 1,
        child: Text.rich(
          pixivCountSpan(label, number, numberColor: scheme.onSurface, restColor: scheme.onSurfaceVariant),
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ),
    );
    if (onTap == null) return text;
    return InkWell(borderRadius: BorderRadius.circular(8), onTap: onTap, child: text);
  }
}

/// Follow across the row and Add to group beside it. The group button keeps
/// its name while it fits beside Follow, and its icon and tooltip otherwise;
/// the reader's own profile has Add to group alone.
class _ProfileButtons extends StatelessWidget {
  final PixivUser user;

  const _ProfileButtons({required this.user});

  @override
  Widget build(BuildContext context) {
    // The reader cannot follow themselves.
    final self = context.read<PixivClient>().storedUserId == user.id;
    return LayoutBuilder(
      builder: (context, constraints) {
        final follow = self ? 0.0 : pixivFollowButtonWidth(context) + _buttonGap;
        final group = pixivLabelledButtonWidth(
          context,
          [L10n.of(context).add_to_group],
          chrome: pixivEdgedButtonChrome,
        );
        final button = _GroupButton(user: user, named: follow + group <= constraints.maxWidth);
        if (self) return button;
        return Row(
          children: [
            Expanded(child: PixivFollowButton(user: user, prominent: true)),
            const SizedBox(width: _buttonGap),
            button,
          ],
        );
      },
    );
  }
}

/// Add to group in the Kanten style: outlined with an edge, named when [named].
class _GroupButton extends StatelessWidget {
  final PixivUser user;
  final bool named;

  const _GroupButton({required this.user, required this.named});

  @override
  Widget build(BuildContext context) {
    final label = L10n.of(context).add_to_group;
    final style = pixivEdgedOutlinedStyle(context);
    void add() => addPixivToGroup(context, user);
    const key = ValueKey('pixiv-profile-group');
    const icon = Icon(Icons.group_add_outlined);
    if (named) {
      return OutlinedButton.icon(key: key, style: style, onPressed: add, icon: icon, label: Text(label, maxLines: 1));
    }
    return IconButton.outlined(
      key: key,
      tooltip: label,
      style: IconButton.styleFrom(
        minimumSize: const Size(kMinInteractiveDimension, kMinInteractiveDimension),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(pixivEdgedRadius)),
        side: BorderSide(color: Theme.of(context).colorScheme.outline),
        foregroundColor: Theme.of(context).colorScheme.onSurface,
      ),
      onPressed: add,
      icon: icon,
    );
  }
}

/// The header image; a long press saves it.
class _Banner extends StatelessWidget {
  final String url;

  const _Banner({required this.url});

  @override
  Widget build(BuildContext context) {
    final height = (MediaQuery.sizeOf(context).width / 3).clamp(120.0, 200.0);
    void save() => savePixivImage(context, url);
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
    void save() => savePixivImage(context, url);
    return Tooltip(
      message: label,
      child: Semantics(
        button: true,
        label: label,
        onTap: save,
        excludeSemantics: true,
        child: InkWell(
          key: const ValueKey('pixiv-profile-avatar'),
          customBorder: const CircleBorder(),
          onTap: save,
          child: avatar,
        ),
      ),
    );
  }
}
