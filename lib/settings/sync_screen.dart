import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/utils/reader_value_store.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/_data.dart';
import 'package:xta/settings/settings_chrome.dart';
import 'package:xta/utils/webdav_sync.dart';

/// Carries the same payload as the backup export to a WebDAV server the reader
/// runs, so moving to a new phone stops being a manual file shuffle.
///
/// Restore deliberately reuses the existing import flow rather than inventing a
/// second one: whatever an imported file does, a restore does identically.
class SyncScreen extends StatefulWidget {
  const SyncScreen({super.key});

  @override
  State<SyncScreen> createState() => _SyncScreenState();
}

class _SyncScreenState extends State<SyncScreen> {
  final _url = TextEditingController();
  final _username = TextEditingController();
  final _password = TextEditingController();

  final _busy = ReaderValueStore<bool>(false);
  final _sync = WebDavSync();

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _url.text = prefs.get<String>(optionWebDavUrl) ?? '';
    _username.text = prefs.get<String>(optionWebDavUsername) ?? '';
    _password.text = prefs.get<String>(optionWebDavPassword) ?? '';
  }

  @override
  void dispose() {
    _busy.destroy();
    _sync.close();
    _url.dispose();
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  WebDavConfig get _config => WebDavConfig(url: _url.text, username: _username.text, password: _password.text);

  Future<void> _persistFields() async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionWebDavUrl, _url.text.trim());
    await prefs.set(optionWebDavUsername, _username.text);
    await prefs.set(optionWebDavPassword, _password.text);
  }

  String _message(BuildContext context, WebDavResult result) => switch (result.outcome) {
    WebDavOutcome.success => L10n.of(context).sync_uploaded,
    WebDavOutcome.notConfigured => L10n.of(context).sync_error_not_configured,
    WebDavOutcome.insecureUrl => L10n.of(context).sync_error_insecure,
    WebDavOutcome.unauthorized => L10n.of(context).sync_error_unauthorized,
    WebDavOutcome.notFound => L10n.of(context).sync_error_not_found,
    WebDavOutcome.serverError => L10n.of(context).sync_error_server,
    WebDavOutcome.networkError => L10n.of(context).sync_error_network,
    WebDavOutcome.conflict => L10n.of(context).sync_conflict,
    WebDavOutcome.unsafeServer => L10n.of(context).sync_unsafe_server,
  };

  void _report(WebDavResult result) {
    if (!mounted) {
      return;
    }
    final detail = result.isSuccess ? '' : ' (${result.detail ?? ''})'.replaceAll(' ()', '');
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('${_message(context, result)}$detail')));
  }

  Future<void> _run(Future<WebDavResult?> Function() action) async {
    if (_busy.state) return;
    _busy.update(true);
    try {
      await _persistFields();
      if (!mounted) return;
      final result = await action();
      if (result != null) _report(result);
    } catch (_) {
      _report(const WebDavResult(WebDavOutcome.networkError));
    } finally {
      if (mounted) _busy.update(false);
    }
  }

  Future<void> _upload() => _run(() async {
    final prefs = PrefService.of(context, listen: false);
    final body = await exportSettingsJson(
      context,
      includeAccounts: prefs.get<bool>(optionWebDavIncludeAccounts) == true,
    );
    final result = await _sync.upload(_config, body);
    if (result.isSuccess) {
      await prefs.set(optionWebDavLastSyncAt, DateTime.now().toIso8601String());
    }
    return result;
  });

  Future<void> _download() => _run(() async {
    final config = _config;
    final result = await _sync.download(config);
    if (!result.isSuccess) return result;
    if (result.body != null && mounted && await importSettingsJson(context, result.body!)) {
      await _sync.acknowledge(config, result);
    }
    return null;
  });

  Future<void> _history() => _run(() async {
    final config = _config;
    final current = await _sync.download(config);
    if (!current.isSuccess) return current;
    if (!mounted) return null;
    final versions = webDavVersions(current.body);
    final selected = await showDialog<WebDavVersion>(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(L10n.of(context).sync_history),
        children: versions.isEmpty
            ? [Padding(padding: const EdgeInsets.all(24), child: Text(L10n.of(context).no_results))]
            : [
                for (final version in versions)
                  SimpleDialogOption(
                    onPressed: () => Navigator.pop(context, version),
                    child: Text(DateFormat.yMd().add_Hms().format(version.savedAt.toLocal())),
                  ),
              ],
      ),
    );
    if (selected == null) return null;
    final result = await _sync.downloadVersion(config, selected);
    if (!result.isSuccess) return result;
    if (mounted && result.body != null) await importSettingsJson(context, result.body!);
    return null;
  });

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);
    final lastSync = DateTime.tryParse(prefs.get<String>(optionWebDavLastSyncAt) ?? '');

    return ScopedBuilder<ReaderValueStore<bool>, bool>(
      store: _busy,
      onState: (context, busy) => SettingsPageScaffold(
        title: L10n.of(context).sync,
        body: SettingsList(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            Text(L10n.of(context).sync_description, style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 16),
            TextField(
              controller: _url,
              keyboardType: TextInputType.url,
              autocorrect: false,
              decoration: InputDecoration(
                labelText: L10n.of(context).sync_server_url,
                hintText: L10n.of(context).sync_server_url_hint,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _username,
              autocorrect: false,
              decoration: InputDecoration(
                labelText: L10n.of(context).sync_username,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _password,
              obscureText: true,
              decoration: InputDecoration(
                labelText: L10n.of(context).sync_password,
                border: const OutlineInputBorder(),
              ),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: prefs.get<bool>(optionWebDavIncludeAccounts) == true,
              title: Text(L10n.of(context).sync_include_accounts),
              subtitle: Text(L10n.of(context).sync_include_accounts_description),
              onChanged: (value) => prefs.set(optionWebDavIncludeAccounts, value),
            ),
            const SizedBox(height: 8),
            Text(
              lastSync == null
                  ? L10n.of(context).sync_never
                  : L10n.of(
                      context,
                    ).sync_last(DateFormat.yMd(Localizations.localeOf(context).toString()).add_Hm().format(lastSync)),
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 16),
            if (busy)
              const Center(child: CircularProgressIndicator())
            else
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      icon: const Icon(Icons.cloud_upload_outlined),
                      label: Text(L10n.of(context).sync_upload),
                      onPressed: _upload,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.cloud_download_outlined),
                      label: Text(L10n.of(context).sync_download),
                      onPressed: _download,
                    ),
                  ),
                ],
              ),
            TextButton.icon(
              onPressed: busy ? null : _history,
              icon: const Icon(Icons.history),
              label: Text(L10n.of(context).sync_history),
            ),
          ],
        ),
      ),
    );
  }
}
