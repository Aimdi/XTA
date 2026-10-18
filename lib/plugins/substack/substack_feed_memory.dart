import 'dart:convert';

import 'package:xta/constants.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/utils/json.dart';

/// One followed publication's first page, as it last answered.
typedef SubstackRememberedPage = ({DateTime answeredAt, List<SubstackPost> posts});

/// The Substack feed as it last stood, kept across a restart: each followed
/// publication's first page, without bodies, and when it answered.
///
/// Restoring it paints the feed the moment the screen opens, and spares
/// Substack the same questions for publications still inside their cache
/// window.
class SubstackFeedMemory {
  final Map<String, SubstackRememberedPage> pages;

  const SubstackFeedMemory(this.pages);

  static const empty = SubstackFeedMemory({});

  String encode() => jsonEncode({
    'v': 1,
    'pages': {
      for (final MapEntry(key: key, value: page) in pages.entries)
        key: {
          'answeredAt': page.answeredAt.toUtc().toIso8601String(),
          'posts': [for (final post in page.posts.take(substackFeedPageSize)) post.toJson()],
        },
    },
  });

  static SubstackFeedMemory decode(String? raw) {
    if (raw == null || raw.isEmpty) return empty;
    try {
      final json = Json(jsonDecode(raw));
      if (json['v'].integer != 1) return empty;
      final pages = json['pages'].raw;
      if (pages is! Map) return empty;
      return SubstackFeedMemory({for (final MapEntry(:key, :value) in pages.entries) '$key': ?_page(Json(value))});
    } catch (_) {
      return empty;
    }
  }

  static SubstackRememberedPage? _page(Json json) {
    final answeredAt = DateTime.tryParse(json['answeredAt'].string ?? '');
    if (answeredAt == null) return null;
    final posts = [
      for (final post in json['posts'].list)
        if (post.raw is Map) SubstackPost.fromSnapshot(Map<String, dynamic>.from(post.raw as Map)),
    ].where((post) => post.id.isNotEmpty).toList(growable: false);
    return (answeredAt: answeredAt, posts: posts);
  }
}
