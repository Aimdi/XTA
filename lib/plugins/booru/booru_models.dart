/// Shared booru post model and rating helpers.
library;

import 'package:xta/plugins/booru/booru_engines.dart';

/// Normalised content rating across engines.
///
/// Danbooru uses g/s/q/e (s = sensitive). Moebooru and e621 use s/q/e where
/// **s = safe**. Gelbooru uses general/sensitive/questionable/explicit (and
/// older safe/questionable/explicit).
enum BooruRating {
  general,
  sensitive,
  questionable,
  explicit;

  /// Wire letter used in preference storage (Danbooru-shaped).
  String get code => switch (this) {
    BooruRating.general => 'g',
    BooruRating.sensitive => 's',
    BooruRating.questionable => 'q',
    BooruRating.explicit => 'e',
  };

  /// Preference / settings parse — never engine-specific (`s` = sensitive).
  static BooruRating? tryParse(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    switch (raw.trim().toLowerCase()) {
      case 'g':
      case 'general':
      case 'safe':
        return BooruRating.general;
      case 's':
      case 'sensitive':
        return BooruRating.sensitive;
      case 'q':
      case 'questionable':
        return BooruRating.questionable;
      case 'e':
      case 'explicit':
        return BooruRating.explicit;
      default:
        return null;
    }
  }

  /// Parse a rating letter/label from an API response for [engine].
  static BooruRating? parseWire(String? raw, BooruEngine engine) {
    if (raw == null || raw.isEmpty) return null;
    final value = raw.trim().toLowerCase();
    switch (engine) {
      case BooruEngine.moebooru:
      case BooruEngine.e621:
        // Historical Booru: s = safe, no separate "sensitive".
        return switch (value) {
          's' || 'safe' || 'g' || 'general' => BooruRating.general,
          'q' || 'questionable' => BooruRating.questionable,
          'e' || 'explicit' => BooruRating.explicit,
          _ => null,
        };
      case BooruEngine.danbooru:
        return tryParse(value);
      case BooruEngine.gelbooruV2:
        // Classic Gelbooru / Rule34 / Xbooru: s = safe. Newer Gelbooru also
        // sends the word "sensitive".
        return switch (value) {
          's' || 'safe' || 'g' || 'general' => BooruRating.general,
          'sensitive' => BooruRating.sensitive,
          'q' || 'questionable' => BooruRating.questionable,
          'e' || 'explicit' => BooruRating.explicit,
          _ => tryParse(value),
        };
    }
  }

  bool exceeds(BooruRating max) => index > max.index;
}

class BooruPost {
  final String id;
  final String host;
  final String engine;
  final List<String> tags;
  final BooruRating? rating;
  final int? score;
  final int width;
  final int height;
  final String? previewUrl;
  final String? sampleUrl;
  final String? fileUrl;
  final String? fileExt;
  final String? source;
  final DateTime? createdAt;

  /// Kind of each tag, when the post itself says (Danbooru, e621). Other
  /// engines need a lookup; see `BooruClient.tagCategories`.
  final Map<String, BooruTagCategory> tagCategories;
  final int? fileSize;
  final String? md5;
  final int? favCount;
  final int? upScore;

  /// Down votes as a positive count, whatever sign the host sends.
  final int? downScore;
  final String? parentId;
  final bool hasChildren;
  final String? uploader;

  const BooruPost({
    required this.id,
    required this.host,
    required this.engine,
    required this.tags,
    required this.rating,
    required this.score,
    required this.width,
    required this.height,
    required this.previewUrl,
    required this.sampleUrl,
    required this.fileUrl,
    required this.fileExt,
    required this.source,
    required this.createdAt,
    this.tagCategories = const {},
    this.fileSize,
    this.md5,
    this.favCount,
    this.upScore,
    this.downScore,
    this.parentId,
    this.hasChildren = false,
    this.uploader,
  });

  String get thumbnailUrl => (previewUrl != null && previewUrl!.isNotEmpty)
      ? previewUrl!
      : (sampleUrl ?? fileUrl ?? '');

  String get displayUrl => (sampleUrl != null && sampleUrl!.isNotEmpty)
      ? sampleUrl!
      : (fileUrl ?? previewUrl ?? '');

