import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:sqflite/sqflite.dart';

/// Device-local EH favorites.
class EhFavoritesStore extends Store<List<EhGallery>> {
  EhFavoritesStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<EhGallery>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableEhFavorite,
      orderBy: 'favorited_at DESC',
    );
    return rows.map(EhFavorite.fromMap).map((e) => e.gallery).toList();
  }

  bool contains(int gid) => state.any((g) => g.gid == gid);

  Future<void> toggle(EhGallery gallery) async {
    if (contains(gallery.gid)) {
      await remove(gallery.gid);
    } else {
      await add(gallery);
    }
  }

  Future<void> add(EhGallery gallery) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.insert(
        tableEhFavorite,
        EhFavorite(gallery: gallery, favoritedAt: DateTime.now()).toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      return _read();
    });
  }

  Future<void> remove(int gid) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.delete(
        tableEhFavorite,
        where: 'gid = ?',
        whereArgs: [gid],
      );
      return _read();
    });
  }
}

/// Paginated gallery list for Popular / Front / Search.
class EhFeedStore extends Store<List<EhGallery>> {
  final Future<EhGalleryPage> Function({String? pageUrl}) loader;

  String? _nextUrl;
  var _hasMore = true;
  var _loadingMore = false;

  EhFeedStore(this.loader) : super(const []);

  bool get hasMore => _hasMore;
  bool get loadingMore => _loadingMore;

  Future<void> refresh() async {
    _nextUrl = null;
    _hasMore = true;
    await execute(() async {
      final page = await loader(pageUrl: null);
      _nextUrl = page.nextUrl;
      _hasMore = page.hasMore;
      return page.galleries;
    });
  }

  Future<void> loadMore() async {
    if (!_hasMore || _loadingMore || _nextUrl == null) return;
    _loadingMore = true;
    try {
      final page = await loader(pageUrl: _nextUrl);
      _nextUrl = page.nextUrl;
      _hasMore = page.hasMore;
      if (page.galleries.isNotEmpty) {
        update([...state, ...page.galleries]);
      }
    } catch (_) {
      // Keep what we have.
    } finally {
      _loadingMore = false;
    }
  }
}

/// Favorite row helper.
class EhFavorite with ToMappable {
  final EhGallery gallery;
  final DateTime favoritedAt;

  EhFavorite({required this.gallery, required this.favoritedAt});

  factory EhFavorite.fromMap(Map<String, Object?> map) {
    return EhFavorite(
      gallery: EhGallery(
        gid: map['gid'] as int,
        token: map['token'] as String,
        title: map['title'] as String,
        titleJpn: map['title_jpn'] as String?,
        category: EhCategory.tryParse(map['category'] as String?),
        thumbUrl: map['thumb_url'] as String?,
        uploader: map['uploader'] as String?,
        pageCount: map['page_count'] as int?,
        rating: (map['rating'] as num?)?.toDouble(),
        tags: const [],
        postedAt: map['posted_at'] == null
            ? null
            : DateTime.tryParse(map['posted_at'] as String),
      ),
      favoritedAt: map['favorited_at'] == null
          ? DateTime.now()
          : DateTime.parse(map['favorited_at'] as String),
    );
  }

  @override
  Map<String, dynamic> toMap() => {
    'gid': gallery.gid,
    'token': gallery.token,
    'title': gallery.title,
    'title_jpn': gallery.titleJpn,
    'category': gallery.category?.label,
    'thumb_url': gallery.thumbUrl,
    'uploader': gallery.uploader,
    'page_count': gallery.pageCount,
    'rating': gallery.rating,
    'posted_at': gallery.postedAt?.toIso8601String(),
    'favorited_at': favoritedAt.toIso8601String(),
  };
}
