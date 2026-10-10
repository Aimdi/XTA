import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_follow_dialog.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';

const pixivPreviewWorks = 3;

/// How much of the creator's name, in em, must stay readable beside the follow
/// button; with less room the button moves under the name.
const pixivCardNameMinEm = 8.0;

/// The button's padding, icon and gap around its label (Material 3 tonal icon button).
const _followButtonChrome = 66.0;

/// The avatar and the gaps either side of the name in the card header.
const _headerChrome = 40.0 + 12 + 8;

/// Room each extra icon action beside the follow button takes.
const _actionWidth = kMinInteractiveDimension;

/// The works a preview row may show: none the reader muted, and none their
/// Show R-18 / Hide AI choices keep out of the feeds — dropped, not blurred.
List<PixivIllust> pixivVisiblePreviewWorks(
  List<PixivIllust> works, {
  required PixivMuteState mute,
  required bool showR18,
  required bool hideAi,
}) => mute
    .filter(works)
    .where((work) => pixivContentAllowed(work, includeR18: showR18, includeAi: !hideAi))
    .take(pixivPreviewWorks)
    .toList();

/// How wide the follow button gets with the longer of its two labels at the
/// reader's text size, so the layout does not jump when the follow flips.
double pixivFollowButtonWidth(BuildContext context) {
  final l10n = L10n.of(context);
  final style = Theme.of(context).textTheme.labelLarge;
  double measure(String label) {
    final painter = TextPainter(
      text: TextSpan(text: label, style: style),
      textScaler: MediaQuery.textScalerOf(context),
      textDirection: Directionality.of(context),
      maxLines: 1,
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width;
  }

  return max(measure(l10n.plugin_pixiv_follow), measure(l10n.plugin_pixiv_unfollow)) + _followButtonChrome;
}

/// Follow / Unfollow for one creator, with a 48dp target and a spinner while
/// Pixiv answers. A tap follows publicly or unfollows; a long press opens the
/// follow dialog to follow privately. The state lives in the app-wide
/// [PixivFollowStore]; [onChanged] lets the list holding [user] update its copy.
class PixivFollowButton extends StatelessWidget {
  final PixivUser user;
  final ValueChanged<bool>? onChanged;

  const PixivFollowButton({super.key, required this.user, this.onChanged});

  Future<void> _toggle(BuildContext context) async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final changed = onChanged;
    try {
      final followed = await context.read<PixivFollowStore>().toggle(user);
      if (context.mounted) changed?.call(followed);
    } catch (error) {
      messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final follows = context.read<PixivFollowStore>();
    return ScopedBuilder<PixivFollowStore, PixivFollows>(
      store: follows,
      onState: (context, _) => _button(context, followed: follows.isFollowed(user), busy: follows.isBusy(user.id)),
    );
  }

  Widget _button(BuildContext context, {required bool followed, required bool busy}) {
    final l10n = L10n.of(context);
    final icon = busy
        ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
        : Icon(followed ? Icons.person_remove_outlined : Icons.person_add_alt_1_outlined);
    return FilledButton.tonalIcon(
      key: ValueKey('pixiv-follow-${user.id}'),
      style: FilledButton.styleFrom(minimumSize: const Size(kMinInteractiveDimension, kMinInteractiveDimension)),
      onPressed: busy ? null : () => _toggle(context),
      onLongPress: busy ? null : () => editPixivFollow(context, user, onChanged: onChanged),
      icon: icon,
      label: Text(
        followed ? l10n.plugin_pixiv_unfollow : l10n.plugin_pixiv_follow,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }
}

/// Who a row is about beside their follow button and [actions]: side by side
/// while the name keeps room to be read, the buttons under the name on a
/// narrow screen or with large text.
class PixivFollowHeader extends StatelessWidget {
  final PixivUser user;

  /// The avatar and names, which take what room the buttons leave.
  final Widget identity;
  final ValueChanged<bool>? onFollowChanged;
  final List<Widget> actions;

  /// Width [identity] spends on things other than the name, such as the avatar.
  final double identityChrome;

  const PixivFollowHeader({
    super.key,
    required this.user,
    required this.identity,
    this.onFollowChanged,
    this.actions = const [],
    this.identityChrome = _headerChrome,
  });

  @override
  Widget build(BuildContext context) {
    // The reader cannot follow themselves; their own works and profile offer no button.
    final self = context.read<PixivClient>().storedUserId == user.id;
    if (self && actions.isEmpty) return identity;
    return LayoutBuilder(builder: (context, constraints) => _layout(context, constraints.maxWidth, self: self));
  }

  Widget _layout(BuildContext context, double width, {required bool self}) {
    final buttons = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (!self) PixivFollowButton(user: user, onChanged: onFollowChanged),
        ...actions,
      ],
    );
    final nameSize = Theme.of(context).textTheme.titleSmall?.fontSize ?? 14;
    final nameMin = MediaQuery.textScalerOf(context).scale(nameSize) * pixivCardNameMinEm;
    final buttonsWidth = (self ? 0 : pixivFollowButtonWidth(context)) + _actionWidth * actions.length;
    if (width - buttonsWidth - identityChrome >= nameMin) {
      return Row(
        children: [
          Expanded(child: identity),
          const SizedBox(width: 8),
          buttons,
        ],
      );
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.start, spacing: 8, children: [identity, buttons]);
  }
}

/// A creator's name over their @account, each on one line.
class PixivUserNames extends StatelessWidget {
  final String name;
  final String account;

  const PixivUserNames({super.key, required this.name, required this.account});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          name,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700),
        ),
        Text(
          '@$account',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
      ],
    );
  }
}

