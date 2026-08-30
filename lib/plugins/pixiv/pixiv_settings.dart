import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_auth.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_login_webview.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/ui/errors.dart';

String pixivErrorMessage(L10n l10n, Object error) {
  if (error is! PixivException) {
    return l10n.plugin_pixiv_error_network;
  }
  return switch (error.kind) {
    PixivErrorKind.notConfigured => l10n.plugin_pixiv_not_configured,
    PixivErrorKind.network => l10n.plugin_pixiv_error_network,
    PixivErrorKind.unauthorized => l10n.plugin_pixiv_error_unauthorized,
    PixivErrorKind.rateLimited => l10n.plugin_pixiv_error_rate_limited,
    PixivErrorKind.notFound => l10n.plugin_pixiv_error_not_found,
    PixivErrorKind.badResponse => l10n.plugin_pixiv_error_response,
  };
}

/// Opens the Pixiv login webview and stores tokens on success.
///
/// Returns the signed-in user, or null when cancelled or failed.
Future<PixivAuthUser?> runPixivSignIn(BuildContext context) async {
  final pkce = PixivAuth.generatePkce();
  final code = await Navigator.push<String>(
    context,
    MaterialPageRoute(
      builder: (_) => PixivLoginWebview(codeChallenge: pkce.challenge),
    ),
  );
  if (code == null || !context.mounted) {
    return null;
  }

  try {
    final tokens = await PixivAuth().exchangeCode(
      code: code,
      codeVerifier: pkce.verifier,
    );
    if (!context.mounted) {
      return null;
    }
    final user = await context.read<PixivClient>().applyLoginTokens(tokens);
    if (context.mounted) {
      showSnackBar(
        context,
        icon: '✅',
        message: L10n.of(context).plugin_pixiv_signed_in(user.displayName),
      );
    }
    return user;
  } on PixivException catch (_) {
    if (context.mounted) {
      showSnackBar(
        context,
        icon: '🔒',
        message: L10n.of(context).plugin_pixiv_sign_in_failed,
      );
    }
    return null;
  }
}

/// Refresh token and content filters for the private Pixiv plugin.
class PixivSettingsScreen extends StatefulWidget {
  const PixivSettingsScreen({super.key});

  @override
  State<PixivSettingsScreen> createState() => _PixivSettingsScreenState();
}

