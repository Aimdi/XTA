import 'dart:convert';

/// Locally followed Substack publication.
class SubstackPublication {
  final String subdomain;
  final String baseUrl;
  final String name;
  final String? description;
  final String? logoUrl;

  const SubstackPublication({
    required this.subdomain,
    required this.baseUrl,
    required this.name,
    this.description,
    this.logoUrl,
  });

  String get id => subdomain.toLowerCase();

  Map<String, dynamic> toJson() => {
    'subdomain': subdomain,
    'baseUrl': baseUrl,
    'name': name,
    'description': description,
    'logoUrl': logoUrl,
  };

  factory SubstackPublication.fromJson(Map<String, dynamic> json) {
    return SubstackPublication(
      subdomain: json['subdomain'] as String? ?? '',
      baseUrl: json['baseUrl'] as String? ?? '',
      name: json['name'] as String? ?? json['subdomain'] as String? ?? '',
      description: json['description'] as String?,
      logoUrl: json['logoUrl'] as String?,
    );
  }

  static List<SubstackPublication> listFromPrefs(String? raw) {
    if (raw == null || raw.isEmpty) return const [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return const [];
      return decoded
          .whereType<Map>()
          .map(
            (e) => SubstackPublication.fromJson(Map<String, dynamic>.from(e)),
          )
          .where((e) => e.subdomain.isNotEmpty && e.baseUrl.isNotEmpty)
          .toList();
    } catch (_) {
      return const [];
    }
  }

  static String listToPrefs(List<SubstackPublication> pubs) =>
      jsonEncode(pubs.map((e) => e.toJson()).toList());
}

class SubstackPost {
  final String id;
  final String title;
  final String? subtitle;
  final String? description;
  final String slug;
  final String? postDate;
  final String? canonicalUrl;
  final String? coverImage;
  final String? bodyHtml;
  final String? audience;
  final String? authorName;
  final String publicationBaseUrl;
  final String publicationName;

  /// What kind of post this is: `newsletter`, `podcast`, `video`, …
  final String? type;

  /// Set when the post is built around a video Substack hosts itself.
  final bool hasVideoUpload;

  /// The episode file of a podcast post, playable directly.
  final String? audioUrl;

  /// What the post has gathered. Null when the payload did not carry them,
  /// which is not the same as zero.
  final int? reactionCount;
  final int? commentCount;

  const SubstackPost({
    required this.id,
    required this.title,
    required this.slug,
    required this.publicationBaseUrl,
    required this.publicationName,
    this.type,
    this.hasVideoUpload = false,
    this.subtitle,
    this.description,
    this.postDate,
    this.canonicalUrl,
    this.coverImage,
    this.bodyHtml,
    this.audience,
    this.authorName,
    this.audioUrl,
    this.reactionCount,
    this.commentCount,
  });

  /// Substack uses `only_paid` (and occasionally founding tiers) for gated posts.
  bool get isPaywalled {
    final value = audience?.toLowerCase();
    return value == 'only_paid' ||
        value == 'only_paying' ||
        value == 'founding' ||
        value == 'only_founding';
  }

  String? get excerpt {
    final subtitleText = subtitle?.trim();
    if (subtitleText != null &&
        subtitleText.isNotEmpty &&
        subtitleText != '...') {
      return subtitleText;
    }
    final descriptionText = description?.trim();
    if (descriptionText != null &&
        descriptionText.isNotEmpty &&
        descriptionText != '...') {
      return descriptionText;
    }
    return null;
  }

  DateTime? get publishedAt {
    final raw = postDate;
    if (raw == null || raw.isEmpty) return null;
    return DateTime.tryParse(raw)?.toLocal();
  }

  factory SubstackPost.fromJson(
    Map<String, dynamic> json, {
    required String publicationBaseUrl,
    required String publicationName,
    bool includeBody = true,
  }) {
    final bylines = json['publishedBylines'];
    String? author;
    if (bylines is List && bylines.isNotEmpty) {
      final first = bylines.first;
      if (first is Map) author = first['name'] as String?;
    }

    return SubstackPost(
      id: '${json['id'] ?? json['slug'] ?? ''}',
      title: json['title'] as String? ?? '',
      subtitle: json['subtitle'] as String?,
      description: json['description'] as String?,
      slug: json['slug'] as String? ?? '',
      postDate: json['post_date'] as String?,
      canonicalUrl: json['canonical_url'] as String?,
      coverImage: json['cover_image'] as String?,
      bodyHtml: includeBody ? json['body_html'] as String? : null,
      audience: json['audience'] as String?,
      authorName: author,
      type: json['type'] as String?,
      // Substack has moved this field around, so presence is what is checked
      // rather than any particular shape of it.
      hasVideoUpload:
          json['videoUpload'] != null || json['video_upload'] != null,
      audioUrl: json['podcast_url'] as String?,
      reactionCount:
          _countOf(json['reaction_count']) ??
          _sumOfReactions(json['reactions']),
      commentCount: _countOf(json['comment_count']),
      publicationBaseUrl: publicationBaseUrl,
      publicationName: publicationName,
    );
  }

