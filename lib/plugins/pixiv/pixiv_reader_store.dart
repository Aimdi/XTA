import 'package:flutter_triple/flutter_triple.dart';

class PixivReaderState {
  final bool vertical;
  final int pageIndex;

  const PixivReaderState({this.vertical = true, this.pageIndex = 0});
}

class PixivReaderStore extends Store<PixivReaderState> {
  final int pageCount;
  final Map<int, double> _visibleAreas = {};
  int _navigationGeneration = 0;
  bool _restoringPosition = false;
  bool _closed = false;

  PixivReaderStore({required this.pageCount, int initialPage = 0})
    : super(PixivReaderState(pageIndex: _bounded(initialPage, pageCount)));

  static int _bounded(int index, int count) => count <= 0 ? 0 : index.clamp(0, count - 1);

  void selectPage(int index) {
    if (_closed) return;
    final next = _bounded(index, pageCount);
    if (next == state.pageIndex) return;
    update(PixivReaderState(vertical: state.vertical, pageIndex: next));
  }

  void toggleDirection() {
    if (_closed) return;
    _visibleAreas.clear();
    update(PixivReaderState(vertical: !state.vertical, pageIndex: state.pageIndex));
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

  void observedPage(int index) {
    if (!_restoringPosition) selectPage(index);
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _navigationGeneration++;
    _visibleAreas.clear();
    return super.destroy();
  }
}
