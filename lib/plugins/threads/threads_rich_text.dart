import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/plugin_links.dart';

final _threadsMention = RegExp(r'(?<![A-Za-z0-9_])@([A-Za-z0-9._]{1,30})');
final _threadsUrl = RegExp(r'https?://[^\s<>]+', caseSensitive: false);

/// Caption text with tappable @handles and http(s) links — no Meta entities needed.
class ThreadsCaption extends StatefulWidget {
  final String text;
  final TextStyle style;

  const ThreadsCaption({super.key, required this.text, required this.style});

  @override
  State<ThreadsCaption> createState() => _ThreadsCaptionState();
}

class _ThreadsCaptionState extends State<ThreadsCaption> {
  final _recognizers = <TapGestureRecognizer>[];

  @override
  void dispose() {
    _clearRecognizers();
    super.dispose();
  }

  void _clearRecognizers() {
    for (final recognizer in _recognizers) {
      recognizer.dispose();
    }
    _recognizers.clear();
  }

  TapGestureRecognizer _tap(VoidCallback onTap) {
    final recognizer = TapGestureRecognizer()..onTap = onTap;
    _recognizers.add(recognizer);
    return recognizer;
  }

  @override
  Widget build(BuildContext context) {
    _clearRecognizers();
    final linkStyle = widget.style.copyWith(color: Theme.of(context).colorScheme.primary);
    return Text.rich(TextSpan(style: widget.style, children: _spans(context, linkStyle)));
  }

  List<InlineSpan> _spans(BuildContext context, TextStyle linkStyle) {
    final text = widget.text;
    final hits = hitsInThreadsCaption(text);
    final spans = <InlineSpan>[];
    var cursor = 0;
    for (final hit in hits) {
      if (hit.start < cursor) {
        continue;
      }
      if (hit.start > cursor) {
        spans.add(TextSpan(text: text.substring(cursor, hit.start)));
      }
      spans.add(_spanFor(context, text, hit, linkStyle));
      cursor = hit.end;
    }
    if (cursor < text.length) {
      spans.add(TextSpan(text: text.substring(cursor)));
    }
    return spans;
  }

  InlineSpan _spanFor(BuildContext context, String text, ThreadsCaptionHit hit, TextStyle linkStyle) {
    final label = text.substring(hit.start, hit.end);
    if (hit.isMention) {
      final handle = normaliseThreadsHandle(hit.value) ?? hit.value.toLowerCase();
      return TextSpan(
        text: label,
        style: linkStyle,
        recognizer: _tap(() {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => ThreadsProfileScreen(username: handle)),
          );
        }),
      );
    }
    return TextSpan(
      text: label,
      style: linkStyle,
      recognizer: _tap(() => openLink(context, hit.value)),
    );
  }
}

/// One tappable run inside a Threads caption — exposed for tests.
class ThreadsCaptionHit {
  final int start;
  final int end;
  final String value;
  final bool isMention;

  const ThreadsCaptionHit({
    required this.start,
    required this.end,
    required this.value,
    required this.isMention,
  });
}

/// Pure split of [text] into mention / URL hits, left-to-right, no overlaps kept.
List<ThreadsCaptionHit> hitsInThreadsCaption(String text) {
  final hits = <ThreadsCaptionHit>[];
  for (final match in _threadsMention.allMatches(text)) {
    hits.add(ThreadsCaptionHit(
      start: match.start,
      end: match.end,
      value: match.group(1)!,
      isMention: true,
    ));
  }
  for (final match in _threadsUrl.allMatches(text)) {
    final trimmed = trimThreadsCaptionUrl(match.group(0)!);
    hits.add(ThreadsCaptionHit(
      start: match.start,
      end: match.start + trimmed.length,
      value: trimmed,
      isMention: false,
    ));
  }
  hits.sort((a, b) => a.start.compareTo(b.start));
  return hits;
}

String trimThreadsCaptionUrl(String raw) {
  var url = raw;
  while (url.isNotEmpty && '.,);]!?"\''.contains(url[url.length - 1])) {
    url = url.substring(0, url.length - 1);
  }
  return url;
}
