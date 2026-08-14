import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_errors.dart';
import 'package:xta/ui/errors.dart';

class InstagramSettingsScreen extends StatefulWidget {
  const InstagramSettingsScreen({super.key});

  @override
  State<InstagramSettingsScreen> createState() =>
      _InstagramSettingsScreenState();
}

class _InstagramSettingsScreenState extends State<InstagramSettingsScreen> {
  late final TextEditingController _cookies;
  var _testing = false;
  var _cookiesHidden = true;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _cookies = TextEditingController(
      text: prefs.get<String>(optionPluginInstagramCookies) ?? '',
    );
  }

  @override
  void dispose() {
    _cookies.dispose();
    super.dispose();
  }

  Future<void> _saveCookies() async {
    await context.read<InstagramClient>().setCookies(_cookies.text);
  }

  Future<void> _test() async {
    setState(() => _testing = true);
    final l10n = L10n.of(context);
    final client = context.read<InstagramClient>();
    try {
      await client.setCookies(_cookies.text);
      final profile = await client.profile('instagram');
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '✅',
        message: l10n.plugin_instagram_test_ok(profile.displayName),
      );
    } on InstagramException catch (e) {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_instagram_test_failed(
          instagramErrorMessage(l10n, e),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_instagram_test_failed('$e'),
      );
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  Future<void> _importThreads() async {
    final l10n = L10n.of(context);
    try {
      await context.read<InstagramClient>().importThreadsCookies();
      if (!mounted) return;
      _cookies.text =
          PrefService.of(context).get<String>(optionPluginInstagramCookies) ??
          '';
      setState(() {});
      showSnackBar(
        context,
        icon: '✅',
        message: l10n.plugin_instagram_import_threads_ok,
      );
    } on InstagramException {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_instagram_import_threads_empty,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_instagram_settings_title)),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Card(
              margin: EdgeInsets.zero,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.info_outline, color: theme.colorScheme.primary),
                    const SizedBox(width: 12),
                    Expanded(child: Text(l10n.plugin_instagram_guest_note)),
                  ],
                ),
              ),
            ),
          ),
          PrefSwitch(
            title: Text(l10n.plugin_instagram_show_tab),
            subtitle: Text(l10n.plugin_instagram_show_tab_description),
            pref: optionPluginInstagramShowTab,
          ),
          PrefTitle(title: Text(l10n.plugin_instagram_section_session)),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              controller: _cookies,
              obscureText: _cookiesHidden,
              autocorrect: false,
              enableSuggestions: false,
              maxLines: _cookiesHidden ? 1 : 4,
              decoration: InputDecoration(
                labelText: l10n.plugin_instagram_cookies,
                hintText: l10n.plugin_instagram_cookies_hint,
                helperText: l10n.plugin_instagram_cookies_description,
                border: const OutlineInputBorder(),
                suffixIcon: IconButton(
                  icon: Icon(
                    _cookiesHidden ? Icons.visibility : Icons.visibility_off,
                  ),
                  onPressed: () =>
                      setState(() => _cookiesHidden = !_cookiesHidden),
                ),
              ),
              onChanged: (_) => _saveCookies(),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.copy_all_outlined),
            title: Text(l10n.plugin_instagram_import_threads_cookies),
            subtitle: Text(
              l10n.plugin_instagram_import_threads_cookies_description,
            ),
            onTap: _importThreads,
          ),
          ListTile(
            leading: const Icon(Icons.cookie_outlined),
            title: Text(l10n.plugin_instagram_clear_session),
            subtitle: Text(l10n.plugin_instagram_clear_session_description),
            onTap: () async {
              await context.read<InstagramClient>().clearSession();
              if (!context.mounted) return;
              _cookies.clear();
              setState(() {});
              showSnackBar(
                context,
                icon: '✅',
                message: l10n.plugin_instagram_session_cleared,
              );
            },
          ),
          ListTile(
            leading: const Icon(Icons.network_check),
            title: Text(l10n.plugin_instagram_test_connection),
            subtitle: Text(l10n.plugin_instagram_test_connection_description),
            trailing: _testing
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : null,
            onTap: _testing ? null : _test,
          ),
        ],
      ),
    );
  }
}