  static int? _countOf(Object? value) => value is num ? value.toInt() : null;

  /// Older payloads carry no total, only the per-emoji map.
  static int? _sumOfReactions(Object? reactions) {
    if (reactions is! Map || reactions.isEmpty) {
      return null;
    }
    var total = 0;
    for (final value in reactions.values) {
      if (value is num) total += value.toInt();
    }
    return total;
  }

  bool get isPodcast => audioUrl != null && audioUrl!.isNotEmpty;

  /// Whether the post leads with a video, which the tile marks and the reader
  /// plays. A video post used to be indistinguishable from any other, so the
  /// only clue that there was one was opening it.
  bool get isVideo => hasVideoUpload || type?.toLowerCase() == 'video';

  SubstackPublication get publication => SubstackPublication(
    subdomain: subdomainOf(Uri.parse(publicationBaseUrl)),
    baseUrl: publicationBaseUrl,
    name: publicationName,
  );

  /// Snapshot for local likes/saves. Omits [bodyHtml] — the reader fetches it.
  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'subtitle': subtitle,
    'description': description,
    'slug': slug,
    'post_date': postDate,
    'canonical_url': canonicalUrl,
    'cover_image': coverImage,
    'audience': audience,
    'authorName': authorName,
    'type': type,
    'hasVideoUpload': hasVideoUpload,
    'podcast_url': audioUrl,
    'reaction_count': reactionCount,
    'comment_count': commentCount,
    'publicationBaseUrl': publicationBaseUrl,
    'publicationName': publicationName,
  };

  /// Rebuild from a local like/save snapshot (or a prefs list entry).
  factory SubstackPost.fromSnapshot(Map<String, dynamic> json) {
    final base = json['publicationBaseUrl'] as String? ?? '';
    final name = json['publicationName'] as String? ?? '';
    return SubstackPost(
      id: '${json['id'] ?? json['slug'] ?? ''}',
      title: json['title'] as String? ?? '',
      subtitle: json['subtitle'] as String?,
      description: json['description'] as String?,
      slug: json['slug'] as String? ?? '',
      postDate: json['post_date'] as String? ?? json['postDate'] as String?,
      canonicalUrl:
          json['canonical_url'] as String? ?? json['canonicalUrl'] as String?,
      coverImage:
          json['cover_image'] as String? ?? json['coverImage'] as String?,
      audience: json['audience'] as String?,
      authorName: json['authorName'] as String?,
      type: json['type'] as String?,
      hasVideoUpload: json['hasVideoUpload'] == true,
      audioUrl: json['podcast_url'] as String? ?? json['audioUrl'] as String?,
      reactionCount: json['reaction_count'] is num
          ? (json['reaction_count'] as num).toInt()
          : null,
      commentCount: json['comment_count'] is num
          ? (json['comment_count'] as num).toInt()
          : null,
      publicationBaseUrl: base,
      publicationName: name,
    );
  }

  static List<SubstackPost> listFromPrefs(String? raw) {
    if (raw == null || raw.isEmpty) return const [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return const [];
      return decoded
          .whereType<Map>()
          .map((e) => SubstackPost.fromSnapshot(Map<String, dynamic>.from(e)))
          .where((e) => e.id.isNotEmpty && e.slug.isNotEmpty)
          .toList();
    } catch (_) {
      return const [];
    }
  }

  static String listToPrefs(List<SubstackPost> posts) =>
      jsonEncode(posts.map((e) => e.toJson()).toList());
}

class SubstackFeedSnapshot {
  final List<SubstackPost> posts;
  final bool canLoadMore;
  final int failedCount;

  const SubstackFeedSnapshot({
    this.posts = const [],
    this.canLoadMore = false,
    this.failedCount = 0,
  });

  SubstackFeedSnapshot copyWith({
    List<SubstackPost>? posts,
    bool? canLoadMore,
    int? failedCount,
  }) {
    return SubstackFeedSnapshot(
      posts: posts ?? this.posts,
      canLoadMore: canLoadMore ?? this.canLoadMore,
      failedCount: failedCount ?? this.failedCount,
    );
  }
}

/// Local filter on the merged Substack feed (inbox-style, no account needed).
enum SubstackFeedFilter { all, unread, free, podcast }

