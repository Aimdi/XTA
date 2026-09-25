import 'dart:convert';

import 'package:xta/plugins/bluesky/bluesky_facets.dart';

/// Profile descriptions contain plain text, without record facets.
List<BlueskyFacet> blueskyProfileLinks(String text) {
  final matches = RegExp(r'''\bhttps?://[^\s<>"']+''', caseSensitive: false).allMatches(text);
  return [
    for (final match in matches)
      if (_profileUrl(match.group(0)!) case final String url)
        BlueskyFacet(
          byteStart: utf8.encode(text.substring(0, match.start)).length,
          byteEnd: utf8.encode(text.substring(0, match.start + url.length)).length,
          kind: BlueskyFacetKind.link,
          value: url,
        ),
  ];
}

String? _profileUrl(String candidate) {
  var url = candidate.replaceFirst(RegExp(r'[.,;:!?]+$'), '');
  for (final pair in const [('(', ')'), ('[', ']'), ('{', '}')]) {
    while (url.endsWith(pair.$2) && pair.$2.allMatches(url).length > pair.$1.allMatches(url).length) {
      url = url.substring(0, url.length - 1);
    }
  }
  final uri = Uri.tryParse(url);
  return uri != null && uri.hasAuthority && uri.host.isNotEmpty && uri.userInfo.isEmpty ? url : null;
}
