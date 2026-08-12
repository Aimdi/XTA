import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_errors.dart';
import 'package:xta/ui/errors.dart';

class TikTokSettingsScreen extends StatefulWidget {
  const TikTokSettingsScreen({super.key});

  @override
  State<TikTokSettingsScreen> createState() => _TikTokSettingsScreenState();
}

class _TikTokSettingsScreenState extends State<TikTokSettingsScreen> {
  var _testing = false;

  Future<void> _test() async {
    setState(() => _testing = true);
    final l10n = L10n.of(context);
    try {
      final profile = await context.read<TikTokClient>().profile('tiktok');
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '✅',
        message: l10n.plugin_tiktok_test_ok(profile.displayName),
      );
    } on TikTokException catch (e) {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_tiktok_test_failed(tiktokErrorMessage(l10n, e)),
      );
    } catch (e) {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_tiktok_test_failed('$e'),
      );
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_tiktok_settings_title)),
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
                    Expanded(child: Text(l10n.plugin_tiktok_guest_note)),
                  ],
                ),
              ),
            ),
          ),
          PrefSwitch(
            title: Text(l10n.plugin_tiktok_show_tab),
            subtitle: Text(l10n.plugin_tiktok_show_tab_description),
            pref: optionPluginTiktokShowTab,
          ),
          PrefTitle(title: Text(l10n.plugin_tiktok_section_playback)),
          PrefSwitch(
            title: Text(l10n.plugin_tiktok_prefer_embed),
            subtitle: Text(l10n.plugin_tiktok_prefer_embed_description),
            pref: optionPluginTiktokPreferEmbed,
          ),
          PrefTitle(title: Text(l10n.plugin_tiktok_section_session)),
          ListTile(
            leading: const Icon(Icons.cookie_outlined),
            title: Text(l10n.plugin_tiktok_clear_session),
            subtitle: Text(l10n.plugin_tiktok_clear_session_description),
            onTap: () async {
              await context.read<TikTokClient>().clearSession();
              if (!context.mounted) return;
              showSnackBar(
                context,
                icon: '✅',
                message: l10n.plugin_tiktok_session_cleared,
              );
            },
          ),
          ListTile(
            leading: const Icon(Icons.network_check),
            title: Text(l10n.plugin_tiktok_test_connection),
            subtitle: Text(l10n.plugin_tiktok_test_connection_description),
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
