import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:pref/pref.dart';
import 'package:xta/settings/settings_chrome.dart';

/// The accent names, kept beside the picker because they exist only for it.
String xLookAccentName(L10n l10n, String accent) => switch (accent) {
  'yellow' => l10n.colour_yellow,
  'pink' => l10n.colour_pink,
  'purple' => l10n.colour_purple,
  'orange' => l10n.colour_orange,
  'green' => l10n.colour_green,
  _ => l10n.colour_blue,
};

class SettingsThemeFragment extends StatelessWidget {
  const SettingsThemeFragment({super.key});

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);
    final l10n = L10n.of(context);

    return SettingsPageScaffold(
      title: l10n.theme,
      body: SettingsList(
        children: [
          SettingsSection(
            title: l10n.theme_background,
            description: l10n.theme_background_description,
            children: [
              SettingsPreferenceSelector<String>(
                prefs: prefs,
                pref: optionXLookBackground,
                options: [
                  SettingsOption(
                    value: xLookBackgroundSystem,
                    label: l10n.system,
                    icon: Icons.brightness_auto_outlined,
                  ),
                  SettingsOption(
                    value: xLookBackgroundLight,
                    label: l10n.light,
                    color: const Color(0xFFFFFFFF),
                  ),
                  SettingsOption(
                    value: xLookBackgroundDim,
                    label: l10n.theme_background_dim,
                    color: const Color(0xFF15202B),
                  ),
                  SettingsOption(
                    value: xLookBackgroundLightsOut,
                    label: l10n.theme_background_lights_out,
                    color: const Color(0xFF000000),
                  ),
                ],
              ),
            ],
          ),
          SettingsSection(
            title: l10n.theme_accent,
            description: l10n.theme_accent_description,
            children: [
              SettingsPreferenceSelector<String>(
                prefs: prefs,
                pref: optionXLookAccent,
                options: xLookAccents.entries
                    .map(
                      (accent) => SettingsOption(
                        value: accent.key,
                        label: xLookAccentName(l10n, accent.key),
                        color: accent.value,
                      ),
                    )
                    .toList(growable: false),
              ),
            ],
          ),
          SettingsSection(
            children: [
              PrefSwitch(
                title: Text(l10n.true_black),
                pref: optionThemeTrueBlack,
                subtitle: Text(l10n.use_true_black_for_the_dark_mode_theme),
                onChange: (value) {
                  prefs.set(optionThemeTrueBlackTweetCards, value);
                },
              ),
              PrefSwitch(
                title: Text(l10n.true_black_tweet_cards),
                pref: optionThemeTrueBlackTweetCards,
                disabled: !prefs.get(optionThemeTrueBlack),
                subtitle: Text(l10n.use_true_black_for_tweet_cards),
              ),
              PrefSwitch(
                title: Text(l10n.show_navigation_labels),
                pref: optionShowNavigationLabels,
              ),
            ],
          ),
        ],
      ),
    );
  }
}
