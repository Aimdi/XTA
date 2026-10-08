import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_links.dart';

typedef SubstackTextPart = ({String text, String? url});

String? substackDiscussionUrl(String? value) {
  final uri = Uri.tryParse(value?.trim() ?? '');
  return uri != null && uri.host.isNotEmpty && (uri.scheme == 'https' || uri.scheme == 'http') ? uri.toString() : null;
}

List<SubstackTextPart> substackDiscussionTextParts(String text) {
  final parts = <SubstackTextPart>[];
  var offset = 0;
  for (final match in RegExp(r'''https?://[^\s<>\[\]"]+''', caseSensitive: false).allMatches(text)) {
    var candidate = match.group(0)!.replaceFirst(RegExp(r'[.,!?;:]+$'), '');
    while (candidate.endsWith(')') && ')'.allMatches(candidate).length > '('.allMatches(candidate).length) {
      candidate = candidate.substring(0, candidate.length - 1);
    }
    final url = substackDiscussionUrl(candidate);
    if (url == null) continue;
    if (match.start > offset) parts.add((text: text.substring(offset, match.start), url: null));
    parts.add((text: candidate, url: url));
    offset = match.start + candidate.length;
  }
  if (offset < text.length) parts.add((text: text.substring(offset), url: null));
  return parts;
}

/// Notes and comments keep their original text while web links open through XTA.
class SubstackDiscussionText extends StatefulWidget {
  final String text;
  final TextStyle? style;
  final bool selectable;
  final ValueChanged<String>? onOpenLink;
  const SubstackDiscussionText({super.key, required this.text, this.style, this.selectable = false, this.onOpenLink});
  @override
  State<SubstackDiscussionText> createState() => _SubstackDiscussionTextState();
}

class _SubstackDiscussionTextState extends State<SubstackDiscussionText> {
  List<SubstackTextPart> _parts = const [];
  final _recognizers = <int, TapGestureRecognizer>{};
  @override
  void initState() {
    super.initState();
    _readText();
  }

  @override
  void didUpdateWidget(SubstackDiscussionText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.text != widget.text) _readText();
  }

  void _readText() {
    for (final recognizer in _recognizers.values) {
      recognizer.dispose();
    }
    _recognizers.clear();
    _parts = substackDiscussionTextParts(widget.text);
    for (var i = 0; i < _parts.length; i++) {
      final url = _parts[i].url;
      if (url != null) {
        _recognizers[i] = TapGestureRecognizer()
          ..onTap = () {
            if (widget.onOpenLink != null) {
              widget.onOpenLink!(url);
            } else {
              openLink(context, url);
            }
          };
      }
    }
  }

  @override
  void dispose() {
    for (final recognizer in _recognizers.values) {
      recognizer.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final text = Text.rich(
      TextSpan(
        children: [
          for (var i = 0; i < _parts.length; i++)
            TextSpan(
              text: _parts[i].text,
              recognizer: _recognizers[i],
              style: _parts[i].url == null
                  ? null
                  : TextStyle(color: Theme.of(context).colorScheme.primary, decoration: TextDecoration.underline),
            ),
        ],
      ),
      style: widget.style,
    );
    return widget.selectable ? SelectionArea(child: text) : text;
  }
}