class _PixivSettingsScreenState extends State<PixivSettingsScreen> {
  late final TextEditingController _token;
  bool _hidden = true;
  bool _testing = false;
  bool _signingIn = false;
  late bool _showR18;
  String? _signedInName;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _token = TextEditingController(
      text: prefs.get<String>(optionPluginPixivRefreshToken) ?? '',
    );
    _showR18 = prefs.get<bool>(optionPluginPixivShowR18) == true;
    _loadSignedInName();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<PixivMuteStore>().load();
      }
    });
  }

  Future<void> _loadSignedInName() async {
    final prefs = PrefService.of(context, listen: false);
    if ((prefs.get<String>(optionPluginPixivRefreshToken) ?? '')
        .trim()
        .isEmpty) {
      return;
    }
    try {
      final user = await context.read<PixivClient>().verify();
      if (mounted) {
        setState(() => _signedInName = user.displayName);
        _token.text = prefs.get<String>(optionPluginPixivRefreshToken) ?? '';
      }
    } catch (_) {
      // Leave name blank; test connection will surface errors.
    }
  }

  @override
  void dispose() {
    _token.dispose();
    super.dispose();
  }

  bool get _signedIn =>
      (PrefService.of(
                context,
                listen: false,
              ).get<String>(optionPluginPixivRefreshToken) ??
              '')
          .trim()
          .isNotEmpty;

  Future<void> _saveToken() async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionPluginPixivRefreshToken, _token.text.trim());
    await prefs.set(optionPluginPixivAccessToken, '');
    await prefs.set(optionPluginPixivAccessExpiresAt, '');
  }

  Future<void> _signIn() async {
    setState(() => _signingIn = true);
    try {
      final user = await runPixivSignIn(context);
      if (mounted) {
        final prefs = PrefService.of(context, listen: false);
        setState(() {
          _signedInName = user?.displayName;
          _token.text = prefs.get<String>(optionPluginPixivRefreshToken) ?? '';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _signingIn = false);
      }
    }
  }

  Future<void> _signOut() async {
    await context.read<PixivClient>().signOut();
    if (mounted) {
      context.read<PixivFeedStore>().update(const []);
      context.read<PixivBookmarkStore>().update(const {});
      setState(() {
        _signedInName = null;
        _token.text = '';
      });
    }
  }

  Future<void> _test() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final client = context.read<PixivClient>();

    await _saveToken();
    setState(() => _testing = true);
    String message;
    try {
      final user = await client.verify();
      message = l10n.plugin_pixiv_signed_in(user.displayName);
      if (mounted) {
        setState(() => _signedInName = user.displayName);
      }
    } catch (e) {
      message = pixivErrorMessage(l10n, e);
    }

    if (mounted) {
      setState(() => _testing = false);
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final prefs = PrefService.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_pixiv_title)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            l10n.plugin_pixiv_settings_intro,
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 20),
          if (_signedIn && _signedInName != null && _signedInName!.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(
                l10n.plugin_pixiv_signed_in(_signedInName!),
                style: theme.textTheme.titleSmall,
              ),
            ),
          Row(
            children: [
              FilledButton(
                onPressed: _signingIn || _signedIn ? null : _signIn,
                child: _signingIn
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(l10n.plugin_pixiv_sign_in),
              ),
              if (_signedIn) ...[
                const SizedBox(width: 12),
                TextButton(
                  onPressed: _signOut,
                  child: Text(l10n.plugin_pixiv_sign_out),
                ),
              ],
            ],
          ),
          const SizedBox(height: 28),
          Text(
            l10n.plugin_pixiv_advanced_token,
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _token,
            obscureText: _hidden,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              hintText: l10n.plugin_pixiv_refresh_token_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(_hidden ? Icons.visibility : Icons.visibility_off),
                onPressed: () => setState(() => _hidden = !_hidden),
              ),
            ),
            onChanged: (_) => _saveToken(),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.tonal(
              onPressed: _testing ? null : _test,
              child: _testing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(l10n.plugin_pixiv_test),
            ),
          ),
          const SizedBox(height: 28),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.plugin_pixiv_show_r18),
            subtitle: Text(l10n.plugin_pixiv_show_r18_description),
            value: _showR18,
            onChanged: (value) async {
              await prefs.set(optionPluginPixivShowR18, value);
              setState(() => _showR18 = value);
            },
          ),
          const SizedBox(height: 28),
          _mutedSection(l10n, theme),
        ],
      ),
    );
  }

  Widget _mutedSection(L10n l10n, ThemeData theme) {
    return ScopedBuilder<PixivMuteStore, PixivMuteState>.transition(
      store: context.read<PixivMuteStore>(),
      onState: (context, state) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.plugin_pixiv_muted_section,
              style: theme.textTheme.titleSmall,
            ),
            const SizedBox(height: 8),
            if (state.isEmpty)
              Text(l10n.plugin_pixiv_muted_empty)
            else
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final id in state.authorIds)
                    _muteChip(
                      Icons.person_off_outlined,
                      '$id',
                      () => _unmuteAuthor(id),
                    ),
                  for (final tag in state.tags)
                    _muteChip(
                      Icons.label_off_outlined,
                      '#$tag',
                      () => _unmuteTag(tag),
                    ),
                  for (final id in state.illustIds)
                    _muteChip(
                      Icons.hide_image_outlined,
                      '$id',
                      () => _unmuteIllust(id),
                    ),
                ],
              ),
          ],
        );
      },
    );
  }

  Widget _muteChip(IconData icon, String label, VoidCallback onDeleted) {
    return InputChip(
      avatar: Icon(icon, size: 18),
      label: Text(label),
      onDeleted: onDeleted,
      deleteButtonTooltipMessage: L10n.of(context).plugin_pixiv_unmute,
    );
  }

  void _unmuteAuthor(int id) {
    context.read<PixivMuteStore>().unmuteAuthor(id);
  }

  void _unmuteTag(String tag) {
    context.read<PixivMuteStore>().unmuteTag(tag);
  }

  void _unmuteIllust(int id) {
    context.read<PixivMuteStore>().unmuteIllust(id);
  }
}

