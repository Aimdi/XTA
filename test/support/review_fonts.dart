import 'package:flutter/services.dart';

/// Inter and Material's icons, so text measures and draws as on a phone; the
/// test font draws every glyph 1 em wide.
Future<void> loadReviewFonts() async {
  for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
  }
  await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
}
