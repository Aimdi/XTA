import 'package:xta/utils/iterables.dart';
import 'package:xta/utils/native_locale_names.dart';

/// The languages X tags posts with, by the code `lang:` takes.
const searchLanguageCodes = [
  'am', 'ar', 'bg', 'bn', 'ca', 'cs', 'cy', 'da', 'de', 'el', 'en', 'es', 'et', 'eu', 'fa', 'fi', 'fr', 'ga', //
  'gl', 'gu', 'he', 'hi', 'hr', 'hu', 'hy', 'id', 'is', 'it', 'ja', 'ka', 'km', 'kn', 'ko', 'lo', 'lt', 'lv', //
  'ml', 'mr', 'ms', 'my', 'ne', 'nl', 'no', 'or', 'pa', 'pl', 'ps', 'pt', 'ro', 'ru', 'si', 'sk', 'sl', 'sr', //
  'sv', 'ta', 'te', 'th', 'tl', 'tr', 'uk', 'ur', 'vi', 'zh',
];

/// What the language of [code] calls itself, or the code when nothing here knows it.
String searchLanguageName(String code) => nativeLocaleNameOf(code) ?? code;

/// [searchLanguageCodes] by their names, with [current] among them when a query asked for another code.
List<String> searchLanguageChoices(String current) {
  String byName(String code) => searchLanguageName(code).toLowerCase();
  final codes = {...searchLanguageCodes, if (current.isNotEmpty) current};
  return codes.sorted((a, b) => byName(a).compareTo(byName(b))).toList();
}
