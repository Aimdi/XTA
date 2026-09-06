import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/plugin_connection_store.dart';
import 'package:xta/settings/settings_chrome.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/karakeep/karakeep_client.dart';
import 'package:xta/utils/urls.dart';

/// Server URL and API key for a self-hosted Karakeep instance, with a probe so
/// the user finds out here rather than the first time they try to save.
class KarakeepSettingsScreen extends StatefulWidget {
  const KarakeepSettingsScreen({super.key});

  @override
  State<KarakeepSettingsScreen> createState() => _KarakeepSettingsScreenState();
}

class _KarakeepSettingsScreenState extends State<KarakeepSettingsScreen> {
  late final TextEditingController _serverController;
  late final TextEditingController _keyController;
  final _connection = PluginConnectionStore();
  bool get _obscureKey => _connection.state.obscureKey;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _serverController = TextEditingController(text: prefs.get<String>(optionPluginKarakeepServerUrl) ?? '');
    _keyController = TextEditingController(text: prefs.get<String>(optionPluginKarakeepApiKey) ?? '');
  }

  @override
  void dispose() {
    _connection.invalidate();
    _connection.destroy();
    _serverController.dispose();
    _keyController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final server = _serverController.text.trim();
    final apiKey = _keyController.text.trim();
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionPluginKarakeepServerUrl, server);
    await prefs.set(optionPluginKarakeepApiKey, apiKey);
  }

  Future<void> _test() async {
    final baseUrl = _serverController.text;
    final apiKey = _keyController.text;
    final revision = _connection.begin();
    await _save();
    if (!mounted || !_connection.isCurrent(revision)) return;
    final l10n = L10n.of(context);
    try {
      await context.read<KarakeepClient>().verify(baseUrl: baseUrl, apiKey: apiKey);
      if (!mounted) return;
      _connection.finish(revision, PluginConnectionStatus.ok, l10n.plugin_karakeep_test_ok);
    } on KarakeepException catch (e) {
      if (!mounted) return;
      _connection.finish(revision, PluginConnectionStatus.failed, switch (e.kind) {
        KarakeepErrorKind.notConfigured => l10n.plugin_karakeep_not_configured,
        KarakeepErrorKind.unauthorized => l10n.plugin_karakeep_error_unauthorized,
        KarakeepErrorKind.badServer => l10n.plugin_karakeep_error_server_url,
        KarakeepErrorKind.network => l10n.plugin_karakeep_error_network,
        KarakeepErrorKind.server => l10n.plugin_karakeep_error_generic,
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return SettingsPageScaffold(
      title: l10n.plugin_karakeep_title,
      body: ScopedBuilder<PluginConnectionStore, PluginConnectionState>(store: _connection,
        onState: (context, state) => SettingsList(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.plugin_karakeep_settings_intro, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 20),
          Semantics(header: true, child: Text(l10n.plugin_integration_connection,
            style: theme.textTheme.titleMedium)),
          const SizedBox(height: 16),
          TextField(
            controller: _serverController,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_karakeep_server_url,
              helperMaxLines: 5,
              helperText: l10n.plugin_karakeep_server_url_hint,
              border: const OutlineInputBorder(),
            ),
            onChanged: (_) => _connection.invalidate(),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _keyController,
            obscureText: _obscureKey,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_karakeep_api_key,
              helperMaxLines: 5,
              helperText: l10n.plugin_karakeep_api_key_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(_obscureKey ? Icons.visibility : Icons.visibility_off),
                tooltip: _obscureKey ? l10n.show : l10n.hide,
                onPressed: () => _connection.toggleVisibility(),
              ),
            ),
            onChanged: (_) => _connection.invalidate(),
          ),
          const SizedBox(height: 20),
          PluginConnectionActions(state: state, testLabel: l10n.plugin_karakeep_test,
            onTest: _test, onSave: () async {
              await _save();
              if (context.mounted) Navigator.pop(context);
            }),
          PluginConnectionFeedback(state: state),
          const SizedBox(height: 28),
          TextButton.icon(
            onPressed: () => openUri(context, 'https://karakeep.app'),
            icon: const Icon(Icons.open_in_new, size: 18),
            label: Text(l10n.plugin_karakeep_learn_more),
          ),
        ],
      )),
    );
  }
}
