import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_mark_style.dart';

/// Deterministic fallback color for groups without a chosen color, hashed from
/// the group name so the same group always gets the same hue.
Color groupFallbackColor(String name) {
  final hue = (name.codeUnits.fold<int>(0, (h, c) => h * 31 + c) % 360).toDouble();
  return HSLColor.fromAHSL(1.0, hue, 0.45, 0.55).toColor();
}

Color groupSeedColor(SubscriptionGroup group) => group.color ?? groupFallbackColor(group.name);

/// A group's own colour, made readable as text on the current background.
///
/// The colour is how a group is recognised, so it is kept rather than replaced
/// — but a group whose colour is nearly the colour of the sheet it is written
/// on would be a row of invisible text. The hue survives; only how far it is
/// from the background changes.
Color readableGroupColor(SubscriptionGroup group, ThemeData theme) {
  final seed = groupSeedColor(group);
  final onDark = theme.brightness == Brightness.dark;
  final seedIsDark = ThemeData.estimateBrightnessForColor(seed) == Brightness.dark;

  if (onDark && seedIsDark) {
    return Color.lerp(seed, Colors.white, 0.55)!;
  }
  if (!onDark && !seedIsDark) {
    return Color.lerp(seed, Colors.black, 0.45)!;
  }
  return seed;
}

/// Glyph colour for a solid [seed] disc: white on saturated colours, near-black
/// on pale ones — the same rule the member avatars use.
Color onGroupSeed(Color seed) =>
    ThemeData.estimateBrightnessForColor(seed) == Brightness.dark ? Colors.white : Colors.black87;

final RegExp _letterGrapheme = RegExp(r'\p{L}', unicode: true);

/// Single dominant initial: first letter grapheme, else first grapheme, else `?`.
///
/// One letter plus colour disambiguates colliding names (`Art (1)` /
/// `Art NSFW` both give `A`) without the weight of a two-letter monogram.
String groupInitial(String name) {
  final trimmed = name.trim();
  if (trimmed.isEmpty) {
    return '?';
  }
  for (final grapheme in trimmed.characters) {
    if (_letterGrapheme.hasMatch(grapheme)) {
      return grapheme.toUpperCase();
    }
  }
  return trimmed.characters.first.toUpperCase();
}

bool _hasEmoji(String? emoji) => emoji != null && emoji.trim().isNotEmpty;

bool _hasCustomIcon(String icon) =>
    icon.isNotEmpty && icon != defaultGroupIcon && icon != 'rss' && icon != 'rss_feed';

/// Which mark content a chip should show for [markStyle] plus stored fields.
enum GroupMarkKind { emoji, initial, symbol }

GroupMarkKind resolveGroupMarkKind({
  required int markStyle,
  required String? emoji,
  required String icon,
}) {
  switch (GroupMarkStyle.coerce(markStyle)) {
    case GroupMarkStyle.emoji:
      return _hasEmoji(emoji) ? GroupMarkKind.emoji : GroupMarkKind.initial;
    case GroupMarkStyle.symbol:
      return _hasCustomIcon(icon) ? GroupMarkKind.symbol : GroupMarkKind.initial;
    case GroupMarkStyle.generated:
      return GroupMarkKind.initial;
    case GroupMarkStyle.auto:
    default:
      return _hasEmoji(emoji) ? GroupMarkKind.emoji : GroupMarkKind.initial;
  }
}

/// True when the user picked an explicit mark for this group, so it should win
/// over the member-faced mosaic on the board.
bool hasExplicitGroupMark(SubscriptionGroup group) {
  final style = GroupMarkStyle.coerce(group.markStyle);
  if (style == GroupMarkStyle.emoji || style == GroupMarkStyle.symbol) {
    return resolveGroupMarkKind(markStyle: style, emoji: group.emoji, icon: group.icon) !=
        GroupMarkKind.initial;
  }
  return _hasEmoji(group.emoji);
}

/// A group's identity disc: a solid circle in the group's own colour carrying
/// one emoji, initial or icon.
///
/// Deliberately not a Material tonal container — no `primaryContainer` pair, no
/// rounded-square chip, no ink. It matches the circular member avatars beside
/// it, so a group reads as "one of these accounts' faces" rather than as a
/// Material You chip.
class GroupMark extends StatelessWidget {
  final String name;
  final Color seed;
  final String? emoji;
  final String icon;
  final int markStyle;
  final double size;

  const GroupMark({
    super.key,
    required this.name,
    required this.seed,
    this.emoji,
    this.icon = '',
    this.markStyle = GroupMarkStyle.auto,
    this.size = 40,
  });

  factory GroupMark.forGroup(SubscriptionGroup group, {Key? key, double size = 40}) {
    return GroupMark(
      key: key,
      name: group.name,
      seed: groupSeedColor(group),
      emoji: group.emoji,
      icon: group.icon,
      markStyle: group.markStyle,
      size: size,
    );
  }

  @override
  Widget build(BuildContext context) {
    final onSeed = onGroupSeed(seed);
    final kind = resolveGroupMarkKind(markStyle: markStyle, emoji: emoji, icon: icon);
    final child = switch (kind) {
      GroupMarkKind.emoji => Text(
          emoji!.trim().characters.first,
          textScaler: TextScaler.noScaling,
          style: TextStyle(fontSize: size * 0.5, height: 1),
        ),
      GroupMarkKind.symbol => Icon(
          deserializeIconData(icon),
          size: size * 0.5,
          color: onSeed,
        ),
      GroupMarkKind.initial => Text(
          groupInitial(name),
          textScaler: TextScaler.noScaling,
          style: TextStyle(
            color: onSeed,
            fontWeight: FontWeight.w700,
            fontSize: size * 0.46,
            height: 1,
            letterSpacing: 0,
          ),
        ),
    };

    return ExcludeSemantics(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(color: seed, shape: BoxShape.circle),
        alignment: Alignment.center,
        child: child,
      ),
    );
  }
}
