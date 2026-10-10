import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/settings_view_store.dart';

/// What the feeds may show: R-18 works and AI-generated works.
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
