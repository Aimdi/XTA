import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_api.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// Where the reader points the plugin, and who they follow with it.
class ThreadsSettingsScreen extends StatefulWidget {
  const ThreadsSettingsScreen({super.key});

  @override
  State<ThreadsSettingsScreen> createState() => _ThreadsSettingsScreenState();
}

class _ThreadsSettingsScreenState extends State<ThreadsSettingsScreen> {
  late final TextEditingController _cookies;
  late final TextEditingController _bearer;
  late final TextEditingController _instance;
  late final TextEditingController _apiBase;
  late final TextEditingController _apiToken;
  bool _testingDirect = false;
  bool _testing = false;
  bool _testingApi = false;
  bool _cookiesHidden = true;
  bool _bearerHidden = true;
  bool _tokenHidden = true;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _cookies = TextEditingController(
      text: prefs.get<String>(optionPluginThreadsDirectCookies) ?? '',
    );
    _bearer = TextEditingController(
      text: prefs.get<String>(optionPluginThreadsDirectBearer) ?? '',
    );
    _instance = TextEditingController(
      text: prefs.get<String>(optionPluginThreadsInstance) ?? '',
    );
    _apiBase = TextEditingController(
      text:
          prefs.get<String>(optionPluginThreadsApiBase) ??
          kThreadsApiDefaultBase,
    );
    _apiToken = TextEditingController(
      text: prefs.get<String>(optionPluginThreadsApiToken) ?? '',
    );
  }

  @override
  void dispose() {
    _cookies.dispose();
    _bearer.dispose();
    _instance.dispose();
    _apiBase.dispose();
    _apiToken.dispose();
    super.dispose();
  }

  Future<void> _saveDirect() async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionPluginThreadsDirectCookies, _cookies.text.trim());
    await prefs.set(optionPluginThreadsDirectBearer, _bearer.text.trim());
  }

  Future<void> _testDirect() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final direct = context.read<ThreadsDirectClient>();

    await _saveDirect();
    setState(() => _testingDirect = true);
    String message;
    try {
      final who = await direct.verify();
      message = l10n.plugin_threads_direct_test_ok(who);
    } catch (e) {
      message = threadsSettingsError(l10n, e);
    }

    if (mounted) {
      setState(() => _testingDirect = false);
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _clearDirect() async {
    _cookies.clear();
    _bearer.clear();
    await _saveDirect();
    if (mounted) setState(() {});
  }

  Future<void> _save() async {
    await PrefService.of(
      context,
      listen: false,
    ).set(optionPluginThreadsInstance, _instance.text.trim());
  }

  Future<void> _test() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final client = context.read<ThreadsClient>();

    await _save();
    setState(() => _testing = true);
    String message;
    try {
      await client.verify(_instance.text.trim());
      message = l10n.plugin_threads_test_ok;
    } catch (e) {
      message = threadsSettingsError(l10n, e);
    }

    if (mounted) {
      setState(() => _testing = false);
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _saveApi() async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionPluginThreadsApiBase, _apiBase.text.trim());
    await prefs.set(optionPluginThreadsApiToken, _apiToken.text.trim());
  }

  Future<void> _testApi() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final api = context.read<ThreadsApi>();

    await _saveApi();
    setState(() => _testingApi = true);
    String message;
    try {
      final ok = await api.health(_apiBase.text.trim());
      message = ok
          ? l10n.plugin_threads_api_test_ok
          : l10n.plugin_threads_error_unreachable;
    } catch (e) {
      message = threadsApiSettingsError(l10n, e);
    }

    if (mounted) {
      setState(() => _testingApi = false);
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _remove(String handle) async {
    await context.read<ThreadsAccountsStore>().remove(handle);
    if (mounted) {
      await context.read<ThreadsFeedStore>().refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_threads_title)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: ListTile(
              leading: Icon(Icons.public, color: theme.colorScheme.primary),
              title: Text(l10n.plugin_threads_guest_card),
              subtitle: Text(l10n.plugin_threads_guest_card_body),
            ),
          ),
          const SizedBox(height: 12),
          Text(
            l10n.plugin_threads_settings_intro,
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            secondary: const Icon(Icons.dynamic_feed_outlined),
            title: Text(l10n.plugin_threads_in_home_feed),
            subtitle: Text(l10n.plugin_threads_in_home_feed_description),
            value:
                PrefService.of(
                  context,
                ).get<bool>(optionPluginThreadsInHomeFeed) ==
                true,
            onChanged: (value) async {
              await PrefService.of(
                context,
                listen: false,
              ).set(optionPluginThreadsInHomeFeed, value);
              if (mounted) setState(() {});
            },
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            secondary: const Icon(Icons.gpp_maybe_outlined),
            title: Text(l10n.plugin_threads_use_session),
            subtitle: Text(l10n.plugin_threads_use_session_description),
            value:
                PrefService.of(
                  context,
                ).get<bool>(optionPluginThreadsUseSessionApis) ==
                true,
            onChanged: (value) async {
              await PrefService.of(
                context,
                listen: false,
              ).set(optionPluginThreadsUseSessionApis, value);
              if (mounted) setState(() {});
            },
          ),
          if (context.read<ThreadsDirectClient>().isSessionParked)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(
                Icons.pause_circle_outline,
                color: theme.colorScheme.error,
              ),
              title: Text(l10n.plugin_threads_session_parked),
            ),
          const Divider(height: 32),
          Text(l10n.plugin_threads_accounts, style: theme.textTheme.titleSmall),
          ScopedBuilder<ThreadsAccountsStore, List<ThreadsAccount>>(
            store: context.read<ThreadsAccountsStore>(),
            onState: (context, accounts) {
              if (accounts.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  child: Text(
                    l10n.plugin_threads_no_accounts,
                    style: theme.textTheme.bodySmall,
                  ),
                );
              }
              return Column(
                children: [
                  for (final account in accounts)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: FallbackAvatar(
                        seed: account.handle,
                        displayName: account.name,
                        size: 36,
                        accent: theme.colorScheme.primary,
                      ),
                      title: Text('@${account.handle}'),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        tooltip: l10n.delete,
                        onPressed: () => _remove(account.handle),
                      ),
                    ),
                ],
              );
            },
          ),
          const Divider(height: 32),
          Text(
            l10n.plugin_threads_session_section,
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          Text(
            l10n.plugin_threads_direct_intro,
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _cookies,
            obscureText: _cookiesHidden,
            autocorrect: false,
            enableSuggestions: false,
            maxLines: _cookiesHidden ? 1 : 4,
            decoration: InputDecoration(
              labelText: l10n.plugin_threads_direct_cookies,
              hintText: l10n.plugin_threads_direct_cookies_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(
                  _cookiesHidden ? Icons.visibility : Icons.visibility_off,
                ),
                onPressed: () =>
                    setState(() => _cookiesHidden = !_cookiesHidden),
              ),
            ),
            onChanged: (_) => _saveDirect(),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _bearer,
            obscureText: _bearerHidden,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_threads_direct_bearer,
              hintText: l10n.plugin_threads_direct_bearer_hint,
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(
                  _bearerHidden ? Icons.visibility : Icons.visibility_off,
                ),
                onPressed: () => setState(() => _bearerHidden = !_bearerHidden),
              ),
            ),
            onChanged: (_) => _saveDirect(),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              FilledButton.tonal(
                onPressed: _testingDirect ? null : _testDirect,
                child: Text(l10n.plugin_threads_direct_test),
              ),
              TextButton(
                onPressed: _clearDirect,
                child: Text(l10n.plugin_threads_direct_clear),
              ),
            ],
          ),
          const Divider(height: 32),
          Text(l10n.plugin_threads_instance, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          TextField(
            controller: _instance,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_threads_instance,
              hintText: l10n.plugin_threads_instance_hint,
            ),
            onChanged: (_) => _save(),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.tonal(
              onPressed: _testing ? null : _test,
              child: Text(l10n.plugin_threads_test),
            ),
          ),
          const Divider(height: 32),
          Text(
            l10n.plugin_threads_api_intro,
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _apiBase,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_threads_api_base,
            ),
            onChanged: (_) => _saveApi(),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _apiToken,
            obscureText: _tokenHidden,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_threads_api_token,
              suffixIcon: IconButton(
                icon: Icon(
                  _tokenHidden ? Icons.visibility : Icons.visibility_off,
                ),
                onPressed: () => setState(() => _tokenHidden = !_tokenHidden),
              ),
            ),
            onChanged: (_) => _saveApi(),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.tonal(
              onPressed: _testingApi ? null : _testApi,
              child: Text(l10n.plugin_threads_test),
            ),
          ),
        ],
      ),
    );
  }
}

