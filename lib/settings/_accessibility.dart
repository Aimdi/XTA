import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/speech/tts_settings.dart';
import 'package:xta/settings/settings_chrome.dart';

class SettingsAccessibilityFragment extends StatelessWidget {
  const SettingsAccessibilityFragment({super.key});

  @override
  Widget build(BuildContext context) {
    return SettingsPageScaffold(
      title: L10n.current.accessibility,
      body: SettingsList(
        children: [
          PrefSlider(
            title: Text(L10n.of(context).text_scale_factor),
            pref: optionTextScaleFactor,
            subtitle: Text(L10n.of(context).text_scale_factor_description),
            min: 1.0,
            max: 1.5,
            divisions: 10,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).disable_animations),
            pref: optionDisableAnimations,
            subtitle: Text(L10n.of(context).disable_animations_description),
          ),
          PrefSwitch(
            title: Text(L10n.of(context).ticker_chart),
            pref: optionTickerChart,
            subtitle: Text(L10n.of(context).ticker_chart_description),
          ),
          PrefSwitch(
            title: Text(L10n.of(context).gesture_double_tap_like),
            pref: optionGestureDoubleTapLike,
            subtitle: Text(
              L10n.of(context).gesture_double_tap_like_description,
            ),
          ),
          ListTile(
            title: Text(L10n.of(context).settings_speech),
            subtitle: Text(L10n.of(context).settings_speech_description),
            onTap: () => openTtsSettings(context, FlutterTts()),
          ),
        ],
      ),
    );
  }
}
