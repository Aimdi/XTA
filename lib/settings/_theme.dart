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
    final BasePrefService prefs = PrefService.of(context);

    return SettingsPageScaffold(
      title: L10n.current.theme,
      body: SettingsList(
        children: [
          PrefDropdown(
            fullWidth: false,
            title: Text(L10n.of(context).theme_background),
            subtitle: Text(L10n.of(context).theme_background_description),
            pref: optionXLookBackground,
            items: [
              DropdownMenuItem(
                value: xLookBackgroundSystem,
                child: Text(L10n.of(context).system),
              ),
              DropdownMenuItem(
                value: xLookBackgroundLight,
                child: Text(L10n.of(context).light),
              ),
              DropdownMenuItem(
                value: xLookBackgroundDim,
                child: Text(L10n.of(context).theme_background_dim),
              ),
              DropdownMenuItem(
                value: xLookBackgroundLightsOut,
                child: Text(L10n.of(context).theme_background_lights_out),
              ),
            ],
          ),
          PrefDropdown(
            fullWidth: false,
            title: Text(L10n.of(context).theme_accent),
            subtitle: Text(L10n.of(context).theme_accent_description),
            pref: optionXLookAccent,
            items: xLookAccents.entries
                .map(
                  (accent) => DropdownMenuItem(
                    value: accent.key,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // The swatch carries the meaning; the name is there for
                        // anyone who cannot tell these colours apart.
                        Container(
                          width: 16,
                          height: 16,
                          margin: const EdgeInsets.only(right: 8),
                          decoration: BoxDecoration(
                            color: accent.value,
                            shape: BoxShape.circle,
                          ),
                        ),
                        Text(xLookAccentName(L10n.of(context), accent.key)),
                      ],
                    ),
                  ),
                )
                .toList(),
          ),
          PrefSwitch(
            title: Text(L10n.of(context).true_black),
            pref: optionThemeTrueBlack,
            subtitle: Text(
              L10n.of(context).use_true_black_for_the_dark_mode_theme,
            ),
            onChange: (bool changeValue) {
              prefs.set(optionThemeTrueBlackTweetCards, changeValue);
            },
          ),
          PrefSwitch(
            title: Text(L10n.of(context).true_black_tweet_cards),
            pref: optionThemeTrueBlackTweetCards,
            disabled: !prefs.get(optionThemeTrueBlack),
            subtitle: Text(L10n.of(context).use_true_black_for_tweet_cards),
          ),
          PrefSwitch(
            title: Text(L10n.of(context).show_navigation_labels),
            pref: optionShowNavigationLabels,
          ),
        ],
      ),
    );
  }
}