/// An Xy failure as the settings screen puts it, preferring whatever the
/// server itself said.
String threadsApiSettingsError(L10n l10n, Object error) {
  if (error is! ThreadsApiException) {
    return l10n.plugin_threads_error_unreachable;
  }
  final said = error.message?.trim();
  if (said != null && said.isNotEmpty) {
    return said;
  }
  return switch (error.kind) {
    ThreadsApiErrorKind.notConfigured => l10n.plugin_threads_api_not_configured,
    ThreadsApiErrorKind.unauthorized => l10n.plugin_threads_api_unauthorized,
    ThreadsApiErrorKind.notFound => l10n.plugin_threads_error_no_feed,
    _ => l10n.plugin_threads_error_unreachable,
  };
}

String threadsSettingsError(L10n l10n, Object error) {
  if (error is! ThreadsException) {
    return l10n.plugin_threads_error_unreachable;
  }
  return switch (error.kind) {
    ThreadsErrorKind.notConfigured => l10n.plugin_threads_not_configured,
    ThreadsErrorKind.noSuchFeed => l10n.plugin_threads_error_no_route,
    ThreadsErrorKind.throttled => l10n.plugin_threads_error_throttled,
    ThreadsErrorKind.unreachable => l10n.plugin_threads_error_unreachable,
    ThreadsErrorKind.unauthorized => l10n.plugin_threads_error_unauthorized,
    ThreadsErrorKind.sessionSuspended =>
      l10n.plugin_threads_error_session_suspended,
  };
}
