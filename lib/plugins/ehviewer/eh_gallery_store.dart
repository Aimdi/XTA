import 'dart:math' as math;

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';

/// A gallery page: its detail once loaded and the preview tiles read so far.
class EhGalleryState {
  final EhGalleryDetail? detail;
  final List<EhPreview> previews;

  /// The preview sheet (`?p=`) to read next.
  final int nextSheet;
  final bool loadingMore;

  /// Reading [nextSheet] failed; scrolling leaves it to the button from here.
  final bool moreFailed;

  const EhGalleryState({
    this.detail,
    this.previews = const [],
    this.nextSheet = 1,
    this.loadingMore = false,
    this.moreFailed = false,
  });

  factory EhGalleryState.loaded(EhGalleryDetail detail) =>
      EhGalleryState(detail: detail, previews: detail.previews, nextSheet: detail.previewSheetIndex + 1);

  bool get hasMorePreviews => nextSheet < (detail?.previewSheetCount ?? 0);

  EhGalleryState copyWith({List<EhPreview>? previews, int? nextSheet, bool? loadingMore, bool? moreFailed}) =>
      EhGalleryState(
        detail: detail,
        previews: previews ?? this.previews,
        nextSheet: nextSheet ?? this.nextSheet,
        loadingMore: loadingMore ?? this.loadingMore,
        moreFailed: moreFailed ?? this.moreFailed,
      );
}

/// [known] previews plus a newly read [sheet], each page once, in page order.
List<EhPreview> mergeEhPreviews(List<EhPreview> known, List<EhPreview> sheet) {
  final pages = known.map((preview) => preview.page).toSet();
  return [...known, ...sheet.where((preview) => !pages.contains(preview.page))]
    ..sort((a, b) => a.page.compareTo(b.page));
}

class EhGalleryStore extends Store<EhGalleryState> {
  final EhClient client;
  final EhHistoryStore history;
  final EhGallery gallery;

  /// Bumped by every load and on destroy, so a preview sheet that arrives for
  /// an older load is dropped.
  var _generation = 0;
  var _closed = false;

  EhGalleryStore({required this.client, required this.history, required this.gallery}) : super(const EhGalleryState());

  /// The loaded detail, else what the gallery list already knew.
  EhGallery get shown => state.detail ?? gallery;

  /// The page history left off at, when that is past the first.
  int? get continuePage {
    final page = history.entryFor(gallery.gid)?.lastPage;
    if (page == null || page <= 1) return null;
    final total = shown.pageCount;
    return total == null ? page : math.min(page, total);
  }

  Future<void> load() {
    _generation++;
    if (state.loadingMore) update(state.copyWith(loadingMore: false));
    return execute(() async {
      final detail = await client.galleryDetail(gid: gallery.gid, token: gallery.token);
      return EhGalleryState.loaded(detail);
    });
  }

  /// Reads the next preview sheet; the button's way in, so it retries a failure.
  Future<void> loadMorePreviews() async {
    final start = state;
    if (_closed || !start.hasMorePreviews || start.loadingMore) return;
    final generation = _generation;
    update(start.copyWith(loadingMore: true, moreFailed: false));
    try {
      final sheet = await client.galleryPreviewSheet(
        gid: gallery.gid,
        token: gallery.token,
        previewSheet: start.nextSheet,
      );
      if (generation != _generation) return;
      update(
        state.copyWith(
          previews: mergeEhPreviews(state.previews, sheet),
          nextSheet: start.nextSheet + 1,
          loadingMore: false,
        ),
      );
    } catch (_) {
      if (generation == _generation) update(state.copyWith(loadingMore: false, moreFailed: true));
    }
  }

  /// The reader is close to the last preview: read on, unless the last try failed.
  Future<void> nearEnd() async {
    if (!state.moreFailed) await loadMorePreviews();
  }

  @override
  void propagate(Triple<EhGalleryState> triple) {
    if (!_closed) super.propagate(triple);
  }

  @override
  Future destroy() {
    _closed = true;
    _generation++;
    return super.destroy();
  }
}
