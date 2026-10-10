import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_account.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// Flare-style More list: account, history, preferences, mute, about, logout.
class PixivMorePane extends StatefulWidget {
  final VoidCallback onAuthChanged;

  const PixivMorePane({super.key, required this.onAuthChanged});

  @override
  State<PixivMorePane> createState() => _PixivMorePaneState();
}

class _PixivMorePaneState extends State<PixivMorePane> {
  final _view = PluginViewStore(const PixivAccountView());

  bool get _signedIn => pixivSignedIn(PrefService.of(context, listen: false));

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadName());
  }

  @override
  void dispose() {
    _view.destroy();
    super.dispose();
  }

  Future<void> _loadName() async {
    if (!mounted || !_signedIn) return;
    final name = await pixivVerifiedName(context.read<PixivClient>());
    if (mounted && name != null) _view.select(_view.state.copyWith(name: name));
  }

  String _maskedToken(String raw) {
    if (raw.length < 8) return '••••';
    return '${raw.substring(0, 4)}••••${raw.substring(raw.length - 4)}';
  }

  Future<void> _signIn() async {
    _view.select(_view.state.copyWith(signingIn: true));
    try {
      await runPixivSignIn(context);
      if (mounted) {
        await _loadName();
        widget.onAuthChanged();
      }
    } finally {
      if (mounted) _view.select(_view.state.copyWith(signingIn: false));
    }
  }

  Future<void> _openSettings() async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => const PixivSettingsScreen()));
    if (mounted) widget.onAuthChanged();
  }

  Future<void> _signOut() async {
    await pixivSignOut(context);
    if (!mounted) return;
    _view.select(_view.state.copyWith(clearName: true, tokenShown: false));
    widget.onAuthChanged();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginViewStore<PixivAccountView>, PixivAccountView>(
    store: _view,
    onState: (context, view) => ListView(children: _entries(context, view)),
  );

  List<Widget> _entries(BuildContext context, PixivAccountView view) {
    final l10n = L10n.of(context);
    return [
      _account(context, view),
      if (view.signingIn)
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 8),
          child: Center(child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))),
        ),
      ListTile(
        leading: const Icon(Icons.history),
        title: Text(l10n.plugin_pixiv_search_history),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PixivSearchScreen())),
      ),
      ListTile(
        leading: const Icon(Icons.label_outline),
        title: Text(l10n.plugin_pixiv_search_favorite_tags),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PixivFavoriteTagsScreen())),
      ),
      ListTile(leading: const Icon(Icons.tune), title: Text(l10n.plugin_pixiv_more_preferences), onTap: _openSettings),
      ListTile(
        leading: const Icon(Icons.volume_off_outlined),
        title: Text(l10n.plugin_pixiv_more_mute),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PixivSettingsScreen())),
      ),
      ListTile(
        leading: const Icon(Icons.info_outline),
        title: Text(l10n.plugin_pixiv_more_about),
        subtitle: Text(l10n.plugin_pixiv_description),
      ),
      if (_signedIn)
        ListTile(leading: const Icon(Icons.logout), title: Text(l10n.plugin_pixiv_sign_out), onTap: _signOut),
    ];
  }

  Widget _account(BuildContext context, PixivAccountView view) {
    final l10n = L10n.of(context);
    final token = (PrefService.of(context, listen: false).get<String>(optionPluginPixivRefreshToken) ?? '').trim();
    final name = view.name;
    final status = _signedIn
        ? (name == null || name.isEmpty ? l10n.plugin_pixiv_title : l10n.plugin_pixiv_signed_in(name))
        : l10n.plugin_pixiv_not_configured;
    return ListTile(
      leading: const Icon(Icons.person_outline),
      title: Text(l10n.plugin_pixiv_more_account),
      subtitle: Text(view.tokenShown && token.isNotEmpty ? _maskedToken(token) : status),
      trailing: _signedIn
          ? TextButton(
              onPressed: () => _view.select(view.copyWith(tokenShown: !view.tokenShown)),
              child: Text(l10n.plugin_pixiv_reveal),
            )
          : null,
      onTap: _signedIn
          ? () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PixivSettingsScreen()))
          : _signIn,
    );
  }
}
