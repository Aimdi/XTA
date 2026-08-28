import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_links.dart';

/// One run of Reddit markdown: ordinary words, or a tappable link.
class RedditTextPart {
  final String text;
  final String? url;

  const RedditTextPart(this.text, [this.url]);

  bool get isLink => url != null;
}

final _redditTextLink = RegExp(
  r'\[([^\]\n]{1,200})\]\((https?://[^)\s]+)\)|https?://[^\s\]\)<>]+',
  caseSensitive: false,
);

final _xLinkHosts = {'x.com', 'www.x.com', 'twitter.com', 'www.twitter.com'};

/// Splits a Reddit title, selftext or comment into words and links.
///
/// `[label](url)` keeps the label; a bare URL is shortened to host / path so
/// a Drive folder or an X status does not print its query string across four
/// lines.
List<RedditTextPart> redditTextParts(String raw) {
  if (raw.isEmpty) {
    return const [];
  }

  final parts = <RedditTextPart>[];
  var cursor = 0;
  for (final match in _redditTextLink.allMatches(raw)) {
    if (match.start > cursor) {
      parts.add(RedditTextPart(raw.substring(cursor, match.start)));
    }
    final labelled = match.group(1);
    final url = labelled == null ? match.group(0)! : match.group(2)!;
    final trimmedUrl = _trimUrlJunk(url);
    parts.add(
      RedditTextPart(
        compactRedditLinkLabel(trimmedUrl, label: labelled),
        trimmedUrl,
      ),
    );
    cursor = match.end;
  }
  if (cursor < raw.length) {
    parts.add(RedditTextPart(raw.substring(cursor)));
  }
  return parts;
}

/// What a link is called on the card: the markdown label when it is a word,
/// otherwise the host (and a short path when that is the whole identity).
String compactRedditLinkLabel(String url, {String? label}) {
  final named = label?.trim();
  if (named != null &&
      named.isNotEmpty &&
      !named.toLowerCase().startsWith('http')) {
    return named.length > 42 ? '${named.substring(0, 41)}…' : named;
  }

  final uri = Uri.tryParse(url);
  if (uri == null || uri.host.isEmpty) {
    return url;
  }
  final host = uri.host.replaceFirst(RegExp(r'^www\.'), '');
  final segs = uri.pathSegments.where((s) => s.isNotEmpty).toList();
  if (segs.isEmpty) {
    return host;
  }
  if (host == 'drive.google.com' || host.endsWith('.google.com')) {
    return host;
  }
  if (_xLinkHosts.contains(uri.host) ||
      host == 'x.com' ||
      host == 'twitter.com') {
    return '$host/${segs.first}';
  }
  if (host == 'reddit.com' || host.endsWith('.reddit.com')) {
    if (segs.first == 'r' && segs.length >= 2) {
      return 'r/${segs[1]}';
    }
    if ((segs.first == 'u' || segs.first == 'user') && segs.length >= 2) {
      return 'u/${segs[1]}';
    }
  }
  final first = segs.first;
  if (first.length <= 22) {
    return '$host/$first';
  }
  return host;
}

String _trimUrlJunk(String url) {
  return url.replaceFirst(RegExp(r'[.,;:!?]+$'), '');
}

/// Comment / selftext with tappable, shortened links.
///
/// Links are [WidgetSpan]s so a tap hits the label itself, not a parent
/// [InkWell] that would otherwise open the thread or fold the comment.
class RedditRichText extends StatelessWidget {
  final String text;
  final TextStyle? style;
  final int? maxLines;

  const RedditRichText({
    super.key,
    required this.text,
    this.style,
    this.maxLines,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = this.style ?? theme.textTheme.bodyMedium!;
    final parts = redditTextParts(text);
    if (parts.every((part) => !part.isLink)) {
      return Text(
        text,
        style: style,
        maxLines: maxLines,
        overflow: maxLines == null ? TextOverflow.clip : TextOverflow.ellipsis,
      );
    }

    final blue = theme.brightness == Brightness.dark
        ? const Color(0xFF4EA3FF)
        : const Color(0xFF1565C0);
    final linkStyle = style.copyWith(
      color: blue,
      decoration: TextDecoration.underline,
      decorationColor: blue,
    );

    return Text.rich(
      TextSpan(
        children: [
          for (final part in parts) _span(context, part, style, linkStyle),
        ],
      ),
      maxLines: maxLines,
      overflow: maxLines == null ? TextOverflow.clip : TextOverflow.ellipsis,
    );
  }

  InlineSpan _span(
    BuildContext context,
    RedditTextPart part,
    TextStyle style,
    TextStyle link,
  ) {
    final url = part.url;
    if (url == null) {
      return TextSpan(text: part.text, style: style);
    }
    return WidgetSpan(
      alignment: PlaceholderAlignment.baseline,
      baseline: TextBaseline.alphabetic,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () {
          if (context.mounted) {
            openLink(context, url);
          }
        },
        child: Text(part.text, style: link),
      ),
    );
  }
}
