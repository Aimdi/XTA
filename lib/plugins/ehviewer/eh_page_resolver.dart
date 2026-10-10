/// Turns a gallery page number into its image the way the site links them:
/// page number → page token (from a preview sheet) → image page.
library;

import 'dart:async';
import 'dart:math';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_parse.dart';

sealed class EhPageSlot {
  const EhPageSlot();
}

/// Fetched again after a reload or a retry. A page never asked for has no slot.
final class EhPageLoading extends EhPageSlot {
  const EhPageLoading();
}

final class EhPageReady extends EhPageSlot {
  final EhImagePage image;

  const EhPageReady(this.image);
}

final class EhPageFailed extends EhPageSlot {
  final Object error;

  const EhPageFailed(this.error);
}

/// Tiles per preview sheet with the large thumbnails the client asks for.
const ehDefaultSheetSize = 20;

/// Pages per preview sheet, read off sheet [sheet] holding [previews]: a full
/// first sheet says it outright, a later one by the page it starts at.
int? ehSheetSizeFrom({required int sheet, required List<EhPreview> previews, required int total}) {
  if (previews.isEmpty) return null;
  final first = previews.map((preview) => preview.page).reduce(min);
  if (first == 1) return previews.length < total ? previews.length : null;
  final before = first - 1;
  return sheet > 0 && before % sheet == 0 ? before ~/ sheet : null;
}

/// The unbroken run of pages from page 1: the sheets a gallery screen loaded first.
List<EhPreview> ehLeadingRun(Iterable<EhPreview> previews) {
  final byPage = {for (final preview in previews) preview.page: preview};
  return [for (var page = 1; byPage.containsKey(page); page++) byPage[page]!];
}

class EhPageResolver extends Store<Map<int, EhPageSlot>> {
  final EhClient client;
  final EhGallery gallery;
  final int total;
  final Map<int, EhPreview> _previews;
  final _pending = <int, Future<EhImagePage?>>{};
  final _sheets = <int, Future<void>>{};
  int? _sheetSize;
  var _closed = false;

  EhPageResolver({
    required this.client,
    required this.gallery,
    required this.total,
    List<EhPreview> previews = const [],
  }) : _previews = {for (final preview in previews) preview.page: preview},
       _sheetSize = ehSheetSizeFrom(sheet: 0, previews: ehLeadingRun(previews), total: total),
       super(const {});

  /// The page token, once a preview sheet or a neighbouring page named it.
  EhPreview? previewOf(int page) => _previews[page];

  EhImagePage? imageOf(int page) => switch (state[page]) {
    EhPageReady(:final image) => image,
    _ => null,
  };

  /// The page's image page, fetched once and shared by everyone who asks.
  ///
  /// A failed page stays failed until [retry], so preloading never hammers the site.
  Future<EhImagePage?> resolve(int page) {
    if (_closed || page < 1 || page > total) return Future.value();
    return switch (state[page]) {
      EhPageReady(:final image) => Future.value(image),
      EhPageFailed() => Future.value(),
      _ => _pending[page] ??= _fetch(page),
    };
  }

  Future<EhImagePage?> retry(int page) => _refetch(page);

  /// Asks for the page again from another image server, through the page's `nl` key.
  Future<EhImagePage?> reload(int page) => _refetch(page, reloadKey: imageOf(page)?.reloadKey);

  Future<EhImagePage?> _refetch(int page, {String? reloadKey}) {
    if (_closed || page < 1 || page > total) return Future.value();
    final pending = _pending[page];
    if (pending != null) return pending;
    _set(page, const EhPageLoading());
    return _pending[page] = _fetch(page, reloadKey: reloadKey);
  }

  Future<EhImagePage?> _fetch(int page, {String? reloadKey}) async {
    try {
      final preview = await _previewFor(page);
      if (preview == null) throw EhException(EhErrorKind.notFound, '${gallery.gid}-$page');
      final image = await client.imagePage(
        gid: gallery.gid,
        pageToken: preview.pageToken,
        page: page,
        reloadKey: reloadKey,
      );
      _learnNeighbours(image);
      _set(page, EhPageReady(image));
      return image;
    } catch (error) {
      _set(page, EhPageFailed(error));
      return null;
    } finally {
      _pending.remove(page);
    }
  }

  /// Fetches the sheet the page should sit on; a wrong guess at the sheet size
  /// is corrected by what the sheet held, then tried once more.
  Future<EhPreview?> _previewFor(int page) async {
    final tried = <int>{};
    while (_previews[page] == null && tried.length < 3) {
      final sheet = (page - 1) ~/ (_sheetSize ?? ehDefaultSheetSize);
      if (!tried.add(sheet)) break;
      await _loadSheet(sheet);
    }
    return _previews[page];
  }

  Future<void> _loadSheet(int sheet) => _sheets[sheet] ??= _fetchSheet(sheet).whenComplete(() {
    _sheets.remove(sheet);
  });

  Future<void> _fetchSheet(int sheet) async {
    final previews = await client.galleryPreviewSheet(gid: gallery.gid, token: gallery.token, previewSheet: sheet);
    _previews.addAll({for (final preview in previews) preview.page: preview});
    _sheetSize = ehSheetSizeFrom(sheet: sheet, previews: previews, total: total) ?? _sheetSize;
  }

  /// An image page links its neighbours, so reading on needs no preview sheet.
  void _learnNeighbours(EhImagePage image) {
    final links = [image.prevPageUrl, image.nextPageUrl].map(parseEhPageLink).nonNulls;
    for (final link in links.where((link) => link.gid == gallery.gid)) {
      _previews.putIfAbsent(link.page, () => EhPreview(pageToken: link.pageToken, page: link.page));
    }
  }

  void _set(int page, EhPageSlot slot) => update({...state, page: slot});

  @override
  void update(Map<int, EhPageSlot> newState, {bool force = false}) {
    if (!_closed) super.update(newState, force: force);
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
