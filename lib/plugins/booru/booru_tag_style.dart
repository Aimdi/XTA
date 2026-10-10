import 'package:flutter/material.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

/// The shared tag kind a booru category is coloured as.
PluginTagKind? booruTagKind(BooruTagCategory? category) => switch (category) {
  BooruTagCategory.artist => PluginTagKind.artist,
  BooruTagCategory.copyright => PluginTagKind.copyright,
  BooruTagCategory.character => PluginTagKind.character,
  BooruTagCategory.species => PluginTagKind.species,
  BooruTagCategory.general => PluginTagKind.general,
  BooruTagCategory.meta => PluginTagKind.meta,
  null => null,
};

/// Text colour for a tag kind; a tag of unknown kind keeps the body colour.
Color? booruTagColor(BooruTagCategory? category, ColorScheme scheme) =>
    pluginTagKindColor(booruTagKind(category), scheme);

/// How a tag reads: boorus join words with underscores, people read spaces.
String booruTagDisplayName(String tag) => tag.replaceAll('_', ' ');

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
