import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

/// Status body with tappable `@mentions` and `#tags`, like Tusky / Ivory.
class MastodonRichText extends StatefulWidget {
  final String text;
  final List<String> mentionAccts;
  final TextStyle? style;
  final int? maxLines;
  final ValueChanged<String>? onMentionTap;
  final ValueChanged<String>? onTagTap;

  const MastodonRichText({
    super.key,
    required this.text,
    this.mentionAccts = const [],
    this.style,
    this.maxLines,
    this.onMentionTap,
    this.onTagTap,
  });

  @override
  State<MastodonRichText> createState() => _MastodonRichTextState();
}

class _MastodonRichTextState extends State<MastodonRichText> {
  final _recognizers = <GestureRecognizer>[];

  @override
  void dispose() {
    _clear();
    super.dispose();
  }

  void _clear() {
    for (final recognizer in _recognizers) {
      recognizer.dispose();
    }
    _recognizers.clear();
  }

  @override
  Widget build(BuildContext context) {
    _clear();
    final theme = Theme.of(context);
    final style =
        widget.style ?? theme.textTheme.bodyLarge!.copyWith(height: 1.35);
    final link = style.copyWith(color: theme.colorScheme.primary);
    final parts = mastodonTextParts(
      widget.text,
      mentionAccts: widget.mentionAccts,
    );
    return Text.rich(
      TextSpan(children: [for (final part in parts) _span(part, style, link)]),
      maxLines: widget.maxLines,
      overflow: widget.maxLines == null
          ? TextOverflow.clip
          : TextOverflow.ellipsis,
    );
  }

  InlineSpan _span(MastodonTextPart part, TextStyle style, TextStyle link) {
    if (part.kind == MastodonTextKind.text) {
      return TextSpan(text: part.text, style: style);
    }
    final recognizer = TapGestureRecognizer()..onTap = () => _open(part);
    _recognizers.add(recognizer);
    return TextSpan(text: part.text, style: link, recognizer: recognizer);
  }

  void _open(MastodonTextPart part) {
    if (part.kind == MastodonTextKind.tag) {
      widget.onTagTap?.call(part.value);
      return;
    }
    widget.onMentionTap?.call(part.value);
  }
}
