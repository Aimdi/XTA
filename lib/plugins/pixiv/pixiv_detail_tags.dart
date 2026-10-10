import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';

/// A work's tags, each searching Pixiv for itself.
class PixivDetailTags extends StatelessWidget {
  final List<PixivTag> tags;

  const PixivDetailTags({super.key, required this.tags});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 4,
      children: [
        for (final tag in tags)
          ActionChip(
            label: _label(context, tag),
            visualDensity: VisualDensity.compact,
            materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute<void>(builder: (_) => PixivSearchScreen(initialQuery: tag.name)),
            ),
          ),
      ],
    );
  }

  /// The tag as Pixiv spells it, with its translation beside it when there is one.
  Widget _label(BuildContext context, PixivTag tag) {
    final translated = tag.translatedName?.trim() ?? '';
    if (translated.isEmpty || translated == tag.name) return Text('#${tag.name}');
    final muted = Theme.of(context).colorScheme.onSurfaceVariant;
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(text: '#${tag.name}'),
          TextSpan(
            text: '  $translated',
            style: TextStyle(color: muted),
          ),
        ],
      ),
    );
  }
}
