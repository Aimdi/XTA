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

  /// Custom-domain follows can land on `www`; never show that as the title.
  String get displayName {
    final trimmed = name.trim();
    if (trimmed.isNotEmpty && trimmed.toLowerCase() != 'www') return trimmed;
    final sub = subdomain.trim();
    if (sub.isNotEmpty && sub.toLowerCase() != 'www') return sub;
    final parsed = Uri.tryParse(baseUrl);
    if (parsed != null) {
      final fromHost = subdomainOf(parsed);
      if (fromHost.isNotEmpty && fromHost != 'www') return fromHost;
    }
    return trimmed.isNotEmpty ? trimmed : sub;
  }

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

/// Primary publication on a public profile (`/api/v1/user/{handle}/public_profile`).
SubstackPublication? publicationFromProfileJson(Map<String, dynamic> json) {
  final primary = json['primaryPublication'];
  if (primary is Map) {
    final publication = publicationFromDiscoveryJson(
      Map<String, dynamic>.from(primary),
    );
    if (publication != null) return publication;
  }

  final users = json['publicationUsers'];
  if (users is List) {
    for (final user in users.whereType<Map>()) {
      final nested = user['publication'];
      if (nested is! Map) continue;
      final publication = publicationFromDiscoveryJson(
        Map<String, dynamic>.from(nested),
      );
      if (publication != null) return publication;
    }
  }
  return null;
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

/// A publication the author recommends, or one search treated as similar.
class SubstackRecommendation {
  final SubstackPublication publication;
  final String? blurb;

  const SubstackRecommendation({required this.publication, this.blurb});
}

/// Official `/recommendations` page hydrates `window._preloads`.
String? substackPreloadsJson(String html) {
  final match = RegExp(
    r'window\._preloads\s*=\s*JSON\.parse\("((?:\\.|[^"\\])*)"\)',
  ).firstMatch(html);
  if (match == null) {
    return null;
  }
  try {
    final decoded = jsonDecode('"${match.group(1)}"');
    return decoded is String && decoded.isNotEmpty ? decoded : null;
  } catch (_) {
    return null;
  }
}

List<SubstackRecommendation> parseSubstackRecommendationsHtml(String html) {
  final raw = substackPreloadsJson(html);
  if (raw == null) {
    return const [];
  }
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! Map) {
      return const [];
    }
    return parseSubstackRecommendationsJson(decoded['recommendations']);
  } catch (_) {
    return const [];
  }
}

List<SubstackRecommendation> parseSubstackRecommendationsJson(Object? raw) {
  if (raw is! List) {
    return const [];
  }
  final out = <SubstackRecommendation>[];
  final seen = <String>{};
  for (final item in raw.whereType<Map>()) {
    final map = Map<String, dynamic>.from(item);
    final pubRaw =
        map['recommendedPublication'] ?? map['recommended_publication'];
    if (pubRaw is! Map) {
      continue;
    }
    final publication = publicationFromDiscoveryJson(
      Map<String, dynamic>.from(pubRaw),
    );
    if (publication == null || publication.subdomain.isEmpty) {
      continue;
    }
    if (seen.contains(publication.id)) {
      continue;
    }
    seen.add(publication.id);
    final blurb = (map['description'] as String?)?.trim();
    out.add(
      SubstackRecommendation(
        publication: publication,
        blurb: blurb == null || blurb.isEmpty ? null : blurb,
      ),
    );
  }
  return out;
}

/// Author recs first, then search hits, never the seed itself.
List<SubstackRecommendation> mergeSubstackSimilar({
  required SubstackPublication seed,
  List<SubstackRecommendation> recommended = const [],
  List<SubstackPublication> searched = const [],
  int limit = 12,
}) {
  final seen = {seed.id};
  final out = <SubstackRecommendation>[];

  void add(SubstackRecommendation rec) {
    if (out.length >= limit) {
      return;
    }
    if (seen.contains(rec.publication.id)) {
      return;
    }
    seen.add(rec.publication.id);
    out.add(rec);
  }

  for (final rec in recommended) {
    add(rec);
  }
  for (final publication in searched) {
    add(SubstackRecommendation(publication: publication));
  }
  return out;
}

/// Hosts that are Substack itself, not a publication.
const substackServiceHosts = {
  'substack.com',
  'www.substack.com',
  'open.substack.com',
};

/// Sites that look like a newsletter but are never Substack-hosted.
bool isObviousNonSubstackHost(String host) {
  final h = host.toLowerCase();
  const exact = {
    'medium.com',
    'www.medium.com',
    'x.com',
    'www.x.com',
    'twitter.com',
    'www.twitter.com',
    'ghost.org',
    'www.ghost.org',
    'beehiiv.com',
    'www.beehiiv.com',
    'buttondown.com',
    'www.buttondown.com',
  };
  if (exact.contains(h)) return true;
  return h.endsWith('.medium.com') ||
      h.endsWith('.beehiiv.com') ||
      h.endsWith('.ghost.io') ||
      h.endsWith('.buttondown.com');
}