/// A creator as Pixiv previews them in recommendations, search and follow
/// lists: avatar, name, @account, three recent works and a follow button.
class PixivUserPreviewCard extends StatelessWidget {
  final PixivUserPreview preview;

  /// Hears the card's follow button, so the list can update its preview.
  final ValueChanged<bool>? onFollowChanged;

  /// Icon buttons beside the follow button, such as Add to group.
  final List<Widget> actions;

  const PixivUserPreviewCard({super.key, required this.preview, this.onFollowChanged, this.actions = const []});

  @override
  Widget build(BuildContext context) {
    final user = preview.user;
    return Card.outlined(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: ValueKey('pixiv-user-card-${user.id}'),
        onTap: () => openPixivUser(context, user.id),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              PixivFollowHeader(
                user: user,
                identity: Row(
                  children: [
                    PixivAvatar.user(user),
                    const SizedBox(width: 12),
                    Expanded(
                      child: PixivUserNames(name: user.name, account: user.account),
                    ),
                  ],
                ),
                onFollowChanged: onFollowChanged,
                actions: actions,
              ),
              ScopedBuilder<PixivMuteStore, PixivMuteState>(
                store: context.read<PixivMuteStore>(),
                onState: (context, mute) => _works(context, _visibleWorks(context, mute)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  List<PixivIllust> _visibleWorks(BuildContext context, PixivMuteState mute) {
    final client = context.read<PixivClient>();
    return pixivVisiblePreviewWorks(preview.illusts, mute: mute, showR18: client.showR18, hideAi: client.hideAi);
  }

  Widget _works(BuildContext context, List<PixivIllust> works) {
    if (works.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Row(
        spacing: 4,
        children: [
          for (var index = 0; index < pixivPreviewWorks; index++)
            Expanded(
              child: AspectRatio(
                aspectRatio: 1,
                child: index < works.length ? _work(context, works, index) : const SizedBox.shrink(),
              ),
            ),
        ],
      ),
    );
  }

  Widget _work(BuildContext context, List<PixivIllust> works, int index) {
    final work = works[index];
    void open() => openPixivIllustFromList(context, works, index);
    return Semantics(
      container: true,
      button: true,
      label: work.title,
      onTap: open,
      excludeSemantics: true,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Stack(
          fit: StackFit.expand,
          children: [
            ColoredBox(color: Theme.of(context).colorScheme.surfaceContainerHighest),
            PixivNetworkImage(url: work.thumbnailUrl, fit: BoxFit.cover),
            Material(
              type: MaterialType.transparency,
              child: InkWell(key: ValueKey('pixiv-user-card-work-${work.id}'), onTap: open),
            ),
          ],
        ),
      ),
    );
  }
}
