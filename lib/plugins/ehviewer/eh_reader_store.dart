import 'dart:math';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';

enum EhReadingMode {
  leftToRight,
  rightToLeft,
  vertical;

  static EhReadingMode parse(String? raw) => values.where((mode) => mode.name == raw).firstOrNull ?? leftToRight;

  /// One page at a time, swiped sideways.
  bool get paged => this != vertical;

  /// Pages advance towards the left, as in manga.
  bool get reversed => this == rightToLeft;
}

enum EhTapAction { previous, next, toggleChrome }

/// What a tap at [x] across a [width]-wide reader does: the outer thirds turn
/// pages, mirrored when reading right to left, and the middle shows or hides the bars.
EhTapAction ehTapAction(double x, double width, EhReadingMode mode) {
  final third = width / 3;
  if (x >= third && x <= width - third) return EhTapAction.toggleChrome;
  return (x < third) == mode.reversed ? EhTapAction.next : EhTapAction.previous;
}

/// The gallery's page count, else the furthest page known so far.
int ehReaderTotal(EhGallery gallery, List<EhPreview> previews, int initialPage) {
  final count = gallery.pageCount ?? 0;
  if (count > 0) return count;
  return [initialPage, ...previews.map((preview) => preview.page)].reduce(max);
}

/// Pages worth fetching around [page]: the next three, then the one before.
List<int> ehPreloadPages(int page, int total) =>
    [page + 1, page + 2, page + 3, page - 1].where((p) => p >= 1 && p <= total).toList();

typedef EhPageVisibility = ({double visible, double height});

/// The page a vertical reader is on: the topmost one in view, or the last page
/// once all of it shows, since a short last page never reaches the top.
int? ehVisiblePage(Map<int, EhPageVisibility> pages, int total) {
  final shown = pages.entries.where((entry) => entry.value.visible > 0).map((entry) => entry.key);
  if (shown.isEmpty) return null;
  final last = pages[total];
  if (last != null && last.height > 0 && last.visible >= last.height - 0.5) return total;
  return shown.reduce(min);
}

class EhReaderState {
  /// 1-based page the reader is on.
  final int page;

  /// The page under the slider thumb while it is dragged.
  final int? scrubPage;
  final EhReadingMode mode;
  final bool chromeVisible;

  /// The page the vertical list is laid out from. Moves on a jump, not on a scroll.
  final int anchor;

  const EhReaderState({
    required this.page,
    required this.mode,
    required this.anchor,
    this.scrubPage,
    this.chromeVisible = true,
  });

  int get shownPage => scrubPage ?? page;

  EhReaderState copyWith({
    int? page,
    EhReadingMode? mode,
    int? anchor,
    bool? chromeVisible,
    int? scrubPage,
    bool clearScrub = false,
  }) => EhReaderState(
    page: page ?? this.page,
    mode: mode ?? this.mode,
    anchor: anchor ?? this.anchor,
    chromeVisible: chromeVisible ?? this.chromeVisible,
    scrubPage: clearScrub ? null : scrubPage ?? this.scrubPage,
  );
}

EhReadingMode ehReadingModeOf(BasePrefService prefs) =>
    EhReadingMode.parse(prefs.get<String>(optionPluginEhReadingMode));

class EhReaderStore extends Store<EhReaderState> {
  final int total;
  final BasePrefService prefs;

  /// Runs once for each page the reader settles on, never while the slider is dragged.
  final void Function(int page)? onPageSettled;
  final Map<int, EhPageVisibility> _visible = {};
  var _closed = false;

  EhReaderStore({required this.total, required int initialPage, required this.prefs, this.onPageSettled})
    : super(
        EhReaderState(
          page: _bounded(initialPage, total),
          anchor: _bounded(initialPage, total),
          mode: ehReadingModeOf(prefs),
        ),
      );

  static int _bounded(int page, int total) => total < 1 ? 1 : page.clamp(1, total);

  /// A page the reader asked for: a tap zone, the slider or the jump dialog.
  void goTo(int page) {
    final target = _bounded(page, total);
    final previous = state.page;
    if (_closed || (target == previous && target == state.anchor && state.scrubPage == null)) return;
    _visible.clear();
    update(state.copyWith(page: target, anchor: target, clearScrub: true));
    if (target != previous) onPageSettled?.call(target);
  }

  void next() => goTo(state.page + 1);

  void previous() => goTo(state.page - 1);

  /// A page the view itself swiped or scrolled to.
  void observe(int page) {
    final target = _bounded(page, total);
    if (_closed || target == state.page) return;
    update(state.copyWith(page: target));
    onPageSettled?.call(target);
  }

  /// Follows the slider while it is dragged; null gives the counter back to the page.
  void scrub(int? page) {
    final target = page == null ? null : _bounded(page, total);
    if (target == state.scrubPage) return;
    update(state.copyWith(scrubPage: target, clearScrub: target == null));
  }

  void toggleChrome() => update(state.copyWith(chromeVisible: !state.chromeVisible));

  Future<void> setMode(EhReadingMode mode) async {
    if (mode == state.mode) return;
    _visible.clear();
    update(state.copyWith(mode: mode, anchor: state.page));
    await prefs.set(optionPluginEhReadingMode, mode.name);
  }

  /// How much of [page] the vertical list laid out from [anchor] shows; settles on
  /// the page being read. Reports from a list a jump replaced are dropped.
  void pageVisibility(int page, {required int anchor, required double visible, required double height}) {
    if (_closed || state.mode.paged || anchor != state.anchor) return;
    if (visible <= 0) {
      _visible.remove(page);
    } else {
      _visible[page] = (visible: visible, height: height);
    }
    final current = ehVisiblePage(_visible, total);
    if (current != null) observe(current);
  }

  @override
  void update(EhReaderState newState, {bool force = false}) {
    if (!_closed) super.update(newState, force: force);
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _visible.clear();
    return super.destroy();
  }
}
