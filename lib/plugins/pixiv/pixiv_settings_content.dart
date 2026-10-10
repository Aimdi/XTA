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
    final prefs = PrefService.of(context);
    return Column(
      children: [
        PixivPrefSwitch(
          pref: optionPluginPixivShowR18,
          title: l10n.plugin_pixiv_show_r18,
          subtitle: l10n.plugin_pixiv_show_r18_description,
        ),
        const PixivHideAiSwitch(),
        // Keyed by account, so a switch reads the new account's setting.
        if (pixivSignedIn(prefs)) PixivAiShowSetting(key: ValueKey(prefs.get<int>(optionPluginPixivUserId) ?? 0)),
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

  /// What the switch shows while nothing is stored.
  final bool defaultValue;

  const PixivPrefSwitch({
    super.key,
    required this.pref,
    required this.title,
    required this.subtitle,
    this.defaultValue = false,
  });

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
        value: prefs.get<bool>(widget.pref) ?? widget.defaultValue,
        onChanged: (value) => _set(prefs, value),
      ),
    );
  }
}

/// One Pixiv preference picked from a few [options]: the current one under the [title],
/// the rest in a dialog.
class PixivPrefChoice<T extends Object> extends StatefulWidget {
  final String pref;
  final String title;

  /// What is shown and kept while nothing usable is stored.
  final T fallback;
  final List<(T, String)> options;

  const PixivPrefChoice({
    super.key,
    required this.pref,
    required this.title,
    required this.fallback,
    required this.options,
  });

  @override
  State<PixivPrefChoice<T>> createState() => _PixivPrefChoiceState<T>();
}

class _PixivPrefChoiceState<T extends Object> extends State<PixivPrefChoice<T>> {
  final _revision = SettingsRevisionStore();

  @override
  void dispose() {
    _revision.destroy();
    super.dispose();
  }

  T _current(BasePrefService prefs) {
    final stored = prefs.get<Object>(widget.pref);
    return widget.options.map((option) => option.$1).where((value) => value == stored).firstOrNull ?? widget.fallback;
  }

  Future<void> _pick(BasePrefService prefs) async {
    final current = _current(prefs);
    final picked = await showDialog<T>(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(widget.title),
        children: [
          RadioGroup<T>(
            groupValue: current,
            onChanged: (value) => Navigator.pop(context, value),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final (value, label) in widget.options) RadioListTile<T>(value: value, title: Text(label)),
              ],
            ),
          ),
        ],
      ),
    );
    if (picked == null || picked == current) return;
    await prefs.set<T>(widget.pref, picked);
    _revision.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context, listen: false);
    return ScopedBuilder<SettingsRevisionStore, int>(
      store: _revision,
      onState: (context, _) {
        final current = _current(prefs);
        return ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(widget.title),
          subtitle: Text(
            widget.options.firstWhere((option) => option.$1 == current, orElse: () => widget.options.first).$2,
          ),
          trailing: const Icon(Icons.arrow_drop_down),
          onTap: () => _pick(prefs),
        );
      },
    );
  }
}
