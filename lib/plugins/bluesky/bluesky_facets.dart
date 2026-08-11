import 'dart:convert';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:xta/utils/json.dart';

/// One annotated span inside a Bluesky post — link, @mention, or #tag.
///
/// Facet indices are UTF-8 byte offsets into the post text (AT Protocol), not
/// Dart string indices.
class BlueskyFacet {
  final int byteStart;
  final int byteEnd;
  final BlueskyFacetKind kind;

  /// Link URI, mention DID, or hashtag name (without `#`).
  final String value;

  const BlueskyFacet({
    required this.byteStart,
    required this.byteEnd,
    required this.kind,
    required this.value,
  });

  Map<String, dynamic> toJson() => {
    'byteStart': byteStart,
    'byteEnd': byteEnd,
    'kind': kind.name,
    'value': value,
  };

  factory BlueskyFacet.fromSnapshot(Object? raw) {
    final json = raw is Map
        ? Map<String, dynamic>.from(raw)
        : const <String, dynamic>{};
    final kindName = json['kind'] as String? ?? '';
    final kind = BlueskyFacetKind.values
        .where((k) => k.name == kindName)
        .firstOrNull;
    return BlueskyFacet(
      byteStart: json['byteStart'] is num
          ? (json['byteStart'] as num).toInt()
          : 0,
      byteEnd: json['byteEnd'] is num ? (json['byteEnd'] as num).toInt() : 0,
      kind: kind ?? BlueskyFacetKind.link,
      value: json['value'] as String? ?? '',
    );
  }
}

enum BlueskyFacetKind { link, mention, tag }

/// Facets from a post `record` / `value`, ordered by byte start.
List<BlueskyFacet> blueskyFacetsOf(Json record) {
  final facets = <BlueskyFacet>[];
  for (final facet in record['facets'].list) {
    final start = facet['index']['byteStart'].integer;
    final end = facet['index']['byteEnd'].integer;
    if (start == null || end == null || end <= start) {
      continue;
    }
    for (final feature in facet['features'].list) {
      final type = feature['\$type'].string ?? '';
      if (type.contains('facet#link')) {
        final uri = feature['uri'].string?.trim();
        if (uri != null && uri.isNotEmpty) {
          facets.add(
            BlueskyFacet(
              byteStart: start,
              byteEnd: end,
              kind: BlueskyFacetKind.link,
              value: uri,
            ),
          );
          break;
        }
      }
      if (type.contains('facet#mention')) {
        final did = feature['did'].string?.trim();
        if (did != null && did.isNotEmpty) {
          facets.add(
            BlueskyFacet(
              byteStart: start,
              byteEnd: end,
              kind: BlueskyFacetKind.mention,
              value: did,
            ),
          );
          break;
        }
      }
      if (type.contains('facet#tag')) {
        final tag = feature['tag'].string?.trim();
        if (tag != null && tag.isNotEmpty) {
          facets.add(
            BlueskyFacet(
              byteStart: start,
              byteEnd: end,
              kind: BlueskyFacetKind.tag,
              value: tag,
            ),
          );
          break;
        }
      }
    }
  }
  facets.sort((a, b) => a.byteStart.compareTo(b.byteStart));
  return List.unmodifiable(facets);
}

/// Maps UTF-8 byte offsets onto Dart string indices for [text].
List<int> blueskyUtf8IndexMap(String text) {
  final bytes = utf8.encode(text);
  final map = List<int>.filled(bytes.length + 1, text.length);
  var byteIndex = 0;
  var stringIndex = 0;
  for (final rune in text.runes) {
    final encoded = utf8.encode(String.fromCharCode(rune));
    for (var i = 0; i < encoded.length; i++) {
      map[byteIndex + i] = stringIndex;
    }
    byteIndex += encoded.length;
    stringIndex += String.fromCharCode(rune).length;
  }
  map[bytes.length] = text.length;
  return map;
}

int _stringIndex(List<int> map, int byteOffset) {
  if (byteOffset <= 0) {
    return 0;
  }
  if (byteOffset >= map.length) {
    return map.last;
  }
  return map[byteOffset];
}

/// Builds tappable [TextSpan]s for a Bluesky post body.
///
/// Callers own [recognizers] and must dispose them (see [BlueskyRichText]).
List<InlineSpan> blueskyRichTextSpans({
  required String text,
  required List<BlueskyFacet> facets,
  required TextStyle style,
  required TextStyle linkStyle,
  required void Function(BlueskyFacet facet) onFacetTap,
  required List<GestureRecognizer> recognizers,
}) {
  if (text.isEmpty) {
    return const [];
  }
  if (facets.isEmpty) {
    return [TextSpan(text: text, style: style)];
  }

  final map = blueskyUtf8IndexMap(text);
  final spans = <InlineSpan>[];
  var cursor = 0;

  for (final facet in facets) {
    final start = _stringIndex(map, facet.byteStart).clamp(0, text.length);
    final end = _stringIndex(map, facet.byteEnd).clamp(0, text.length);
    if (end <= start || start < cursor) {
      continue;
    }
    if (start > cursor) {
      spans.add(TextSpan(text: text.substring(cursor, start), style: style));
    }
    final recognizer = TapGestureRecognizer()..onTap = () => onFacetTap(facet);
    recognizers.add(recognizer);
    spans.add(
      TextSpan(
        text: text.substring(start, end),
        style: linkStyle,
        recognizer: recognizer,
      ),
    );
    cursor = end;
  }

  if (cursor < text.length) {
    spans.add(TextSpan(text: text.substring(cursor), style: style));
  }

  return spans;
}

/// Post body with tappable mentions, links, and tags — Graysky / Sky.app style.
class BlueskyRichText extends StatefulWidget {
  final String text;
  final List<BlueskyFacet> facets;
  final TextStyle? style;
  final int? maxLines;
  final TextOverflow overflow;
  final void Function(BlueskyFacet facet)? onFacetTap;

  const BlueskyRichText({
    super.key,
    required this.text,
    this.facets = const [],
    this.style,
    this.maxLines,
    this.overflow = TextOverflow.clip,
    this.onFacetTap,
  });

  @override
  State<BlueskyRichText> createState() => _BlueskyRichTextState();
}

class _BlueskyRichTextState extends State<BlueskyRichText> {
  final List<GestureRecognizer> _recognizers = [];

  @override
  void dispose() {
    _disposeRecognizers();
    super.dispose();
  }

  void _disposeRecognizers() {
    for (final recognizer in _recognizers) {
      recognizer.dispose();
    }
    _recognizers.clear();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style =
        widget.style ?? theme.textTheme.bodyLarge!.copyWith(height: 1.35);
    final linkStyle = style.copyWith(color: theme.colorScheme.primary);

    _disposeRecognizers();
    final spans = blueskyRichTextSpans(
      text: widget.text,
      facets: widget.facets,
      style: style,
      linkStyle: linkStyle,
      onFacetTap: widget.onFacetTap ?? (_) {},
      recognizers: _recognizers,
    );

    return Text.rich(
      TextSpan(style: style, children: spans),
      maxLines: widget.maxLines,
      overflow: widget.overflow,
    );
  }
}
