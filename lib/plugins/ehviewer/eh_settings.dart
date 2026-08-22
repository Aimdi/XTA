import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/ui/errors.dart';

class EhSettingsScreen extends StatefulWidget {
  const EhSettingsScreen({super.key});

  @override
  State<EhSettingsScreen> createState() => _EhSettingsScreenState();
}

class _EhSettingsScreenState extends State<EhSettingsScreen> {
  late final TextEditingController _cookies;
  var _testing = false;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _cookies = TextEditingController(
      text: prefs.get<String>(optionPluginEhCookies) ?? '',
    );
  }

  @override
  void dispose() {
    _cookies.dispose();
    super.dispose();
  }

  Future<void> _test() async {
    setState(() => _testing = true);
    final l10n = L10n.of(context);
    try {
      final page = await context.read<EhClient>().popular();
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '✅',
        message: l10n.plugin_eh_test_ok(page.galleries.length),
      );
    } on EhException catch (e) {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_eh_test_failed(ehErrorMessage(l10n, e)),
      );
    } catch (e) {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_eh_test_failed('$e'),
      );
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context);
    final included = context.read<EhClient>().includedCategories;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_eh_settings_title)),
      body: ListView(
        children: [
          PrefTitle(title: Text(l10n.plugin_eh_section_account)),
          PrefSwitch(
            title: Text(l10n.plugin_eh_use_exhentai),
            subtitle: Text(l10n.plugin_eh_use_exhentai_description),
            pref: optionPluginEhUseExhentai,
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              controller: _cookies,
              maxLines: 3,
              decoration: InputDecoration(
                labelText: l10n.plugin_eh_cookies,
                helperText: l10n.plugin_eh_cookies_help,
              ),
              onChanged: (value) =>
                  prefs.set(optionPluginEhCookies, value.trim()),
            ),
          ),
          ListTile(
            title: Text(l10n.plugin_eh_clear_cookies),
            onTap: () async {
              await prefs.set(optionPluginEhCookies, '');
              _cookies.clear();
              setState(() {});
            },
          ),
          PrefTitle(title: Text(l10n.plugin_eh_section_categories)),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final cat in EhCategory.values)
                  FilterChip(
                    label: Text(cat.label),
                    selected: included.contains(cat),
                    onSelected: (selected) async {
                      final next = Set<EhCategory>.of(included);
                      if (selected) {
                        next.add(cat);
                      } else if (next.length > 1) {
                        next.remove(cat);
                      }
                      await prefs.set(
                        optionPluginEhCategories,
                        jsonEncode(next.map((c) => c.name).toList()),
                      );
                      setState(() {});
                    },
                  ),
              ],
            ),
          ),
          PrefTitle(title: Text(l10n.plugin_eh_section_reading)),
          PrefSwitch(
            title: Text(l10n.plugin_eh_prefer_japanese),
            subtitle: Text(l10n.plugin_eh_prefer_japanese_description),
            pref: optionPluginEhPreferJapanese,
          ),
          PrefSwitch(
            title: Text(l10n.plugin_eh_keep_screen_on),
            subtitle: Text(l10n.plugin_eh_keep_screen_on_description),
            pref: optionPluginEhKeepScreenOn,
          ),
          ListTile(
            title: Text(l10n.plugin_eh_clear_history),
            onTap: () async {
              final history = context.read<EhHistoryStore>();
              await history.clear();
              if (mounted) setState(() {});
            },
          ),
          PrefSwitch(
            title: Text(l10n.plugin_eh_show_tab),
            subtitle: Text(l10n.plugin_eh_show_tab_description),
            pref: optionPluginEhShowTab,
            onChange: (show) async {
              if (!show) {
                await pinPluginOnFeedStripIn(context, pluginIdEhViewer);
              }
            },
          ),
          ListTile(
            title: Text(l10n.plugin_eh_test_connection),
            trailing: _testing
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.network_check),
            onTap: _testing ? null : _test,
          ),
        ],
      ),
    );
  }
}
