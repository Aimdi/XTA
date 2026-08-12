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
      case BooruEngine.gelbooruV2:
        return tryParse(value);
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

  bool get isVideo {
    final ext = (fileExt ?? '').toLowerCase();
    if (ext == 'mp4' || ext == 'webm' || ext == 'mkv') return true;
    final url = (fileUrl ?? sampleUrl ?? '').toLowerCase();
    return url.endsWith('.mp4') || url.endsWith('.webm');
  }

  String get tagLine => tags.join(' ');

  /// Canonical page on the host for this post, when the engine has one.
  String? get hostPageUrl {
    final base = Uri.tryParse(host);
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

class BooruTagSuggestion {
  final String name;
  final int? postCount;

  const BooruTagSuggestion({required this.name, this.postCount});
}
