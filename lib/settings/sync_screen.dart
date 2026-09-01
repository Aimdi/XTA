import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:quax/constants.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/settings/_data.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/utils/webdav_sync.dart';

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

  late final SettingsValueStore<bool> _busyStore;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _url.text = prefs.get<String>(optionWebDavUrl) ?? '';
    _username.text = prefs.get<String>(optionWebDavUsername) ?? '';
    _password.text = prefs.get<String>(optionWebDavPassword) ?? '';
    _busyStore = SettingsValueStore(false);
  }

  @override
  void dispose() {
    _url.dispose();
    _username.dispose();
    _password.dispose();
    _busyStore.destroy();
    super.dispose();
  }

  WebDavConfig get _config => WebDavConfig(
    url: _url.text,
    username: _username.text,
    password: _password.text,
  );

  Future<void> _persistFields() async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionWebDavUrl, _url.text.trim());
    await prefs.set(optionWebDavUsername, _username.text);
    await prefs.set(optionWebDavPassword, _password.text);
  }

  String _message(BuildContext context, WebDavResult result) =>
      switch (result.outcome) {
        WebDavOutcome.success => L10n.of(context).sync_uploaded,
        WebDavOutcome.notConfigured => L10n.of(
          context,
        ).sync_error_not_configured,
        WebDavOutcome.insecureUrl => L10n.of(context).sync_error_insecure,
        WebDavOutcome.unauthorized => L10n.of(context).sync_error_unauthorized,
        WebDavOutcome.notFound => L10n.of(context).sync_error_not_found,
        WebDavOutcome.serverError => L10n.of(context).sync_error_server,
        WebDavOutcome.networkError => L10n.of(context).sync_error_network,
      };

  void _report(WebDavResult result) {
    if (!mounted) {
      return;
    }
    final detail = result.isSuccess
        ? ''
        : ' (${result.detail ?? ''})'.replaceAll(' ()', '');
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('${_message(context, result)}$detail')),
    );
  }

  Future<void> _run(Future<WebDavResult> Function() action) async {
    _busyStore.setValue(true);
    await _persistFields();
    final result = await action();
    if (!mounted) return;
    _busyStore.setValue(false);
    _report(result);
  }

  Future<void> _upload() => _run(() async {
    final prefs = PrefService.of(context, listen: false);
    final body = await exportSettingsJson(
      context,
      includeAccounts: prefs.get<bool>(optionWebDavIncludeAccounts) == true,
    );
    final result = await WebDavSync().upload(_config, body);
    if (result.isSuccess) {
      await prefs.set(optionWebDavLastSyncAt, DateTime.now().toIso8601String());
    }
    return result;
  });

  Future<void> _download() => _run(() async {
    final result = await WebDavSync().download(_config);
    if (result.isSuccess && result.body != null && mounted) {
      await importSettingsJson(context, result.body!);
    }
    return result;
  });

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);
    final lastSync = DateTime.tryParse(
      prefs.get<String>(optionWebDavLastSyncAt) ?? '',
    );

    return SettingsPageScaffold(
      title: L10n.of(context).sync,
      body: ScopedBuilder<SettingsValueStore<bool>, bool>(
        store: _busyStore,
        onState: (_, busy) => SettingsList(
          children: [
            SettingsSection(
              description: L10n.of(context).sync_description,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: kTweetHorizontalPadding,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
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
                      const SizedBox(height: kTweetSpace3),
                      TextField(
                        controller: _username,
                        autocorrect: false,
                        decoration: InputDecoration(
                          labelText: L10n.of(context).sync_username,
                          border: const OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: kTweetSpace3),
                      TextField(
                        controller: _password,
                        obscureText: true,
                        decoration: InputDecoration(
                          labelText: L10n.of(context).sync_password,
                          border: const OutlineInputBorder(),
                        ),
                      ),
                      SettingsToggleRow(
                        contentPadding: EdgeInsets.zero,
                        value:
                            prefs.get<bool>(optionWebDavIncludeAccounts) ==
                            true,
                        title: L10n.of(context).sync_include_accounts,
                        description: L10n.of(
                          context,
                        ).sync_include_accounts_description,
                        onChanged: (value) =>
                            prefs.set(optionWebDavIncludeAccounts, value),
                      ),
                      const SizedBox(height: kTweetSpace2),
                      Text(
                        lastSync == null
                            ? L10n.of(context).sync_never
                            : L10n.of(context).sync_last(
                                DateFormat.yMd(
                                  Localizations.localeOf(context).toString(),
                                ).add_Hm().format(lastSync),
                              ),
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: kTweetSpace4),
                      if (busy)
                        const Center(
                          child: SizedBox.square(
                            dimension: kTweetTouchTarget,
                            child: Padding(
                              padding: EdgeInsets.all(kTweetSpace3),
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          ),
                        )
                      else
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            FilledButton.icon(
                              icon: const Icon(Icons.cloud_upload_outlined),
                              label: Text(L10n.of(context).sync_upload),
                              onPressed: _upload,
                            ),
                            const SizedBox(height: kTweetSpace2),
                            OutlinedButton.icon(
                              icon: const Icon(Icons.cloud_download_outlined),
                              label: Text(L10n.of(context).sync_download),
                              onPressed: _download,
                            ),
                          ],
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