bool isSubstackPublicationHost(String host) {
  final h = host.toLowerCase();
  return h.endsWith('.substack.com') && !substackServiceHosts.contains(h);
}

bool isSubstackServiceHost(String host) =>
    substackServiceHosts.contains(host.toLowerCase());

/// Hosts that differ only by a `www.` prefix count as the same publication.
bool sameSubstackHost(String a, String b) {
  String norm(String host) {
    final h = host.toLowerCase();
    return h.startsWith('www.') ? h.substring(4) : h;
  }

  return norm(a) == norm(b);
}

/// First label of a custom domain (`www.platformer.news` → `platformer`).
String? registrableLabel(String host) {
  var h = host.toLowerCase();
  if (h.startsWith('www.')) h = h.substring(4);
  final parts = h.split('.').where((e) => e.isNotEmpty).toList();
  if (parts.length < 2) return null;
  return parts.first;
}

/// A leftover www/host label, never a real publication title.
bool publicationNameLooksGeneric(String name) {
  final n = name.trim().toLowerCase();
  return n.isEmpty || n == 'www' || n == 'www2';
}

String? _metaContent(String html, String property) {
  final named = RegExp(
    'property=["\']$property["\'][^>]*content=["\']([^"\']+)["\']',
    caseSensitive: false,
  ).firstMatch(html);
  if (named != null) return named.group(1)?.trim();
  final reversed = RegExp(
    'content=["\']([^"\']+)["\'][^>]*property=["\']$property["\']',
    caseSensitive: false,
  ).firstMatch(html);
  return reversed?.group(1)?.trim();
}

String? _htmlTitle(String html) {
  return RegExp(
    r'<title[^>]*>([^<]+)</title>',
    caseSensitive: false,
  ).firstMatch(html)?.group(1)?.trim();
}

String? _titleFromHtmlBits(String? siteName, String? title) {
  if (siteName != null &&
      siteName.isNotEmpty &&
      siteName.toLowerCase() != 'substack') {
    return siteName;
  }
  final raw = title?.trim();
  if (raw == null || raw.isEmpty) return null;
  final parts = raw
      .split('|')
      .map((e) => e.trim())
      .where((e) => e.isNotEmpty)
      .where((e) {
        final lower = e.toLowerCase();
        return lower != 'home' && lower != 'substack';
      })
      .toList();
  if (parts.isNotEmpty) return parts.first;
  return publicationNameLooksGeneric(raw) ? null : raw;
}

/// Homepage OG tags when a custom domain no longer serves Substack JSON.
SubstackPublication? publicationFromHomepageHtml(String html, Uri base) {
  final name = _titleFromHtmlBits(
    _metaContent(html, 'og:site_name'),
    _metaContent(html, 'og:title') ?? _htmlTitle(html),
  );
  if (name == null || publicationNameLooksGeneric(name)) return null;
  final image = _metaContent(html, 'og:image');
  return SubstackPublication(
    subdomain: subdomainOf(base),
    baseUrl: base.origin,
    name: name,
    logoUrl: image == null || image.isEmpty ? null : image,
  );
}

/// `@casey` / `substack.com/@casey` — a profile, not a publication host.
String? resolveSubstackProfileHandle(String input) {
  final trimmed = input.trim();
  if (trimmed.isEmpty) return null;

  if (trimmed.startsWith('@') &&
      !trimmed.substring(1).contains('/') &&
      !trimmed.substring(1).contains('.')) {
    final handle = trimmed.substring(1).trim();
    return _isHandle(handle) ? handle : null;
  }

  final uri = _parseUserUri(trimmed);
  if (uri == null) return null;
  if (!isSubstackServiceHost(uri.host) ||
      uri.host.toLowerCase() == 'open.substack.com') {
    return null;
  }
  final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
  if (segments.isEmpty || !segments.first.startsWith('@')) return null;
  final handle = segments.first.substring(1);
  return _isHandle(handle) ? handle : null;
}