  /// Sample/large for catalog tiles. Preview (~150px) looks like 240p when
  /// stretched across a two-column phone tile. Video posters stay on preview
  /// because sample can be a webm.
  String get catalogUrl {
    if (isVideo) {
      return thumbnailUrl;
    }
    if (sampleUrl != null && sampleUrl!.isNotEmpty) {
      return sampleUrl!;
    }
    if (fileUrl != null && fileUrl!.isNotEmpty) {
      return fileUrl!;
    }
    return thumbnailUrl;
  }

  double get aspectRatio {
    if (width <= 0 || height <= 0) return 1;
    return width / height;
  }

  bool get isVideo =>
      _videoExtensions.contains((fileExt ?? '').toLowerCase()) ||
      videoUrl != null;

  /// A file the player can open: the original, else a sample. Danbooru keeps
  /// animations as a zip of frames and samples them as webm.
  String? get videoUrl => [fileUrl, sampleUrl].nonNulls
      .where((url) => _videoExtensions.contains(_extensionOf(url)))
      .firstOrNull;

  static const _videoExtensions = {'mp4', 'webm', 'mkv'};

  static String _extensionOf(String url) {
    final path = Uri.tryParse(url)?.path ?? url;
    final dot = path.lastIndexOf('.');
    return dot < 0 ? '' : path.substring(dot + 1).toLowerCase();
  }

  String get tagLine => tags.join(' ');

  /// Identity across hosts: ids are only unique on one.
  String get key => '$host:$id';

  /// The original file, else the largest version the host gave.
  String get originalUrl =>
      (fileUrl != null && fileUrl!.isNotEmpty) ? fileUrl! : displayUrl;

  /// Whether the post belongs to a parent/child set on the host.
  bool get hasFamily => parentId != null || hasChildren;

  /// Canonical page on the host for this post, when the engine has one.
  String? get hostPageUrl {
    final base = Uri.tryParse(booruPageHost(host));
    if (base == null) return null;
    final engineKind = BooruEngine.tryParse(engine);
    switch (engineKind) {
      case BooruEngine.danbooru:
      case BooruEngine.e621:
        return base.replace(path: '${_trim(base.path)}/posts/$id').toString();
      case BooruEngine.moebooru:
        return base
            .replace(path: '${_trim(base.path)}/post/show/$id')
            .toString();
      case BooruEngine.gelbooruV2:
        return base
            .replace(
              path: '${_trim(base.path)}/index.php',
              queryParameters: {'page': 'post', 's': 'view', 'id': id},
            )
            .toString();
      case null:
        return null;
    }
  }

  static String _trim(String path) {
    if (path.isEmpty || path == '/') return '';
    return path.replaceAll(RegExp(r'/+$'), '');
  }
}

class BooruPostPage {
  final List<BooruPost> posts;
  final int page;
  final bool hasMore;

  const BooruPostPage({
    required this.posts,
    required this.page,
    required this.hasMore,
  });
}

/// Tag kinds the hosts colour differently. Each engine numbers them its own
/// way; [BooruTagCategory.fromWire] maps the numbers.
enum BooruTagCategory {
  general,
  artist,
  copyright,
  character,
  species,
  meta;

  static BooruTagCategory? fromWire(int code, BooruEngine engine) =>
      switch ((engine, code)) {
        (_, 0) => general,
        (_, 1) => artist,
        (_, 3) => copyright,
        (_, 4) => character,
        (BooruEngine.e621, 5) => species,
        (BooruEngine.e621, 7) => meta,
        (BooruEngine.moebooru, 5) => artist,
        (BooruEngine.moebooru, 6) => meta,
        (_, 5) => meta,
        _ => null,
      };

  /// Gelbooru forks and Moebooru's tag map send the kind as a word. A
  /// Moebooru circle is a group of artists; faults are notes about the file.
  static BooruTagCategory? named(String? name) =>
      switch ((name ?? '').toLowerCase()) {
        'general' || 'tag' => general,
        'artist' || 'circle' => artist,
        'copyright' => copyright,
        'character' => character,
        'species' => species,
        'metadata' || 'meta' || 'faults' || 'lore' => meta,
        _ => null,
      };
}

class BooruTagSuggestion {
  final String name;
  final int? postCount;
  final BooruTagCategory? category;

  const BooruTagSuggestion({required this.name, this.postCount, this.category});
}

class BooruComment {
  final String id;
  final String? author;
  final String body;
  final DateTime? createdAt;
  final int? score;

  const BooruComment({
    required this.id,
    required this.author,
    required this.body,
    required this.createdAt,
    required this.score,
  });
}
