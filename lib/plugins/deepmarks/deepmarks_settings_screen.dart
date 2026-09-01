import 'package:flutter/material.dart';
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

enum _ProbeState { idle, running, ok, warning, failed }

class _DeepmarksSettingsScreenState extends State<DeepmarksSettingsScreen> {
  late final TextEditingController _apiKeyController;
  late final TextEditingController _secretController;
  late final TextEditingController _baseController;
  var _probe = _ProbeState.idle;
  String? _probeMessage;
  var _obscureKey = true;
  var _obscureSecret = true;

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
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionPluginDeepmarksApiKey, _apiKeyController.text.trim());
    await prefs.set(optionPluginDeepmarksSecretKey, _secretController.text.trim());
    await prefs.set(optionPluginDeepmarksApiBase, _baseController.text.trim());
  }

  Future<void> _test() async {
    await _save();
    if (!mounted) return;

    final l10n = L10n.of(context);
    setState(() {
      _probe = _ProbeState.running;
      _probeMessage = null;
    });

    if (_secretController.text.trim().isNotEmpty && _publicKey == null) {
      setState(() {
        _probe = _ProbeState.failed;
        _probeMessage = l10n.plugin_deepmarks_error_secret_key;
      });
      return;
    }

    try {
      final owner = await context.read<DeepmarksClient>().verify(
            baseUrl: _baseController.text,
            apiKey: _apiKeyController.text,
          );
      if (!mounted) return;

      final mine = _publicKey;
      // The API has no "who am I", so an owner is only known once the account
      // has a public bookmark. When it is known, a mismatch would fail every
      // save with a 403, so it is worth saying now.
      if (owner != null && mine != null && owner != mine) {
        setState(() {
          _probe = _ProbeState.failed;
          _probeMessage = l10n.plugin_deepmarks_error_key_mismatch;
        });
        return;
      }

      setState(() {
        _probe = owner == null ? _ProbeState.warning : _ProbeState.ok;
        _probeMessage = owner == null ? l10n.plugin_deepmarks_test_ok_unverified : l10n.plugin_deepmarks_test_ok;
      });
    } on DeepmarksException catch (e) {
      if (!mounted) return;
      setState(() {
        _probe = _ProbeState.failed;
        _probeMessage = deepmarksErrorMessage(l10n, e.kind);
      });
    }
  }

  Color? _probeColor(ThemeData theme) => switch (_probe) {
        _ProbeState.ok => Colors.green,
        _ProbeState.warning => Colors.orange,
        _ProbeState.failed => theme.colorScheme.error,
        _ => null,
      };

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final publicKey = _publicKey;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_deepmarks_title)),
      body: ListView(
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
          TextField(
            controller: _apiKeyController,
            obscureText: _obscureKey,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_deepmarks_api_key,
              helperText: l10n.plugin_deepmarks_api_key_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(_obscureKey ? Icons.visibility : Icons.visibility_off),
                onPressed: () => setState(() => _obscureKey = !_obscureKey),
              ),
            ),
            onChanged: (_) => setState(() => _probe = _ProbeState.idle),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _secretController,
            obscureText: _obscureSecret,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_deepmarks_secret_key,
              helperText: l10n.plugin_deepmarks_secret_key_hint,
              helperMaxLines: 3,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(_obscureSecret ? Icons.visibility : Icons.visibility_off),
                onPressed: () => setState(() => _obscureSecret = !_obscureSecret),
              ),
            ),
            onChanged: (_) => setState(() => _probe = _ProbeState.idle),
          ),
          if (publicKey != null) ...[
            const SizedBox(height: 8),
            Text(
              l10n.plugin_deepmarks_signing_as(publicKey),
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
            onChanged: (_) => setState(() => _probe = _ProbeState.idle),
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              FilledButton.icon(
                onPressed: _probe == _ProbeState.running ? null : _test,
                icon: _probe == _ProbeState.running
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.wifi_tethering),
                label: Text(l10n.plugin_deepmarks_test),
              ),
              const SizedBox(width: 12),
              TextButton(
                onPressed: () async {
                  await _save();
                  if (context.mounted) Navigator.pop(context);
                },
                child: Text(l10n.save),
              ),
            ],
          ),
          if (_probeMessage != null) ...[
            const SizedBox(height: 16),
            Row(
              children: [
                Icon(
                  _probe == _ProbeState.ok ? Icons.check_circle : Icons.error_outline,
                  size: 18,
                  color: _probeColor(theme),
                ),
                const SizedBox(width: 8),
                Expanded(child: Text(_probeMessage!)),
              ],
            ),
          ],
          const SizedBox(height: 28),
          TextButton.icon(
            onPressed: () => openUri(context, 'https://github.com/ostermayer/deepmarks-public'),
            icon: const Icon(Icons.open_in_new, size: 18),
            label: Text(l10n.plugin_deepmarks_learn_more),
          ),
        ],
      ),
    );
  }
}
