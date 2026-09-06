import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/plugin_connection_store.dart';
import 'package:xta/settings/settings_chrome.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/deepmarks/deepmarks_client.dart';
import 'package:xta/plugins/deepmarks/deepmarks_save.dart';
import 'package:xta/plugins/deepmarks/nostr_event.dart';
import 'package:xta/utils/urls.dart';

/// API key plus the signing key, with a probe that checks the key really works
/// and warns when the two belong to different accounts.
class DeepmarksSettingsScreen extends StatefulWidget {
  const DeepmarksSettingsScreen({super.key});

  @override
  State<DeepmarksSettingsScreen> createState() => _DeepmarksSettingsScreenState();
}

class _DeepmarksSettingsScreenState extends State<DeepmarksSettingsScreen> {
  late final TextEditingController _apiKeyController;
  late final TextEditingController _secretController;
  late final TextEditingController _baseController;
  final _connection = PluginConnectionStore();
  bool get _obscureKey => _connection.state.obscureKey;
  bool get _obscureSecret => _connection.state.obscureSecret;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _apiKeyController = TextEditingController(text: prefs.get<String>(optionPluginDeepmarksApiKey) ?? '');
    _secretController = TextEditingController(text: prefs.get<String>(optionPluginDeepmarksSecretKey) ?? '');
    _baseController = TextEditingController(text: prefs.get<String>(optionPluginDeepmarksApiBase) ?? '');
  }

  @override
  void dispose() {
    _connection.invalidate();
    _connection.destroy();
    _apiKeyController.dispose();
    _secretController.dispose();
    _baseController.dispose();
    super.dispose();
  }

  /// The identity the stored key signs as, or null when it cannot be read.
  String? get _publicKey {
    try {
      return nostrPublicKey(normaliseNostrSecretKey(_secretController.text));
    } catch (_) {
      return null;
    }
  }

  Future<void> _save() async {
    final apiKey = _apiKeyController.text.trim();
    final secret = _secretController.text.trim();
    final base = _baseController.text.trim();
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionPluginDeepmarksApiKey, apiKey);
    await prefs.set(optionPluginDeepmarksSecretKey, secret);
    await prefs.set(optionPluginDeepmarksApiBase, base);
  }

  Future<void> _test() async {
    final baseUrl = _baseController.text;
    final apiKey = _apiKeyController.text;
    final secret = _secretController.text.trim();
    final mine = _publicKey;
    final revision = _connection.begin();
    await _save();
    if (!mounted || !_connection.isCurrent(revision)) return;
    final l10n = L10n.of(context);
    if (secret.isNotEmpty && mine == null) {
      _connection.finish(revision, PluginConnectionStatus.failed, l10n.plugin_deepmarks_error_secret_key);
      return;
    }
    try {
      final owner = await context.read<DeepmarksClient>().verify(baseUrl: baseUrl, apiKey: apiKey);
      if (!mounted) return;
      if (owner != null && mine != null && owner != mine) {
        _connection.finish(revision, PluginConnectionStatus.failed, l10n.plugin_deepmarks_error_key_mismatch);
        return;
      }
      _connection.finish(revision, owner == null ? PluginConnectionStatus.warning : PluginConnectionStatus.ok,
        owner == null ? l10n.plugin_deepmarks_test_ok_unverified : l10n.plugin_deepmarks_test_ok);
    } on DeepmarksException catch (e) {
      if (!mounted) return;
      _connection.finish(revision, PluginConnectionStatus.failed, deepmarksErrorMessage(l10n, e.kind));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return SettingsPageScaffold(
      title: l10n.plugin_deepmarks_title,
      body: ScopedBuilder<PluginConnectionStore, PluginConnectionState>(store: _connection,
        onState: (context, state) => SettingsList(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.plugin_deepmarks_settings_intro, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 12),
          Card(
            margin: EdgeInsets.zero,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Icon(Icons.info_outline, size: 18, color: theme.colorScheme.primary),
                  const SizedBox(width: 10),
                  Expanded(child: Text(l10n.plugin_deepmarks_lifetime_notice, style: theme.textTheme.bodySmall)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),
          Semantics(header: true, child: Text(l10n.plugin_integration_connection,
            style: theme.textTheme.titleMedium)),
          const SizedBox(height: 16),
          TextField(
            controller: _apiKeyController,
            obscureText: _obscureKey,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_deepmarks_api_key,
              helperMaxLines: 5,
              helperText: l10n.plugin_deepmarks_api_key_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(_obscureKey ? Icons.visibility : Icons.visibility_off),
                tooltip: _obscureKey ? l10n.show : l10n.hide,
                onPressed: () => _connection.toggleVisibility(),
              ),
            ),
            onChanged: (_) => _connection.invalidate(),
          ),
          const SizedBox(height: 16),
          Semantics(header: true, child: Text(l10n.plugin_integration_identity,
            style: theme.textTheme.titleMedium)),
          const SizedBox(height: 12),
          TextField(
            controller: _secretController,
            obscureText: _obscureSecret,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_deepmarks_secret_key,
              helperMaxLines: 5,
              helperText: l10n.plugin_deepmarks_secret_key_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(_obscureSecret ? Icons.visibility : Icons.visibility_off),
                tooltip: _obscureSecret ? l10n.show : l10n.hide,
                onPressed: () => _connection.toggleVisibility(secret: true),
              ),
            ),
            onChanged: (_) => _connection.invalidate(),
          ),
          if (_publicKey != null) ...[
            const SizedBox(height: 8),
            SelectableText(
              l10n.plugin_deepmarks_signing_as(_publicKey!),
              style: theme.textTheme.bodySmall,
            ),
          ],
          const SizedBox(height: 16),
          TextField(
            controller: _baseController,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_deepmarks_api_base,
              helperText: deepmarksDefaultApiBase,
              border: const OutlineInputBorder(),
            ),
            onChanged: (_) => _connection.invalidate(),
          ),
          const SizedBox(height: 20),
          PluginConnectionActions(state: state, testLabel: l10n.plugin_deepmarks_test,
            onTest: _test, onSave: () async {
              await _save();
              if (context.mounted) Navigator.pop(context);
            }),
          PluginConnectionFeedback(state: state),
          const SizedBox(height: 28),
          TextButton.icon(
            onPressed: () => openUri(context, 'https://github.com/ostermayer/deepmarks-public'),
            icon: const Icon(Icons.open_in_new, size: 18),
            label: Text(l10n.plugin_deepmarks_learn_more),
          ),
        ],
      )),
    );
  }
}
