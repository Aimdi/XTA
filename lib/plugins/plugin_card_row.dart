/// The one row shape every plugin card uses for "who, when, and what kind".
///
/// Each plugin had grown its own: a [Row] whose author name was [Flexible] but
/// whose timestamp was not, a [Wrap] with a [Flexible] inside it, a bullet
/// string with no line limit. In English they all happened to fit. In German
/// the same rows run 30% longer and the last word either falls off the edge
/// behind a yellow stripe or pushes the badges out of the card, so the shapes
/// are shared now and every one of them gives way under pressure.
library;

import 'package:flutter/material.dart';

/// Gap between a name and the metadata that follows it.
const double kPluginMetaGap = 6;

/// Separator between metadata parts, as every plugin already wrote by hand.
const String kPluginMetaSeparator = ' · ';

/// Name first, muted metadata after it, on one line that always fits.
///
/// The name gives way first — it is the part a reader can still recognise
/// half-shown — and the metadata ellipsises rather than pushing off the edge.
class PluginNameMetaRow extends StatelessWidget {
  /// Usually a tappable name. Laid out flexibly, so it must ellipsise itself.
  final Widget name;

  /// Timestamp, edit marker, and anything else that qualifies the name.
  final List<String> meta;

  final TextStyle? metaStyle;

  const PluginNameMetaRow({
    super.key,
    required this.name,
    this.meta = const [],
    this.metaStyle,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final parts = [
      for (final part in meta)
        if (part.trim().isNotEmpty) part.trim(),
    ];

    if (parts.isEmpty) {
      return Row(children: [Flexible(child: name)]);
    }

    return Row(
      children: [
        Flexible(child: name),
        const SizedBox(width: kPluginMetaGap),
        // Flexible too: on a narrow phone in a long locale the timestamp and
        // the edit marker together are wider than the row, and something has
        // to be allowed to shorten.
        Flexible(
          child: Text(
            parts.join(kPluginMetaSeparator),
            maxLines: 1,
            softWrap: false,
            overflow: TextOverflow.ellipsis,
            style:
                metaStyle ??
                theme.textTheme.bodySmall!.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
          ),
        ),
      ],
    );
  }
}

/// Bullet-separated card metadata — points, author, age, comment count.
///
/// One muted line, two at most, ellipsised after that. A card whose footer
/// grew to four lines in German pushed the next card off the screen.
class PluginMetaLine extends StatelessWidget {
  final List<String> parts;
  final int maxLines;
  final TextStyle? style;

  const PluginMetaLine({
    super.key,
    required this.parts,
    this.maxLines = 2,
    this.style,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final kept = [
      for (final part in parts)
        if (part.trim().isNotEmpty) part.trim(),
    ];

    if (kept.isEmpty) return const SizedBox.shrink();

    return Text(
      kept.join(kPluginMetaSeparator),
      maxLines: maxLines,
      overflow: TextOverflow.ellipsis,
      style:
          style ??
          theme.textTheme.bodySmall!.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
    );
  }
}

/// The small outlined label a card uses to say "Reddit", "NSFW", "Pinned".
///
/// Never wider than the card: a long word in a long locale shortens instead of
/// pushing the badges next to it off the edge.
class PluginCardBadge extends StatelessWidget {
  final String label;
  final Color? tint;

  const PluginCardBadge({super.key, required this.label, this.tint});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
      decoration: BoxDecoration(
        border: Border.all(color: tint ?? theme.colorScheme.outline),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.labelSmall!.copyWith(color: tint),
      ),
    );
  }
}

/// A handle and the badges that qualify it, wrapping onto a second line rather
/// than running off the edge.
///
/// [handle] is laid out first at whatever width is left; the badges follow it
/// and drop to the next run when they no longer fit.
class PluginHandleBadgeRow extends StatelessWidget {
  /// Null where there is no handle to show — a deleted Reddit account — so the
  /// badges start the row rather than sitting behind an empty gap.
  final Widget? handle;
  final List<Widget> badges;

  const PluginHandleBadgeRow({super.key, this.handle, this.badges = const []});

  @override
  Widget build(BuildContext context) {
    final handle = this.handle;

    if (badges.isEmpty) {
      return handle == null
          ? const SizedBox.shrink()
          : Row(children: [Flexible(child: handle)]);
    }

    return LayoutBuilder(
      builder: (context, constraints) => Wrap(
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: kPluginMetaGap,
        runSpacing: 2,
        children: [
          // A Wrap hands every child the full width, so the handle needs the
          // bound spelled out before it will ellipsise instead of overflowing.
          if (handle != null)
            ConstrainedBox(
              constraints: BoxConstraints(maxWidth: constraints.maxWidth),
              child: handle,
            ),
          ...badges,
        ],
      ),
    );
  }
}
