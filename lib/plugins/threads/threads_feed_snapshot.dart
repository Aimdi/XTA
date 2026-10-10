import 'dart:convert';

import 'package:xta/plugins/threads/threads_models.dart';

/// How many of the newest posts the Threads tab keeps across a restart.
const kThreadsFeedSnapshotCap = 120;

/// The Threads tab as it last stood: the newest posts, and when each followed
/// account last answered.
///
/// Restoring it paints the tab the moment the app opens, and — while each
/// account is still inside its cache window — spares Meta the same questions a
/// minute after a restart. Only the newest [kThreadsFeedSnapshotCap] posts are
/// kept; an account none of whose posts made the cut is restored as having
/// nothing that recent, which is what the merged feed would show anyway.
class ThreadsFeedSnapshot {
  final List<ThreadsPost> posts;
  final Map<String, DateTime> answeredAt;

  const ThreadsFeedSnapshot({required this.posts, required this.answeredAt});

  static const empty = ThreadsFeedSnapshot(posts: [], answeredAt: {});

  bool get isEmpty => posts.isEmpty;

  String encode() => jsonEncode({
    'v': 1,
    'answeredAt': {for (final e in answeredAt.entries) e.key: e.value.toIso8601String()},
    'posts': [for (final post in posts.take(kThreadsFeedSnapshotCap)) post.toJson()],
  });

  static ThreadsFeedSnapshot decode(String? raw) {
    if (raw == null || raw.isEmpty) {
      return empty;
    }
    try {
      final json = jsonDecode(raw);
      if (json is! Map || json['v'] != 1) {
        return empty;
      }
      return ThreadsFeedSnapshot(
        posts: [
          for (final post in (json['posts'] is List ? json['posts'] as List : const []))
            if (post is Map) ThreadsPost.fromSnapshot(post),
        ].where((post) => post.id.isNotEmpty).toList(growable: false),
        answeredAt: _answeredAt(json['answeredAt']),
      );
    } catch (_) {
      return empty;
    }
  }

  static Map<String, DateTime> _answeredAt(Object? raw) => {
    if (raw is Map)
      for (final e in raw.entries)
        if (e.value is String) '${e.key}': ?DateTime.tryParse(e.value as String),
  };

  /// Only what the reader still follows: an account dropped since must not
  /// come back from the snapshot.
  ThreadsFeedSnapshot followedBy(Set<String> handles) => ThreadsFeedSnapshot(
    posts: [
      for (final post in posts)
        if (handles.contains(post.sourceHandle)) post,
    ],
    answeredAt: {
      for (final e in answeredAt.entries)
        if (handles.contains(e.key)) e.key: e.value,
    },
  );

  /// Each account's own posts, the way the per-account cache holds them.
  List<ThreadsPost> postsOf(String handle) => [
    for (final post in posts)
      if (post.sourceHandle == handle) post,
  ];
}
