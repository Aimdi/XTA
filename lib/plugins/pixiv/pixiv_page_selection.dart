import 'package:flutter_triple/flutter_triple.dart';

/// Which pages of a work the reader ticked in the overview, while picking.
class PixivPageSelection {
  final bool active;
  final Set<int> pages;

  const PixivPageSelection({this.active = false, this.pages = const {}});

  /// The ticked pages in reading order.
  List<int> get ordered => [...pages]..sort();
}

class PixivPageSelectionStore extends Store<PixivPageSelection> {
  final int total;

  PixivPageSelectionStore(this.total) : super(const PixivPageSelection());

  bool get allSelected => total > 0 && state.pages.length == total;

  /// Starts picking with nothing ticked.
  void start() => update(const PixivPageSelection(active: true));

  void stop() => update(const PixivPageSelection());

  void toggle(int page) {
    if (!state.active || page < 0 || page >= total) return;
    final pages = state.pages.contains(page) ? ({...state.pages}..remove(page)) : {...state.pages, page};
    update(PixivPageSelection(active: true, pages: Set.unmodifiable(pages)));
  }

  /// Ticks every page, or clears them all when every page is ticked.
  void toggleAll() => update(
    PixivPageSelection(
      active: true,
      pages: allSelected ? const {} : Set.unmodifiable({for (var i = 0; i < total; i++) i}),
    ),
  );
}
