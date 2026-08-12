import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_errors.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/ui/errors.dart';

class BooruSettingsScreen extends StatefulWidget {
  const BooruSettingsScreen({super.key});

  @override
  State<BooruSettingsScreen> createState() => _BooruSettingsScreenState();
}

class _BooruSettingsScreenState extends State<BooruSettingsScreen> {
  late final TextEditingController _host;
  late final TextEditingController _login;
  late final TextEditingController _apiKey;
  late final TextEditingController _muteTag;
  var _testing = false;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _host = TextEditingController(
      text: prefs.get<String>(optionPluginBooruHost) ?? '',
    );
    _login = TextEditingController(
      text: prefs.get<String>(optionPluginBooruLogin) ?? '',
    );
    _apiKey = TextEditingController(
      text: prefs.get<String>(optionPluginBooruApiKey) ?? '',
    );
    _muteTag = TextEditingController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<BooruMuteStore>().load();
    });
  }

  @override
  void dispose() {
    _host.dispose();
    _login.dispose();
    _apiKey.dispose();
    _muteTag.dispose();
    super.dispose();
  }

  Future<void> _applyPreset(BooruPreset preset) async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionPluginBooruPreset, preset.id);
    await prefs.set(optionPluginBooruEngine, preset.engine.id);
    await prefs.set(optionPluginBooruHost, preset.host);
    setState(() => _host.text = preset.host);
  }

  Future<void> _test() async {
    setState(() => _testing = true);
    final l10n = L10n.of(context);
    try {
      final page = await context.read<BooruClient>().latest(limit: 1);
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '✅',
        message: l10n.plugin_booru_test_ok(page.posts.length),
      );
    } on BooruException catch (e) {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_booru_test_failed(booruErrorMessage(l10n, e)),
      );
    } catch (e) {
      if (!mounted) return;
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_booru_test_failed('$e'),
      );
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context);

    final engine =
        BooruEngine.tryParse(prefs.get<String>(optionPluginBooruEngine)) ??
        BooruEngine.danbooru;
    final maxRating =
        BooruRating.tryParse(prefs.get<String>(optionPluginBooruMaxRating)) ??
        BooruRating.general;
    final presetId = prefs.get<String>(optionPluginBooruPreset) ?? 'danbooru';

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_booru_settings_title)),
      body: ListView(
        children: [
          PrefTitle(title: Text(l10n.plugin_booru_section_host)),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final preset in booruPresets)
                  ChoiceChip(
                    label: Text(preset.name),
                    selected: presetId == preset.id,
                    onSelected: (_) => _applyPreset(preset),
                  ),
              ],
            ),
          ),
          ListTile(
            title: Text(l10n.plugin_booru_engine),
            subtitle: Text(_engineLabel(l10n, engine)),
            onTap: () async {
              final next = await showModalBottomSheet<BooruEngine>(
                context: context,
                builder: (context) => SafeArea(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      for (final value in BooruEngine.values)
                        ListTile(
                          title: Text(_engineLabel(l10n, value)),
                          onTap: () => Navigator.pop(context, value),
                        ),
                    ],
                  ),
                ),
              );
              if (next == null) return;
              await prefs.set(optionPluginBooruEngine, next.id);
              await prefs.set(optionPluginBooruPreset, 'custom');
              setState(() {});
            },
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              controller: _host,
              decoration: InputDecoration(
                labelText: l10n.plugin_booru_host,
                hintText: 'https://danbooru.donmai.us',
              ),
              onChanged: (value) async {
                await prefs.set(
                  optionPluginBooruHost,
                  normaliseBooruHost(value),
                );
                await prefs.set(optionPluginBooruPreset, 'custom');
              },
            ),
          ),
          const SizedBox(height: 8),
          PrefTitle(title: Text(l10n.plugin_booru_section_credentials)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              controller: _login,
              decoration: InputDecoration(
                labelText: l10n.plugin_booru_login,
                helperText: _loginHelp(l10n, engine),
              ),
              onChanged: (value) => prefs.set(optionPluginBooruLogin, value),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: TextField(
              controller: _apiKey,
              obscureText: true,
              decoration: InputDecoration(
                labelText: _apiKeyLabel(l10n, engine),
                helperText: _apiKeyHelp(l10n, engine),
              ),
              onChanged: (value) => prefs.set(optionPluginBooruApiKey, value),
            ),
          ),
          PrefTitle(title: Text(l10n.plugin_booru_section_content)),
          ListTile(
            title: Text(l10n.plugin_booru_max_rating),
            subtitle: Text(_ratingLabel(l10n, maxRating)),
            onTap: () async {
              final next = await showModalBottomSheet<BooruRating>(
                context: context,
                builder: (context) => SafeArea(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      for (final value in BooruRating.values)
                        ListTile(
                          title: Text(_ratingLabel(l10n, value)),
                          onTap: () => Navigator.pop(context, value),
                        ),
                    ],
                  ),
                ),
              );
              if (next == null) return;
              await prefs.set(optionPluginBooruMaxRating, next.code);
              setState(() {});
            },
          ),
          PrefSwitch(
            title: Text(l10n.plugin_booru_in_home_feed),
            subtitle: Text(l10n.plugin_booru_in_home_feed_description),
            pref: optionPluginBooruInHomeFeed,
          ),
          PrefSwitch(
            title: Text(l10n.plugin_booru_show_tab),
            subtitle: Text(l10n.plugin_booru_show_tab_description),
            pref: optionPluginBooruShowTab,
          ),
          PrefTitle(title: Text(l10n.plugin_booru_mute_section)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              controller: _muteTag,
              decoration: InputDecoration(
                labelText: l10n.plugin_booru_mute_tag,
                suffixIcon: IconButton(
                  icon: const Icon(Icons.add),
                  onPressed: () async {
                    final tag = normaliseBooruTag(_muteTag.text);
                    if (tag == null) return;
                    await context.read<BooruMuteStore>().mute(tag);
                    _muteTag.clear();
                  },
                ),
              ),
              onSubmitted: (value) async {
                final tag = normaliseBooruTag(value);
                if (tag == null) return;
                await context.read<BooruMuteStore>().mute(tag);
                _muteTag.clear();
              },
            ),
          ),
          ScopedBuilder<BooruMuteStore, Set<String>>(
            store: context.read<BooruMuteStore>(),
            onState: (context, muted) {
              if (muted.isEmpty) {
                return ListTile(
                  dense: true,
                  title: Text(l10n.plugin_booru_mute_empty),
                );
              }
              return Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                child: Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final tag in muted.toList()..sort())
                      InputChip(
                        label: Text(tag),
                        onDeleted: () =>
                            context.read<BooruMuteStore>().unmute(tag),
                      ),
                  ],
                ),
              );
            },
          ),
          ListTile(
            title: Text(l10n.plugin_booru_test_connection),
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

  String _engineLabel(L10n l10n, BooruEngine engine) => switch (engine) {
    BooruEngine.danbooru => l10n.plugin_booru_engine_danbooru,
    BooruEngine.moebooru => l10n.plugin_booru_engine_moebooru,
    BooruEngine.gelbooruV2 => l10n.plugin_booru_engine_gelbooru,
    BooruEngine.e621 => l10n.plugin_booru_engine_e621,
  };

  String _ratingLabel(L10n l10n, BooruRating rating) => switch (rating) {
    BooruRating.general => l10n.plugin_booru_rating_general,
    BooruRating.sensitive => l10n.plugin_booru_rating_sensitive,
    BooruRating.questionable => l10n.plugin_booru_rating_questionable,
    BooruRating.explicit => l10n.plugin_booru_rating_explicit,
  };

  String _loginHelp(L10n l10n, BooruEngine engine) => switch (engine) {
    BooruEngine.gelbooruV2 => l10n.plugin_booru_login_help_gelbooru,
    BooruEngine.moebooru => l10n.plugin_booru_login_help_moebooru,
    _ => l10n.plugin_booru_login_help,
  };

  String _apiKeyLabel(L10n l10n, BooruEngine engine) => switch (engine) {
    BooruEngine.moebooru => l10n.plugin_booru_password_hash,
    _ => l10n.plugin_booru_api_key,
  };

  String _apiKeyHelp(L10n l10n, BooruEngine engine) => switch (engine) {
    BooruEngine.moebooru => l10n.plugin_booru_password_hash_help,
    BooruEngine.gelbooruV2 => l10n.plugin_booru_api_key_help_gelbooru,
    _ => l10n.plugin_booru_api_key_help,
  };
}