/// Flare-style More list: account, history, preferences, mute, about, logout.
class PixivMorePane extends StatefulWidget {
  final VoidCallback onAuthChanged;

  const PixivMorePane({super.key, required this.onAuthChanged});

  @override
  State<PixivMorePane> createState() => _PixivMorePaneState();
}

class _PixivMorePaneState extends State<PixivMorePane> {
  String? _signedInName;
  var _signingIn = false;
  var _revealToken = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadName());
  }

  bool get _signedIn =>
      (PrefService.of(
                context,
                listen: false,
              ).get<String>(optionPluginPixivRefreshToken) ??
              '')
          .trim()
          .isNotEmpty;

  Future<void> _loadName() async {
    if (!_signedIn) return;
    try {
      final user = await context.read<PixivClient>().verify();
      if (mounted) setState(() => _signedInName = user.displayName);
    } catch (_) {}
  }

  String _maskedToken(String raw) {
    if (raw.length < 8) return '••••';
    return '${raw.substring(0, 4)}••••${raw.substring(raw.length - 4)}';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context, listen: false);
    final token = (prefs.get<String>(optionPluginPixivRefreshToken) ?? '')
        .trim();
    final name = _signedInName;

    return ListView(
      children: [
        ListTile(
          leading: const Icon(Icons.person_outline),
          title: Text(l10n.plugin_pixiv_more_account),
          subtitle: Text(
            _revealToken && token.isNotEmpty
                ? _maskedToken(token)
                : _signedIn
                ? (name == null || name.isEmpty
                      ? l10n.plugin_pixiv_title
                      : l10n.plugin_pixiv_signed_in(name))
                : l10n.plugin_pixiv_not_configured,
          ),
          trailing: _signedIn
              ? TextButton(
                  onPressed: () => setState(() => _revealToken = !_revealToken),
                  child: Text(l10n.plugin_pixiv_reveal),
                )
              : null,
          onTap: _signedIn
              ? () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const PixivSettingsScreen(),
                  ),
                )
              : () async {
                  setState(() => _signingIn = true);
                  try {
                    await runPixivSignIn(context);
                    if (mounted) {
                      await _loadName();
                      widget.onAuthChanged();
                    }
                  } finally {
                    if (mounted) setState(() => _signingIn = false);
                  }
                },
        ),
        if (_signingIn)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Center(
              child: SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          ),
        ListTile(
          leading: const Icon(Icons.history),
          title: Text(l10n.plugin_pixiv_search_history),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const PixivSearchScreen()),
          ),
        ),
        ListTile(
          leading: const Icon(Icons.tune),
          title: Text(l10n.plugin_pixiv_more_preferences),
          onTap: () async {
            await Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const PixivSettingsScreen()),
            );
            if (mounted) widget.onAuthChanged();
          },
        ),
        ListTile(
          leading: const Icon(Icons.volume_off_outlined),
          title: Text(l10n.plugin_pixiv_more_mute),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const PixivSettingsScreen()),
          ),
        ),
        ListTile(
          leading: const Icon(Icons.info_outline),
          title: Text(l10n.plugin_pixiv_more_about),
          subtitle: Text(l10n.plugin_pixiv_description),
        ),
        if (_signedIn)
          ListTile(
            leading: const Icon(Icons.logout),
            title: Text(l10n.plugin_pixiv_sign_out),
            onTap: () async {
              await context.read<PixivClient>().signOut();
              if (!mounted) return;
              context.read<PixivFeedStore>().update(const []);
              context.read<PixivBookmarkStore>().update(const {});
              setState(() {
                _signedInName = null;
                _revealToken = false;
              });
              widget.onAuthChanged();
            },
          ),
      ],
    );
  }
}
