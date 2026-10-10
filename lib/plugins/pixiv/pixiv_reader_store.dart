import 'package:flutter_triple/flutter_triple.dart';

class PixivReaderState {
  final bool vertical;
  final int pageIndex;

  /// The page under the slider thumb while it is dragged.
  final int? scrubIndex;

  /// Pages show their original files instead of the large ones, for this visit.
  final bool hd;

  /// The reader is magnified, so pages decode every pixel.
  final bool zoomed;

  /// Page images that finished loading, which can be shared from the image cache.
  final Set<String> loaded;

  const PixivReaderState({
    this.vertical = true,
    this.pageIndex = 0,
    this.scrubIndex,
    this.hd = false,
    this.zoomed = false,
    this.loaded = const {},
  });

  int get shownIndex => scrubIndex ?? pageIndex;

  /// Decode at full size rather than at the screen's width.
  bool get fullResolution => hd || zoomed;

  PixivReaderState copyWith({
    bool? vertical,
    int? pageIndex,
    int? Function()? scrubIndex,
    bool? hd,
    bool? zoomed,
    Set<String>? loaded,
  }) => PixivReaderState(
    vertical: vertical ?? this.vertical,
    pageIndex: pageIndex ?? this.pageIndex,
    scrubIndex: scrubIndex == null ? this.scrubIndex : scrubIndex(),
    hd: hd ?? this.hd,
    zoomed: zoomed ?? this.zoomed,
    loaded: loaded ?? this.loaded,
  );
}

class PixivReaderStore extends Store<PixivReaderState> {
  final int pageCount;
  final Map<int, double> _visibleAreas = {};
  int _navigationGeneration = 0;
  bool _restoringPosition = false;
  bool _closed = false;

  PixivReaderStore({required this.pageCount, int initialPage = 0, bool vertical = true, bool hd = false})
    : super(PixivReaderState(vertical: vertical, pageIndex: _bounded(initialPage, pageCount), hd: hd));

  static int _bounded(int index, int count) => count <= 0 ? 0 : index.clamp(0, count - 1);

  void selectPage(int index) {
    if (_closed) return;
    final next = _bounded(index, pageCount);
    if (next == state.pageIndex) return;
    update(state.copyWith(pageIndex: next));
  }

  /// Follows the slider while dragging; null hands the counter back to the page.
  void scrub(int? index) {
    if (_closed) return;
    final next = index == null ? null : _bounded(index, pageCount);
    if (next == state.scrubIndex) return;
    update(state.copyWith(scrubIndex: () => next));
  }

  void toggleDirection() {
    if (_closed) return;
    _visibleAreas.clear();
    update(state.copyWith(vertical: !state.vertical, scrubIndex: () => null, zoomed: false));
  }

  /// Switches every page between its large and its original file.
  void toggleHd() {
    if (!_closed) update(state.copyWith(hd: !state.hd));
  }

  void setZoomed(bool zoomed) {
    if (!_closed && zoomed != state.zoomed) update(state.copyWith(zoomed: zoomed));
  }

  /// [url] has loaded and is in the image cache.
  void pageLoaded(String url) {
    if (!_closed && !state.loaded.contains(url)) update(state.copyWith(loaded: {...state.loaded, url}));
  }

  void pageVisibility(int index, double area) {
    if (_closed || _restoringPosition || !state.vertical || index < 0 || index >= pageCount) return;
    if (area <= 0) {
      _visibleAreas.remove(index);
    } else {
      _visibleAreas[index] = area;
    }
    if (_visibleAreas.isEmpty) return;
    final mostVisible = _visibleAreas.entries.reduce((a, b) => a.value >= b.value ? a : b);
    selectPage(mostVisible.key);
  }

  /// Retire old visibility measurements before a jump or direction change.
  int beginNavigation(int index) {
    if (_closed) return _navigationGeneration;
    _restoringPosition = true;
    _visibleAreas.clear();
    selectPage(index);
    return ++_navigationGeneration;
  }

  bool isCurrentNavigation(int generation) => !_closed && generation == _navigationGeneration;

  bool finishNavigation(int generation) {
    if (!isCurrentNavigation(generation)) return false;
    _restoringPosition = false;
    _visibleAreas.clear();
    return true;
  }

  /// A page turned by a swipe; the page left behind is no longer magnified.
  void observedPage(int index) {
    if (_restoringPosition) return;
    if (!state.vertical) setZoomed(false);
    selectPage(index);
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _navigationGeneration++;
    _visibleAreas.clear();
    return super.destroy();
  }
}