bool postMatchesSubstackFilter(
  SubstackPost post,
  SubstackFeedFilter filter,
  Set<String> readIds,
) {
  return switch (filter) {
    SubstackFeedFilter.all => true,
    SubstackFeedFilter.unread => !readIds.contains(post.id),
    SubstackFeedFilter.free => !post.isPaywalled,
    SubstackFeedFilter.podcast => post.isPodcast,
  };
}

/// One public Note from Substack's reader discovery feed.
class SubstackNote {
  final String id;
  final String body;
  final String? authorName;
  final String? authorHandle;
  final String? authorPhotoUrl;
  final DateTime? at;
  final int? reactionCount;
  final String? imageUrl;
  final String? url;
  final SubstackPublication? publication;

  const SubstackNote({
    required this.id,
    required this.body,
    this.authorName,
    this.authorHandle,
    this.authorPhotoUrl,
    this.at,
    this.reactionCount,
    this.imageUrl,
    this.url,
    this.publication,
  });

  factory SubstackNote.fromReaderItem(Map<String, dynamic> item) {
    final comment = item['comment'];
    final commentMap = comment is Map
        ? Map<String, dynamic>.from(comment)
        : const <String, dynamic>{};
    final pubRaw =
        item['publication'] ?? commentMap['user_primary_publication'];
    final pubMap = pubRaw is Map ? Map<String, dynamic>.from(pubRaw) : null;
    final handle = (commentMap['handle'] as String?)?.trim();
    final id = '${commentMap['id'] ?? item['entity_key'] ?? ''}';
    final attachments = commentMap['attachments'];
    String? imageUrl;
    if (attachments is List) {
      for (final a in attachments) {
        if (a is Map && a['type'] == 'image') {
          imageUrl = a['imageUrl'] as String?;
          if (imageUrl != null && imageUrl.isNotEmpty) break;
        }
      }
    }

    SubstackPublication? publication;
    if (pubMap != null) {
      final subdomain = (pubMap['subdomain'] as String?)?.trim() ?? '';
      final custom = (pubMap['custom_domain'] as String?)?.trim();
      if (subdomain.isNotEmpty || (custom != null && custom.isNotEmpty)) {
        final base = custom != null && custom.isNotEmpty
            ? 'https://$custom'
            : 'https://$subdomain.substack.com';
        publication = SubstackPublication(
          subdomain: subdomain.isNotEmpty
              ? subdomain
              : subdomainOf(Uri.parse(base)),
          baseUrl: base,
          name: pubMap['name'] as String? ?? subdomain,
          logoUrl: pubMap['logo_url'] as String?,
        );
      }
    }

    final notePath = handle != null && handle.isNotEmpty && id.isNotEmpty
        ? 'https://substack.com/@$handle/note/c-$id'
        : null;

    return SubstackNote(
      id: id.isEmpty ? (item['entity_key'] as String? ?? '') : id,
      body: (commentMap['body'] as String?)?.trim() ?? '',
      authorName: commentMap['name'] as String?,
      authorHandle: handle,
      authorPhotoUrl: commentMap['photo_url'] as String?,
      at: DateTime.tryParse(commentMap['date'] as String? ?? '')?.toLocal(),
      reactionCount: commentMap['reaction_count'] is num
          ? (commentMap['reaction_count'] as num).toInt()
          : null,
      imageUrl: imageUrl,
      url: notePath,
      publication: publication,
    );
  }
}

class SubstackNotesPage {
  final List<SubstackNote> notes;
  final String? nextCursor;

  const SubstackNotesPage({this.notes = const [], this.nextCursor});
}

/// A Substack leaderboard category (`/api/v1/categories`).
class SubstackCategory {
  final int id;
  final String name;
  final String slug;

  const SubstackCategory({
    required this.id,
    required this.name,
    required this.slug,
  });

  factory SubstackCategory.fromJson(Map<String, dynamic> json) {
    return SubstackCategory(
      id: json['id'] is num ? (json['id'] as num).toInt() : 0,
      name: (json['name'] as String?)?.trim() ?? '',
      slug: (json['slug'] as String?)?.trim() ?? '',
    );
  }
}

