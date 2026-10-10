import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/downloads/downloads_screen.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_account_list.dart';
import 'package:xta/plugins/pixiv/pixiv_accounts.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_history_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_account.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_mute.dart';
import 'package:xta/plugins/pixiv/pixivision_list_screen.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/utils/urls.dart';

/// One line of the More pane. A feature adds its line to [pixivMoreEntries]
/// rather than to the pane.
class PixivMoreEntry {
  /// Stable name; the line is keyed `pixiv-more-<id>`.
  final String id;
  final IconData icon;
  final String Function(L10n l10n) label;
  final Future<void> Function(BuildContext context) open;

  /// Shown only while an account is signed in.
  final bool needsAccount;

  const PixivMoreEntry({
    required this.id,
    required this.icon,
    required this.label,
    required this.open,
    this.needsAccount = false,
  });
}

Future<void> _push(BuildContext context, Widget screen) =>
    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => screen));

Future<void> _openHistory(BuildContext context) => _push(context, const PixivHistoryScreen());

Future<void> _openFavoriteTags(BuildContext context) => _push(context, const PixivFavoriteTagsScreen());

Future<void> _openDownloads(BuildContext context) => _push(context, const DownloadsScreen());

Future<void> _openPreferences(BuildContext context) => _push(context, const PixivSettingsScreen());

Future<void> _openMute(BuildContext context) => _push(context, const PixivMuteScreen());

Future<void> _manageOnPixiv(BuildContext context) => openUri(context, 'https://www.pixiv.net/settings/account');

final pixivMoreEntries = <PixivMoreEntry>[
  PixivMoreEntry(id: 'history', icon: Icons.history, label: (l10n) => l10n.plugin_pixiv_history, open: _openHistory),
  PixivMoreEntry(
    id: 'favorite-tags',
    icon: Icons.label_outline,
    label: (l10n) => l10n.plugin_pixiv_search_favorite_tags,
    open: _openFavoriteTags,
  ),
  PixivMoreEntry(
    id: 'pixivision',
    icon: Icons.article_outlined,
    label: (l10n) => l10n.plugin_pixiv_pixivision_articles,
    open: openPixivisionList,
    needsAccount: true,
  ),
  PixivMoreEntry(
    id: 'downloads',
    icon: Icons.download_outlined,
    label: (l10n) => l10n.downloads_title,
    open: _openDownloads,
  ),
  PixivMoreEntry(
    id: 'preferences',
    icon: Icons.tune,
    label: (l10n) => l10n.plugin_pixiv_more_preferences,
    open: _openPreferences,
  ),
  PixivMoreEntry(
    id: 'mute',
    icon: Icons.volume_off_outlined,
    label: (l10n) => l10n.plugin_pixiv_more_mute,
    open: _openMute,
  ),
  PixivMoreEntry(
    id: 'manage',
    icon: Icons.manage_accounts_outlined,
    label: (l10n) => l10n.plugin_pixiv_manage_account,
    open: _manageOnPixiv,
    needsAccount: true,
  ),
];

/// The Pixiv hub: the account header with its switcher, then [pixivMoreEntries],
/// About and Sign out.
class PixivMorePane extends StatefulWidget {
  final VoidCallback onAuthChanged;

  /// The pane's list controller, so tapping More again scrolls it to the top.
  final ScrollController? scrollController;

  const PixivMorePane({super.key, required this.onAuthChanged, this.scrollController});

  @override
  State<PixivMorePane> createState() => _PixivMorePaneState();
}

class _PixivMorePaneState extends State<PixivMorePane> {
  final _view = PluginViewStore(const PixivAccountView());
  late final PixivAccountsStore _accounts;

