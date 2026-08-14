/// EH gallery models and category helpers.
library;

/// EH gallery category. Bit values match the site's `f_cats` exclude mask.
enum EhCategory {
  misc(1, 'Misc'),
  doujinshi(2, 'Doujinshi'),
  manga(4, 'Manga'),
  artistCg(8, 'Artist CG'),
  gameCg(16, 'Game CG'),
  imageSet(32, 'Image Set'),
  cosplay(64, 'Cosplay'),
  asianPorn(128, 'Asian Porn'),
  nonH(256, 'Non-H'),
  western(512, 'Western');

  final int bit;
  final String label;
  const EhCategory(this.bit, this.label);

  static EhCategory? tryParse(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    final key = raw.trim().toLowerCase().replaceAll(RegExp(r'[\s_-]+'), '');
    return switch (key) {
      'misc' => EhCategory.misc,
      'doujinshi' => EhCategory.doujinshi,
      'manga' => EhCategory.manga,
      'artistcg' || 'artistcgsets' => EhCategory.artistCg,
      'gamecg' => EhCategory.gameCg,
      'imageset' || 'imagesets' => EhCategory.imageSet,
      'cosplay' => EhCategory.cosplay,
      'asianporn' => EhCategory.asianPorn,
      'nonh' || 'non-h' => EhCategory.nonH,
      'western' => EhCategory.western,
      _ => null,
    };
  }

  /// `f_cats` is the bitmask of categories to *exclude*.
  static int excludeMask(Set<EhCategory> included) {
    if (included.isEmpty || included.length == EhCategory.values.length) {
      return 0;
    }
    var mask = 0;
    for (final cat in EhCategory.values) {
      if (!included.contains(cat)) mask |= cat.bit;
    }
    return mask;
  }
}

class EhGallery {
  final int gid;
  final String token;
  final String title;
  final String? titleJpn;
  final EhCategory? category;
  final String? thumbUrl;
  final String? uploader;
  final DateTime? postedAt;
  final int? pageCount;
  final double? rating;
  final List<String> tags;

  const EhGallery({
    required this.gid,
    required this.token,
    required this.title,
    this.titleJpn,
    this.category,
    this.thumbUrl,
    this.uploader,
    this.postedAt,
    this.pageCount,
    this.rating,
    this.tags = const [],
  });

  String get id => '$gid/$token';

  String get displayTitle => titleFor(preferJapanese: true);

  String titleFor({required bool preferJapanese}) {
    if (preferJapanese) {
      final jpn = titleJpn?.trim();
      if (jpn != null && jpn.isNotEmpty) return jpn;
    }
    return title;
  }

  Uri galleryUri(String host) => Uri.parse('$host/g/$gid/$token/');

  Uri pageUri(String host, {required String pageToken, required int page}) =>
      Uri.parse('$host/s/$pageToken/$gid-$page');
}

class EhGalleryPage {
  final List<EhGallery> galleries;
  final String? nextUrl;
  final bool hasMore;

  const EhGalleryPage({
    required this.galleries,
    this.nextUrl,
    required this.hasMore,
  });
}

class EhGalleryDetail extends EhGallery {
  final List<EhPreview> previews;
  final List<EhComment> comments;
  final int? fileSizeBytes;
  final int previewSheetIndex;
  final int previewSheetCount;

  const EhGalleryDetail({
    required super.gid,
    required super.token,
    required super.title,
    super.titleJpn,
    super.category,
    super.thumbUrl,
    super.uploader,
    super.postedAt,
    super.pageCount,
    super.rating,
    super.tags = const [],
    this.previews = const [],
    this.comments = const [],
    this.fileSizeBytes,
    this.previewSheetIndex = 0,
    this.previewSheetCount = 1,
  });
}

class EhComment {
  final String author;
  final String posted;
  final String body;
  final String? score;
  final bool uploader;

  const EhComment({
    required this.author,
    required this.posted,
    required this.body,
    this.score,
    this.uploader = false,
  });
}

/// Site toplist windows (`toplist.php?tl=`).
enum EhToplistPeriod {
  yesterday(11),
  month(12),
  year(13),
  allTime(15);

  final int tl;
  const EhToplistPeriod(this.tl);
}

/// Language filter appended as `language:{tag}` on search.
enum EhSearchLanguage {
  any(''),
  english('english'),
  japanese('japanese'),
  chinese('chinese'),
  korean('korean'),
  spanish('spanish'),
  french('french');

  final String tag;
  const EhSearchLanguage(this.tag);
}

class EhPreview {
  final String pageToken;
  final int page;
  final String? thumbUrl;
  final double? thumbOffsetX;

  const EhPreview({
    required this.pageToken,
    required this.page,
    this.thumbUrl,
    this.thumbOffsetX,
  });
}

class EhImagePage {
  final String imageUrl;
  final String? originalImageUrl;
  final String? reloadKey;
  final String? nextPageUrl;
  final String? prevPageUrl;
  final int page;
  final int? pageCount;

  const EhImagePage({
    required this.imageUrl,
    this.originalImageUrl,
    this.reloadKey,
    required this.page,
    this.nextPageUrl,
    this.prevPageUrl,
    this.pageCount,
  });

  /// Original when the page offers `fullimg`, else the resampled `#img`.
  ///
  /// Guests must not use [originalImageUrl]: `fullimg` 302s to login.
  String displayUrl({required bool signedIn}) {
    if (signedIn) {
      final original = originalImageUrl?.trim();
      if (original != null && original.isNotEmpty) return original;
    }
    return imageUrl;
  }
}