/// Map a Substack API publication object (search / category / reader) into our model.
SubstackPublication? publicationFromDiscoveryJson(Map<String, dynamic> json) {
  final subdomain = (json['subdomain'] as String?)?.trim() ?? '';
  final custom = (json['custom_domain'] as String?)?.trim();
  final baseRaw =
      (json['base_url'] as String?)?.trim() ??
      (json['hostname'] as String?)?.trim();

  if (subdomain.isEmpty &&
      (custom == null || custom.isEmpty) &&
      (baseRaw == null || baseRaw.isEmpty)) {
    return null;
  }

  String baseUrl;
  if (baseRaw != null && baseRaw.isNotEmpty) {
    final parsed = Uri.tryParse(
      baseRaw.contains('://') ? baseRaw : 'https://$baseRaw',
    );
    baseUrl = parsed != null && parsed.host.isNotEmpty
        ? parsed.origin
        : 'https://$baseRaw';
  } else if (custom != null && custom.isNotEmpty) {
    baseUrl = Uri(scheme: 'https', host: custom).origin;
  } else {
    baseUrl = 'https://$subdomain.substack.com';
  }

  final name = (json['name'] as String?)?.trim();
  return SubstackPublication(
    subdomain: subdomain.isNotEmpty
        ? subdomain
        : subdomainOf(Uri.parse(baseUrl)),
    baseUrl: baseUrl,
    name: (name != null && name.isNotEmpty)
        ? name
        : (subdomain.isNotEmpty ? subdomain : subdomainOf(Uri.parse(baseUrl))),
    description:
        (json['hero_text'] as String?)?.trim() ??
        (json['copyright'] as String?)?.trim(),
    logoUrl:
        (json['logo_url'] as String?) ?? (json['logo_url_wide'] as String?),
  );
}

/// Resolve a user-entered Substack handle or URL into a base publication URL.
Uri? resolveSubstackBase(String input) {
  final trimmed = input.trim();
  if (trimmed.isEmpty) return null;

  var raw = trimmed;
  if (!raw.contains('://')) {
    if (raw.contains('.')) {
      raw = 'https://$raw';
    } else {
      raw = 'https://$raw.substack.com';
    }
  }

  final uri = Uri.tryParse(raw);
  if (uri == null || uri.host.isEmpty) return null;
  return Uri(scheme: 'https', host: uri.host);
}

/// Parse a Substack post URL into publication base + slug.
({Uri base, String slug})? resolveSubstackPostRef(String input) {
  final trimmed = input.trim();
  if (trimmed.isEmpty) return null;

  var raw = trimmed;
  if (!raw.contains('://')) raw = 'https://$raw';
  final uri = Uri.tryParse(raw);
  if (uri == null || uri.host.isEmpty) return null;

  final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
  if (segments.length < 2 || segments.first != 'p') return null;
  final slug = segments[1];
  if (slug.isEmpty) return null;
  return (base: Uri(scheme: 'https', host: uri.host), slug: slug);
}

String subdomainOf(Uri base) {
  final host = base.host.toLowerCase();
  if (host.endsWith('.substack.com')) {
    return host.substring(0, host.length - '.substack.com'.length);
  }
  return host.split('.').first;
}

List<String> readIdsFromPrefs(String? raw) {
  if (raw == null || raw.isEmpty) return const [];
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! List) return const [];
    return decoded.whereType<String>().where((e) => e.isNotEmpty).toList();
  } catch (_) {
    return const [];
  }
}

String readIdsToPrefs(List<String> ids) => jsonEncode(ids);

/// One comment under a post, flattened for a list with its nesting [depth].
class SubstackComment {
  final String id;
  final String? author;
  final String body;
  final DateTime? at;
  final int depth;

  const SubstackComment({
    required this.id,
    required this.body,
    this.author,
    this.at,
    this.depth = 0,
  });
}

/// Reads the comments endpoint's tree into reading order.
///
/// The shape is a list of comments each carrying `children`; anything that no
/// longer fits is skipped rather than thrown, in the same spirit as every
/// other reverse-engineered payload here.
List<SubstackComment> flattenSubstackComments(
  Object? json, {
  int maxDepth = 8,
}) {
  final out = <SubstackComment>[];

  void walk(Object? nodes, int depth) {
    if (nodes is! List) {
      return;
    }
    for (final node in nodes) {
      if (node is! Map) {
        continue;
      }
      final body = (node['body'] as String?)?.trim();
      final id = '${node['id'] ?? ''}';
      // A deleted comment arrives with no body; its children survive and are
      // still worth showing where they sat.
      if (id.isNotEmpty && body != null && body.isNotEmpty) {
        out.add(
          SubstackComment(
            id: id,
            body: body,
            author: node['name'] as String?,
            at: DateTime.tryParse(node['date'] as String? ?? '')?.toLocal(),
            depth: depth,
          ),
        );
      }
      if (depth < maxDepth) {
        walk(
          node['children'],
          id.isEmpty || body == null || body.isEmpty ? depth : depth + 1,
        );
      }
    }
  }

  walk(json is Map ? json['comments'] : json, 0);
  return out;
}
