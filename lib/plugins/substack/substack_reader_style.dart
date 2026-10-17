import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// The article typeface: as the web page names it, and as Flutter does.
enum SubstackReaderFont {
  serif('Georgia, "Iowan Old Style", "Palatino Linotype", Palatino, "Times New Roman", serif', 'serif'),
  sans('-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif', null);

  final String cssStack;
  final String? fontFamily;

  const SubstackReaderFont(this.cssStack, this.fontFamily);
}

/// How wide the column of text may grow, in rem (16 logical pixels).
enum SubstackColumnWidth {
  narrow(36),
  medium(42),
  wide(52);

  final int rem;

  const SubstackColumnWidth(this.rem);

  double get logicalPixels => rem * 16.0;
}

/// The reader's look from the plugin settings; anything unknown reads as today's.
({SubstackReaderFont font, SubstackColumnWidth column}) substackReaderStyle(BasePrefService prefs) => (
  font:
      SubstackReaderFont.values.asNameMap()[prefs.get<String>(optionPluginSubstackReaderFont)] ??
      SubstackReaderFont.serif,
  column:
      SubstackColumnWidth.values.asNameMap()[prefs.get<String>(optionPluginSubstackColumnWidth)] ??
      SubstackColumnWidth.medium,
);
