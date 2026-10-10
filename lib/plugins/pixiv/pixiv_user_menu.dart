import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_follow_dialog.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_overflow_menu.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/utils/urls.dart';

/// One entry of a profile's overflow menu. A feature adds its entry to
/// [pixivProfileMenuEntries] rather than to the screen.
class PixivProfileMenuEntry implements PixivMenuItemSpec {
  /// Stable name; the menu item is keyed `pixiv-profile-menu-<id>`.
  @override
  final String id;
  @override
  final IconData icon;
  @override
  final String Function(L10n l10n) label;
  final Future<void> Function(BuildContext context, PixivProfileScope scope) run;
  final bool Function(PixivProfileScope scope) offeredFor;

  const PixivProfileMenuEntry({
    required this.id,
    required this.icon,
    required this.label,
    required this.run,
    this.offeredFor = _always,
  });
}

bool _always(PixivProfileScope _) => true;

bool _someoneElse(PixivProfileScope scope) => !scope.own;

Future<void> _followPrivately(BuildContext context, PixivProfileScope scope) =>
    editPixivFollow(context, scope.profile.user);

Future<void> _copyInfo(BuildContext context, PixivProfileScope scope) async {
  final messenger = ScaffoldMessenger.of(context);
  final copied = L10n.of(context).plugin_pixiv_profile_info_copied;
  await Clipboard.setData(ClipboardData(text: scope.profile.infoText));
  messenger.showSnackBar(SnackBar(content: Text(copied)));
}

Future<void> _mute(BuildContext context, PixivProfileScope scope) async {
  final label = L10n.of(context).plugin_pixiv_mute_author;
  await confirmPixivMute(context, (
    icon: Icons.person_off_outlined,
    label: label,
    mute: (store) => store.muteAuthor(scope.profile.id),
  ));
}

Future<void> _unmute(BuildContext context, PixivProfileScope scope) =>
    context.read<PixivMuteStore>().unmuteAuthor(scope.profile.id);

Future<void> _open(BuildContext context, PixivProfileScope scope) => openUri(context, scope.profile.url);

final pixivProfileMenuEntries = <PixivProfileMenuEntry>[
  PixivProfileMenuEntry(
    id: 'followPrivately',
    icon: Icons.lock_person_outlined,
    label: (l10n) => l10n.plugin_pixiv_follow_privately,
    run: _followPrivately,
    offeredFor: _someoneElse,
  ),
  PixivProfileMenuEntry(
    id: 'copyInfo',
    icon: Icons.copy_outlined,
    label: (l10n) => l10n.plugin_pixiv_profile_copy_info,
    run: _copyInfo,
  ),
  PixivProfileMenuEntry(
    id: 'mute',
    icon: Icons.person_off_outlined,
    label: (l10n) => l10n.plugin_pixiv_mute_author,
    run: _mute,
    offeredFor: (scope) => !scope.own && !scope.muted,
  ),
  PixivProfileMenuEntry(
    id: 'unmute',
    icon: Icons.person_outline,
    label: (l10n) => l10n.plugin_pixiv_unmute,
    run: _unmute,
    offeredFor: (scope) => !scope.own && scope.muted,
  ),
  PixivProfileMenuEntry(
    id: 'open',
    icon: Icons.open_in_new,
    label: (l10n) => l10n.plugin_pixiv_open_on_pixiv,
    run: _open,
  ),
];

/// A profile's AppBar actions: share the profile link, and the overflow menu.
List<Widget> pixivProfileActions(BuildContext context, PixivProfileScope scope) => [
  IconButton(
    key: const ValueKey('pixiv-profile-share'),
    tooltip: L10n.of(context).share_link,
    icon: const Icon(Icons.share_outlined),
    onPressed: () => SharePlus.instance.share(ShareParams(text: scope.profile.url)),
  ),
  PixivProfileMenu(scope: scope),
];

/// The overflow menu built from [pixivProfileMenuEntries].
class PixivProfileMenu extends StatelessWidget {
  final PixivProfileScope scope;

  const PixivProfileMenu({super.key, required this.scope});

  @override
  Widget build(BuildContext context) => PixivOverflowMenu<PixivProfileMenuEntry>(
    keyPrefix: 'pixiv-profile-menu',
    entries: [
      for (final entry in pixivProfileMenuEntries)
        if (entry.offeredFor(scope)) entry,
    ],
    onSelected: (entry) => entry.run(context, scope),
  );
}
