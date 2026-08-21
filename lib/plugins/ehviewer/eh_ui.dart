import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';

bool ehPreferJapaneseOf(BuildContext context) =>
    PrefService.of(
      context,
      listen: false,
    ).get<bool>(optionPluginEhPreferJapanese) !=
    false;

Color ehCategoryColor(EhCategory category) => switch (category) {
  EhCategory.misc => const Color(0xFF777777),
  EhCategory.doujinshi => const Color(0xFF9E2720),
  EhCategory.manga => const Color(0xFFDB6C24),
  EhCategory.artistCg => const Color(0xFFD38F1D),
  EhCategory.gameCg => const Color(0xFF6A936D),
  EhCategory.imageSet => const Color(0xFF325CA2),
  EhCategory.cosplay => const Color(0xFF6A32A2),
  EhCategory.asianPorn => const Color(0xFFA23282),
  EhCategory.nonH => const Color(0xFF5FA9CF),
  EhCategory.western => const Color(0xFFAB9F60),
};

String ehLanguageLabel(L10n l10n, EhSearchLanguage language) =>
    switch (language) {
      EhSearchLanguage.any => l10n.plugin_eh_language_any,
      EhSearchLanguage.english => l10n.plugin_eh_lang_english,
      EhSearchLanguage.japanese => l10n.plugin_eh_lang_japanese,
      EhSearchLanguage.chinese => l10n.plugin_eh_lang_chinese,
      EhSearchLanguage.korean => l10n.plugin_eh_lang_korean,
      EhSearchLanguage.spanish => l10n.plugin_eh_lang_spanish,
      EhSearchLanguage.french => l10n.plugin_eh_lang_french,
    };

String ehToplistLabel(L10n l10n, EhToplistPeriod period) => switch (period) {
  EhToplistPeriod.yesterday => l10n.plugin_eh_toplist_yesterday,
  EhToplistPeriod.month => l10n.plugin_eh_toplist_month,
  EhToplistPeriod.year => l10n.plugin_eh_toplist_year,
  EhToplistPeriod.allTime => l10n.plugin_eh_toplist_all,
};
