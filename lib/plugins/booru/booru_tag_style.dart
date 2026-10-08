import 'package:flutter/material.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query.dart';

/// Text colour for a tag kind. General tags keep the body colour; the rest
/// follow the usual booru colours, tuned to stay readable on light, dim and
/// black surfaces.
Color? booruTagColor(BooruTagCategory? category, ColorScheme scheme) {
  final dark = scheme.brightness == Brightness.dark;
  return switch (category) {
    BooruTagCategory.artist => dark ? const Color(0xFFFF8A80) : const Color(0xFFC62828),
    BooruTagCategory.copyright => dark ? const Color(0xFFD7A6F5) : const Color(0xFF7B1FA2),
    BooruTagCategory.character => dark ? const Color(0xFF7ED68A) : const Color(0xFF2E7D32),
    BooruTagCategory.species => dark ? const Color(0xFFFFB74D) : const Color(0xFFB34700),
    BooruTagCategory.meta => dark ? const Color(0xFFFFD54F) : const Color(0xFF7A5C00),
    BooruTagCategory.general || null => null,
  };
}

/// A query token as rich text: the operator and metatag name are set apart
/// from the value so `-` and `rating:` read at a glance. The token is
/// isolated left to right, so `-tag` never shows as `tag-` in RTL locales.
TextSpan booruTokenSpan(String token, ThemeData theme, {BooruTagCategory? category, TextStyle? base}) {
  final parsed = BooruQueryToken.parse(token);
  final scheme = theme.colorScheme;
  return TextSpan(
    style: base,
    children: [
      const TextSpan(text: '\u2066'),
      if (parsed.operator == BooruTagOperator.exclude)
        TextSpan(
          text: '− ',
          style: TextStyle(color: scheme.error, fontWeight: FontWeight.w700),
        ),
      if (parsed.operator == BooruTagOperator.either)
        TextSpan(
          text: '~ ',
          style: TextStyle(color: scheme.tertiary, fontWeight: FontWeight.w700),
        ),
      if (parsed.metatag != null)
        TextSpan(
          text: '${parsed.metatag}:',
          style: TextStyle(color: scheme.onSurfaceVariant),
        ),
      TextSpan(
        text: parsed.value,
        style: TextStyle(color: booruTagColor(category, scheme)),
      ),
      const TextSpan(text: '\u2069'),
    ],
  );
}

/// A whole query, token by token.
TextSpan booruQuerySpan(String query, ThemeData theme, {TextStyle? base}) {
  final tokens = booruQueryTokens(query);
  return TextSpan(
    style: base,
    children: [
      for (final (index, token) in tokens.indexed) ...[
        if (index > 0) const TextSpan(text: '  '),
        booruTokenSpan(token, theme),
      ],
    ],
  );
}
