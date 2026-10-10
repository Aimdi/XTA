import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_account_api.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_account.dart';
import 'package:xta/settings/settings_view_store.dart';
import 'package:xta/utils/urls.dart';

/// What the feeds may show: R-18 works and AI-generated works, beside the
/// account's own AI setting on Pixiv.
class PixivContentSettings extends StatelessWidget {
  const PixivContentSettings({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        PixivPrefSwitch(
          pref: optionPluginPixivShowR18,
          title: l10n.plugin_pixiv_show_r18,
          subtitle: l10n.plugin_pixiv_show_r18_description,
        ),
        const PixivHideAiSwitch(),
        if (pixivSignedIn(PrefService.of(context))) const PixivAiShowSetting(),
      ],
    );
  }
}

/// Where the account's own AI setting can be changed; XTA only reads it.
const pixivViewingSettingsUrl = 'https://www.pixiv.net/settings/viewing';

/// The signed-in account's own AI display setting, read from Pixiv, with a
/// link to change it on pixiv.net.
class PixivAiShowSetting extends StatefulWidget {
  const PixivAiShowSetting({super.key});

  @override
  State<PixivAiShowSetting> createState() => _PixivAiShowSettingState();
}

class _PixivAiShowSettingState extends State<PixivAiShowSetting> {
  late final PixivAiShowStore _store;
  late final Future<void> _loading;

  @override
  void initState() {
    super.initState();
    _store = PixivAiShowStore(PixivAccountApi.of(context));
    _loading = _store.load();
  }

  @override
  void dispose() {
    // A read still in flight writes to the store when it lands; destroy after.
    unawaited(_loading.whenComplete(_store.destroy));
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      key: const ValueKey('pixiv-ai-show'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.plugin_pixiv_account_ai),
          subtitle: ScopedBuilder<PixivAiShowStore, bool?>(
            store: _store,
            onLoading: (_) => Text(l10n.plugin_pixiv_account_ai_loading),
            onError: (_, _) => Text(l10n.plugin_pixiv_account_ai_failed),
            onState: (_, shown) => Text(switch (shown) {
              true => l10n.plugin_pixiv_account_ai_shown,
              false => l10n.plugin_pixiv_account_ai_hidden,
              null => l10n.plugin_pixiv_account_ai_loading,
            }),
          ),
        ),
        TextButton.icon(
          onPressed: () => openUri(context, pixivViewingSettingsUrl),
          icon: const Icon(Icons.open_in_new, size: 18),
          label: Text(l10n.plugin_pixiv_change_on_pixiv),
        ),
      ],
    );
  }
}

/// Hides works their creators marked as AI-generated; off until switched on.
class PixivHideAiSwitch extends StatelessWidget {
  const PixivHideAiSwitch({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PixivPrefSwitch(
      key: const ValueKey('pixiv-hide-ai'),
      pref: optionPluginPixivHideAi,
      title: l10n.plugin_pixiv_hide_ai,
      subtitle: l10n.plugin_pixiv_hide_ai_description,
    );
  }
}

/// An on/off Pixiv preference that repaints when flipped.
class PixivPrefSwitch extends StatefulWidget {
  final String pref;
  final String title;
  final String subtitle;

  const PixivPrefSwitch({super.key, required this.pref, required this.title, required this.subtitle});

  @override
  State<PixivPrefSwitch> createState() => _PixivPrefSwitchState();
}

class _PixivPrefSwitchState extends State<PixivPrefSwitch> {
  final _revision = SettingsRevisionStore();

  @override
  void dispose() {
    _revision.destroy();
    super.dispose();
  }

  Future<void> _set(BasePrefService prefs, bool value) async {
    await prefs.set(widget.pref, value);
    _revision.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context, listen: false);
    return ScopedBuilder<SettingsRevisionStore, int>(
      store: _revision,
      onState: (context, _) => SwitchListTile(
        contentPadding: EdgeInsets.zero,
        title: Text(widget.title),
        subtitle: Text(widget.subtitle),
        value: prefs.get<bool>(widget.pref) == true,
        onChanged: (value) => _set(prefs, value),
      ),
    );
  }
}
