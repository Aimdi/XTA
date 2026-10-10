import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_account_list.dart';
import 'package:xta/plugins/pixiv/pixiv_accounts.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// What the account controls show while the reader signs in, tests the token
/// or reveals it.
class PixivAccountView {
  final String? name;
  final bool signingIn;
  final bool testing;
  final bool tokenShown;

  const PixivAccountView({this.name, this.signingIn = false, this.testing = false, this.tokenShown = false});

  PixivAccountView copyWith({String? name, bool clearName = false, bool? signingIn, bool? testing, bool? tokenShown}) =>
      PixivAccountView(
        name: clearName ? null : name ?? this.name,
        signingIn: signingIn ?? this.signingIn,
        testing: testing ?? this.testing,
        tokenShown: tokenShown ?? this.tokenShown,
      );
}

bool pixivSignedIn(BasePrefService prefs) => (prefs.get<String>(optionPluginPixivRefreshToken) ?? '').trim().isNotEmpty;

/// The signed-in account's name, or null when the token does not work now.
/// A working account is kept among the stored ones, which is how an account
/// signed in before several were kept joins them.
Future<String?> pixivVerifiedName(PixivClient client) async {
  try {
    final user = await client.verify();
    await rememberPixivAccount(client.prefs, user);
    return user.displayName;
  } catch (_) {
    return null;
  }
}

/// The stored accounts with switching, adding and signing out, and the
/// refresh token for readers who paste one.
class PixivAccountSettings extends StatefulWidget {
  const PixivAccountSettings({super.key});

  @override
  State<PixivAccountSettings> createState() => _PixivAccountSettingsState();
}

class _PixivAccountSettingsState extends State<PixivAccountSettings> {
  late final TextEditingController _token;
  late final PixivAccountsStore _accounts;
  final _view = PluginViewStore(const PixivAccountView());

  BasePrefService get _prefs => PrefService.of(context, listen: false);

  @override
  void initState() {
    super.initState();
    _accounts = PixivAccountsStore(context.read<PixivClient>())..load();
    _token = TextEditingController(text: _prefs.get<String>(optionPluginPixivRefreshToken) ?? '');
    _loadSignedInName();
  }

  @override
  void dispose() {
    _token.dispose();
    _view.destroy();
    _accounts.destroy();
    super.dispose();
  }

  void _syncToken() => _token.text = _prefs.get<String>(optionPluginPixivRefreshToken) ?? '';

  void _syncAccounts() {
    if (!mounted) return;
    _accounts.load();
    _view.select(_view.state.copyWith(name: _accounts.active?.displayName, clearName: _accounts.active == null));
    _syncToken();
  }

  Future<void> _loadSignedInName() async {
    if (!pixivSignedIn(_prefs)) return;
    final name = await pixivVerifiedName(context.read<PixivClient>());
    if (!mounted || name == null) return;
    _accounts.load();
    _view.select(_view.state.copyWith(name: name));
    _syncToken();
  }

  Future<void> _saveToken() async {
    await _prefs.set(optionPluginPixivRefreshToken, _token.text.trim());
    await _prefs.set(optionPluginPixivAccessToken, '');
    await _prefs.set(optionPluginPixivAccessExpiresAt, '');
  }

  Future<void> _signIn() async {
    _view.select(_view.state.copyWith(signingIn: true));
    try {
      final user = await runPixivSignIn(context);
      if (mounted && user != null) _syncAccounts();
    } finally {
      if (mounted) _view.select(_view.state.copyWith(signingIn: false));
    }
  }

  Future<void> _signOut() async {
    if (await confirmPixivSignOut(context, _accounts)) _syncAccounts();
  }

  Future<void> _test() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final client = context.read<PixivClient>();
    await _saveToken();
    _view.select(_view.state.copyWith(testing: true));
    String message;
    try {
      final user = await client.verify();
      await _accounts.remember(user);
      message = l10n.plugin_pixiv_signed_in(user.displayName);
      if (mounted) _view.select(_view.state.copyWith(name: user.displayName));
    } catch (e) {
      message = pixivErrorMessage(l10n, e);
    }
    if (mounted) {
      _view.select(_view.state.copyWith(testing: false));
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginViewStore<PixivAccountView>, PixivAccountView>(
    store: _view,
    onState: (context, view) => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [..._signInControls(context, view), const SizedBox(height: 28), ..._tokenControls(context, view)],
    ),
  );

  List<Widget> _signInControls(BuildContext context, PixivAccountView view) {
    final l10n = L10n.of(context);
    final signedIn = pixivSignedIn(PrefService.of(context));
    final name = view.name;
    return [
      if (signedIn && name != null && name.isNotEmpty)
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Text(l10n.plugin_pixiv_signed_in(name), style: Theme.of(context).textTheme.titleSmall),
        ),
      PixivAccountList(store: _accounts, onChanged: _syncAccounts),
      Wrap(
        spacing: 12,
        runSpacing: 8,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          if (signedIn)
            OutlinedButton.icon(
              onPressed: view.signingIn ? null : _signIn,
              icon: view.signingIn ? const _Spinner() : const Icon(Icons.person_add_alt_1_outlined),
              label: Text(l10n.add_account),
            )
          else
            FilledButton(
              onPressed: view.signingIn ? null : _signIn,
              child: view.signingIn ? const _Spinner() : Text(l10n.plugin_pixiv_sign_in),
            ),
          if (signedIn) TextButton(onPressed: _signOut, child: Text(l10n.plugin_pixiv_sign_out)),
        ],
      ),
    ];
  }

  List<Widget> _tokenControls(BuildContext context, PixivAccountView view) {
    final l10n = L10n.of(context);
    return [
      Text(l10n.plugin_pixiv_advanced_token, style: Theme.of(context).textTheme.titleSmall),
      const SizedBox(height: 8),
      TextField(
        controller: _token,
        obscureText: !view.tokenShown,
        autocorrect: false,
        enableSuggestions: false,
        decoration: InputDecoration(
          hintText: l10n.plugin_pixiv_refresh_token_hint,
          border: const OutlineInputBorder(),
          suffixIcon: IconButton(
            tooltip: view.tokenShown ? null : l10n.plugin_pixiv_reveal,
            icon: Icon(view.tokenShown ? Icons.visibility_off : Icons.visibility),
            onPressed: () => _view.select(view.copyWith(tokenShown: !view.tokenShown)),
          ),
        ),
        onChanged: (_) => _saveToken(),
      ),
      const SizedBox(height: 12),
      Align(
        alignment: Alignment.centerLeft,
        child: FilledButton.tonal(
          onPressed: view.testing ? null : _test,
          child: view.testing ? const _Spinner() : Text(l10n.plugin_pixiv_test),
        ),
      ),
    ];
  }
}

class _Spinner extends StatelessWidget {
  const _Spinner();

  @override
  Widget build(BuildContext context) =>
      const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2));
}
