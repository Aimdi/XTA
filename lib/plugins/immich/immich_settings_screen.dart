import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/immich/immich_client.dart';
import 'package:xta/utils/urls.dart';

/// Server URL and API key for a self-hosted Immich instance, with a probe so the
/// user finds out here rather than the first time they file a bookmark.
class ImmichSettingsScreen extends StatefulWidget {
  const ImmichSettingsScreen({super.key});

  @override
  State<ImmichSettingsScreen> createState() => _ImmichSettingsScreenState();
}

enum _ProbeState { idle, running, ok, failed }

class _ImmichSettingsScreenState extends State<ImmichSettingsScreen> {
  late final TextEditingController _serverController;
  late final TextEditingController _keyController;
  var _probe = _ProbeState.idle;
  String? _probeMessage;
  var _obscureKey = true;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _serverController = TextEditingController(text: prefs.get<String>(optionPluginImmichServerUrl) ?? '');
    _keyController = TextEditingController(text: prefs.get<String>(optionPluginImmichApiKey) ?? '');
  }

  @override
  void dispose() {
    _serverController.dispose();
    _keyController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionPluginImmichServerUrl, _serverController.text.trim());
    await prefs.set(optionPluginImmichApiKey, _keyController.text.trim());
  }

  Future<void> _test() async {
    await _save();
    if (!mounted) return;

    setState(() {
      _probe = _ProbeState.running;
      _probeMessage = null;
    });

    final l10n = L10n.of(context);
    try {
      await context.read<ImmichClient>().verify(
            baseUrl: _serverController.text,
            apiKey: _keyController.text,
          );
      if (!mounted) return;
      setState(() {
        _probe = _ProbeState.ok;
        _probeMessage = l10n.plugin_immich_test_ok;
      });
    } on ImmichException catch (e) {
      if (!mounted) return;
      setState(() {
        _probe = _ProbeState.failed;
        _probeMessage = switch (e.kind) {
          ImmichErrorKind.notConfigured => l10n.plugin_immich_not_configured,
          ImmichErrorKind.unauthorized => l10n.plugin_immich_error_unauthorized,
          ImmichErrorKind.badServer => l10n.plugin_immich_error_server_url,
          ImmichErrorKind.network => l10n.plugin_immich_error_network,
          ImmichErrorKind.server => l10n.plugin_immich_error_generic,
        };
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_immich_title)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.plugin_immich_settings_intro, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 20),
          TextField(
            controller: _serverController,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_immich_server_url,
              helperText: l10n.plugin_immich_server_url_hint,
              border: const OutlineInputBorder(),
            ),
            onChanged: (_) => setState(() => _probe = _ProbeState.idle),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _keyController,
            obscureText: _obscureKey,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_immich_api_key,
              helperText: l10n.plugin_immich_api_key_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(_obscureKey ? Icons.visibility : Icons.visibility_off),
                onPressed: () => setState(() => _obscureKey = !_obscureKey),
              ),
            ),
            onChanged: (_) => setState(() => _probe = _ProbeState.idle),
          ),
          const SizedBox(height: 20),
          _probeRow(l10n),
          if (_probeMessage != null) ...[
            const SizedBox(height: 16),
            _probeResult(theme),
          ],
          const Divider(height: 40),
          PrefSwitch(
            pref: optionPluginImmichAlbumPerFolder,
            title: Text(l10n.plugin_immich_album_per_folder),
            subtitle: Text(l10n.plugin_immich_album_per_folder_description),
          ),
          PrefSwitch(
            pref: optionPluginImmichIncludeVideos,
            title: Text(l10n.plugin_immich_include_videos),
            subtitle: Text(l10n.plugin_immich_include_videos_description),
          ),
          const SizedBox(height: 12),
          Text(l10n.plugin_immich_folder_hint, style: theme.textTheme.bodySmall),
          const SizedBox(height: 20),
          TextButton.icon(
            onPressed: () => openUri(context, 'https://immich.app'),
            icon: const Icon(Icons.open_in_new, size: 18),
            label: Text(l10n.plugin_immich_learn_more),
          ),
        ],
      ),
    );
  }

  Widget _probeRow(L10n l10n) {
    return Row(
      children: [
        FilledButton.icon(
          onPressed: _probe == _ProbeState.running ? null : _test,
          icon: _probe == _ProbeState.running
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.wifi_tethering),
          label: Text(l10n.plugin_immich_test),
        ),
        const SizedBox(width: 12),
        TextButton(
          onPressed: () async {
            await _save();
            if (mounted) Navigator.pop(context);
          },
          child: Text(l10n.save),
        ),
      ],
    );
  }

  Widget _probeResult(ThemeData theme) {
    return Row(
      children: [
        Icon(
          _probe == _ProbeState.ok ? Icons.check_circle : Icons.error_outline,
          size: 18,
          color: _probe == _ProbeState.ok ? Colors.green : theme.colorScheme.error,
        ),
        const SizedBox(width: 8),
        Expanded(child: Text(_probeMessage!)),
      ],
    );
  }
}