/// Resolve a user-entered Substack handle or URL into a base publication URL.
///
/// Share links (`open.substack.com/pub/foo`) and profile handles (`@foo`,
/// `substack.com/@foo`) become `foo.substack.com`. Service hosts with no
/// publication in the path are rejected. Custom domains are returned as-is so
/// the client can probe whether they are still Substack-hosted.
Uri? resolveSubstackBase(String input) {
  final trimmed = input.trim();
  if (trimmed.isEmpty) return null;

  final handle = resolveSubstackProfileHandle(trimmed);
  if (handle != null) {
    return Uri(scheme: 'https', host: '$handle.substack.com');
  }

  final uri = _parseUserUri(trimmed);
  if (uri == null || uri.host.isEmpty) return null;
  if (isObviousNonSubstackHost(uri.host)) return null;

  final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
  final host = uri.host.toLowerCase();

  if (host == 'open.substack.com') {
    if (segments.length >= 2 &&
        segments[0] == 'pub' &&
        _isHandle(segments[1])) {
      return Uri(scheme: 'https', host: '${segments[1]}.substack.com');
    }
    return null;
  }

  if (isSubstackServiceHost(host)) {
    return null;
  }

  return Uri(scheme: 'https', host: host);
}

/// Parse a Substack post URL into publication base + slug.
({Uri base, String slug})? resolveSubstackPostRef(String input) {
  final trimmed = input.trim();
  if (trimmed.isEmpty) return null;

  final uri = _parseUserUri(trimmed);
  if (uri == null || uri.host.isEmpty) return null;

  final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
  final host = uri.host.toLowerCase();

  if (host == 'open.substack.com') {
    if (segments.length >= 4 &&
        segments[0] == 'pub' &&
        segments[2] == 'p' &&
        _isHandle(segments[1]) &&
        _isPostSlug(segments[3])) {
      return (
        base: Uri(scheme: 'https', host: '${segments[1]}.substack.com'),
        slug: segments[3],
      );
    }
    return null;
  }

  if (isSubstackServiceHost(host) || isObviousNonSubstackHost(host)) {
    return null;
  }

  if (segments.length >= 2 &&
      segments.first == 'p' &&
      _isPostSlug(segments[1])) {
    return (base: Uri(scheme: 'https', host: host), slug: segments[1]);
  }
  return null;
}

/// Origins to try when fetching a followed publication.
///
/// The saved host first (a living custom domain still serves JSON there), then
/// the apex/`www` twin, then `{label}.substack.com` so a leftover custom
/// domain — or a follow saved as `www` — still reaches the archive on Substack.
List<Uri> publicationFetchBases(SubstackPublication publication) {
  final parsed = Uri.tryParse(publication.baseUrl);
  final hinted = parsed != null && parsed.host.isNotEmpty
      ? parsed
      : (publication.subdomain.isEmpty
            ? null
            : Uri(
                scheme: 'https',
                host: '${publication.subdomain}.substack.com',
              ));
  if (hinted == null) return const [];
  return substackHostCandidates(hinted, subdomainHint: publication.subdomain);
}

/// Hosts to probe for a pasted or followed publication URL.
List<Uri> substackHostCandidates(Uri base, {String? subdomainHint}) {
  final out = <Uri>[];

  void add(String host) {
    final h = host.toLowerCase();
    if (h.isEmpty) return;
    if (isSubstackServiceHost(h) || isObviousNonSubstackHost(h)) return;
    if (out.any((e) => e.host == h)) return;
    out.add(Uri(scheme: 'https', host: h));
  }

  add(base.host);
  final bare = base.host.toLowerCase().startsWith('www.')
      ? base.host.toLowerCase().substring(4)
      : base.host.toLowerCase();
  add(bare);
  add('www.$bare');

  void addSubstackTwin(String? slug) {
    if (slug == null || slug.isEmpty) return;
    if (!_isHandle(slug) || slug.toLowerCase() == 'www') return;
    add('$slug.substack.com');
  }

  addSubstackTwin(subdomainHint);
  addSubstackTwin(registrableLabel(base.host));
  return out;
}

Uri? _parseUserUri(String input) {
  var raw = input.trim();
  if (raw.isEmpty) return null;
  if (!raw.contains('://')) {
    if (raw.startsWith('@')) return null;
    raw = raw.contains('.') ? 'https://$raw' : 'https://$raw.substack.com';
  }
  final uri = Uri.tryParse(raw);
  if (uri == null || uri.host.isEmpty) return null;
  if (uri.scheme != 'http' && uri.scheme != 'https') return null;
  return uri;
}

bool _isHandle(String value) =>
    RegExp(r'^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$').hasMatch(value);

bool _isPostSlug(String value) =>
    value.isNotEmpty && !value.startsWith('@') && value != 'p';

String subdomainOf(Uri base) {
  final host = base.host.toLowerCase();
  if (host.endsWith('.substack.com')) {
    var sub = host.substring(0, host.length - '.substack.com'.length);
    if (sub.startsWith('www.')) sub = sub.substring(4);
    return sub;
  }
  return registrableLabel(host) ?? host.split('.').first;
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