  @override
  void initState() {
    super.initState();
    _accounts = PixivAccountsStore(context.read<PixivClient>())..load();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadName());
  }

  @override
  void dispose() {
    _view.destroy();
    _accounts.destroy();
    super.dispose();
  }

  /// Confirms the token and keeps the account among the stored ones, so the
  /// header shows its name and picture.
  Future<void> _loadName() async {
    if (!mounted || !_accounts.signedIn) return;
    final name = await pixivVerifiedName(context.read<PixivClient>());
    if (!mounted || name == null) return;
    _accounts.load();
    _view.select(_view.state.copyWith(name: name));
  }

  void _changed() {
    if (!mounted) return;
    _accounts.load();
    _view.select(_view.state.copyWith(name: _accounts.active?.displayName, clearName: !_accounts.signedIn));
    widget.onAuthChanged();
  }

  Future<void> _signIn() async {
    _view.select(_view.state.copyWith(signingIn: true));
    try {
      await runPixivSignIn(context);
      _changed();
    } finally {
      if (mounted) _view.select(_view.state.copyWith(signingIn: false));
    }
  }

  Future<void> _signOut() async {
    if (await confirmPixivSignOut(context, _accounts)) _changed();
  }

  Future<void> _openSwitcher() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => PixivAccountSwitcher(
        store: _accounts,
        onAdd: () {
          Navigator.pop(sheetContext);
          _signIn();
        },
      ),
    );
    _changed();
  }

  Future<void> _open(PixivMoreEntry entry) async {
    await entry.open(context);
    _changed();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginViewStore<PixivAccountView>, PixivAccountView>(
    store: _view,
    onState: (context, view) => ListView(
      controller: pluginInnerScrollController(context, widget.scrollController),
      primary: PluginEmbedded.maybeOf(context) ? false : null,
      children: _entries(context, view),
    ),
  );

  List<Widget> _entries(BuildContext context, PixivAccountView view) {
    final l10n = L10n.of(context);
    final signedIn = _accounts.signedIn;
    return [
      PixivAccountHeader(
        account: _accounts.active,
        name: view.name,
        signedIn: signedIn,
        signingIn: view.signingIn,
        onSignIn: _signIn,
        onSwitch: _openSwitcher,
      ),
      const Divider(height: 1),
      for (final entry in pixivMoreEntries)
        if (signedIn || !entry.needsAccount)
          ListTile(
            key: ValueKey('pixiv-more-${entry.id}'),
            leading: Icon(entry.icon),
            title: Text(entry.label(l10n)),
            onTap: () => _open(entry),
          ),
      ListTile(
        leading: const Icon(Icons.info_outline),
        title: Text(l10n.plugin_pixiv_more_about),
        subtitle: Text(l10n.plugin_pixiv_description),
      ),
      if (signedIn)
        ListTile(leading: const Icon(Icons.logout), title: Text(l10n.plugin_pixiv_sign_out), onTap: _signOut),
    ];
  }
}

/// Who is signed in: avatar, name, @account and a Premium badge, with the
/// account switcher; or the way to sign in.
class PixivAccountHeader extends StatelessWidget {
  final PixivAccount? account;
  final String? name;
  final bool signedIn;
  final bool signingIn;
  final VoidCallback onSignIn;
  final VoidCallback onSwitch;

  const PixivAccountHeader({
    super.key,
    required this.account,
    required this.name,
    required this.signedIn,
    required this.signingIn,
    required this.onSignIn,
    required this.onSwitch,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    if (!signedIn) {
      return ListTile(
        leading: const Icon(Icons.person_outline),
        title: Text(l10n.plugin_pixiv_sign_in),
        subtitle: Text(l10n.plugin_pixiv_not_configured),
        trailing: signingIn
            ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : null,
        onTap: signingIn ? null : onSignIn,
      );
    }
    final known = account;
    final shownName = known?.displayName ?? name ?? l10n.plugin_pixiv_more_account;
    return ListTile(
      key: const ValueKey('pixiv-account-header'),
      contentPadding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
      leading: PixivAvatar(userId: known?.userId ?? 0, name: shownName, url: known?.avatarUrl, size: 48),
      title: Text(shownName, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: _details(context, known),
      trailing: IconButton(
        tooltip: l10n.plugin_pixiv_switch_account,
        icon: const Icon(Icons.switch_account_outlined),
        onPressed: onSwitch,
      ),
      onTap: onSwitch,
    );
  }

  Widget? _details(BuildContext context, PixivAccount? known) {
    if (known == null || (known.account.isEmpty && !known.isPremium)) return null;
    return Wrap(
      spacing: 8,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [if (known.account.isNotEmpty) Text('@${known.account}'), if (known.isPremium) const _PremiumBadge()],
    );
  }
}

class _PremiumBadge extends StatelessWidget {
  const _PremiumBadge();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(color: scheme.tertiaryContainer, borderRadius: BorderRadius.circular(4)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        child: Text(
          L10n.of(context).plugin_pixiv_premium,
          style: Theme.of(context).textTheme.labelSmall!.copyWith(color: scheme.onTertiaryContainer),
        ),
      ),
    );
  }
}

/// The bottom sheet behind the header: every stored account and Add account.
class PixivAccountSwitcher extends StatelessWidget {
  final PixivAccountsStore store;
  final VoidCallback onAdd;

  const PixivAccountSwitcher({super.key, required this.store, required this.onAdd});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(l10n.plugin_pixiv_switch_account, style: Theme.of(context).textTheme.titleMedium),
            ),
            PixivAccountList(store: store, onChanged: () => Navigator.maybePop(context)),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.person_add_alt_1_outlined),
              title: Text(l10n.add_account),
              onTap: onAdd,
            ),
          ],
        ),
      ),
    );
  }
}
