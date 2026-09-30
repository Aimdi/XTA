import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/reading/shared_filter_engine.dart';
import 'package:xta/reading/shared_filter_rule.dart';
import 'package:xta/ui/feed_list.dart';

class _SharedFilterData extends InheritedWidget {
  final SharedFilterEngine engine;
  final int revision;
  const _SharedFilterData({required this.engine, required this.revision, required super.child});

  @override
  bool updateShouldNotify(_SharedFilterData oldWidget) => engine != oldWidget.engine || revision != oldWidget.revision;
}

/// Makes the shared filters available below; surfaces that project through them rebuild when verdicts change.
class SharedFilterRoot extends StatelessWidget {
  final SharedFilterEngine engine;
  final Widget child;
  const SharedFilterRoot({super.key, required this.engine, required this.child});

  @override
  Widget build(BuildContext context) => ScopedBuilder<SharedFilterEngine, int>(
    store: engine,
    onState: (context, revision) => _SharedFilterData(engine: engine, revision: revision, child: child),
  );
}

/// Marks results the reader searched for, so rules limited to timelines leave them alone.
class SharedFilterSurface extends InheritedWidget {
  final SharedFilterScope scope;
  const SharedFilterSurface({super.key, required this.scope, required super.child});

  @override
  bool updateShouldNotify(SharedFilterSurface oldWidget) => scope != oldWidget.scope;
}

SharedFilterEngine? sharedFilterEngineOf(BuildContext context) =>
    context.dependOnInheritedWidgetOfExactType<_SharedFilterData>()?.engine;

/// The items to show, in their original order: hidden and still-unchecked items are left out, so lists and grids
/// never lay out a gap or fetch media for them. Folded items stay, with the rule that matched them.
class SharedFilterProjection<T> {
  final List<T> visible;
  final Map<T, String> _folds;
  final int hidden;
  const SharedFilterProjection._(this.visible, this._folds, this.hidden);

  SharedFilterProjection.all(List<T> items) : this._(items, const {}, 0);

  /// The pattern that folded [item], or null when it is shown as is.
  String? foldReason(T item) => _folds[item];
}

SharedFilterProjection<T> sharedFilterProject<T>(BuildContext context, List<T> items, String Function(T item) textOf) {
  final engine = sharedFilterEngineOf(context);
  if (engine == null || items.isEmpty || !engine.active) return SharedFilterProjection.all(items);
  final scope = context.dependOnInheritedWidgetOfExactType<SharedFilterSurface>()?.scope ?? SharedFilterScope.timelines;
  final visible = <T>[];
  final folds = Map<T, String>.identity();
  for (final item in items) {
    final verdict = engine.verdict(textOf(item), scope);
    if (verdict.hidden) continue;
    visible.add(item);
    if (verdict.folded) folds[item] = verdict.reason!;
  }
  if (visible.length == items.length && folds.isEmpty) return SharedFilterProjection.all(items);
  return SharedFilterProjection._(visible, folds, items.length - visible.length);
}

class _FoldStore extends Store<bool> {
  _FoldStore() : super(false);
  void toggle() => update(!state);
}

/// A folded post: one line naming the rule that matched, and a Show control. The post is built only once shown.
class SharedFilterFold extends StatefulWidget {
  final String reason;
  final Widget child;
  const SharedFilterFold({super.key, required this.reason, required this.child});

  @override
  State<SharedFilterFold> createState() => _SharedFilterFoldState();
}

class _SharedFilterFoldState extends State<SharedFilterFold> {
  final _open = _FoldStore();

  @override
  void dispose() {
    _open.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return ScopedBuilder<_FoldStore, bool>(
      store: _open,
      onState: (context, open) {
        final bar = InkWell(
          onTap: _open.toggle,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 48),
            child: Padding(
              padding: const EdgeInsetsDirectional.fromSTEB(16, 4, 8, 4),
              child: Row(
                children: [
                  Icon(Icons.filter_alt_outlined, size: 18, color: theme.colorScheme.onSurfaceVariant),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      l10n.filter_fold_matched(widget.reason),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                    ),
                  ),
                  Text(
                    open ? l10n.filter_fold_hide_again : l10n.show,
                    style: TextStyle(color: theme.colorScheme.primary),
                  ),
                ],
              ),
            ),
          ),
        );
        if (!open) return Semantics(button: true, child: bar);
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [bar, widget.child],
        );
      },
    );
  }
}

extension SharedFilterWidgets on Widget {
  /// Folds this post when [reason] is set.
  Widget foldedBy(String? reason, {Key? key}) =>
      reason == null ? this : SharedFilterFold(key: key, reason: reason, child: this);
}

/// Where automatic paging stopped because the filters hid everything it loaded.
class SharedFilterHeldPaging extends StatelessWidget {
  final VoidCallback onLoadMore;
  const SharedFilterHeldPaging({super.key, required this.onLoadMore});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            l10n.filter_all_hidden,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            key: const ValueKey('filter-load-more'),
            style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
            onPressed: onLoadMore,
            child: Text(l10n.filter_load_more),
          ),
        ],
      ),
    );
  }
}

/// A [FeedListView] of [items] seen through the shared filters: hidden items take no row, folded ones sit behind
/// the rule that matched them.
class SharedFilterFeedList<T> extends StatelessWidget {
  final List<T> items;
  final String Function(T item) textOf;
  final Object Function(T item) keyOf;
  final Widget Function(BuildContext context, T item, int index) itemBuilder;
  final ScrollController? controller;
  final EdgeInsetsGeometry? padding;
  final ScrollPhysics? physics;
  final Widget? footer;

  const SharedFilterFeedList({
    super.key,
    required this.items,
    required this.textOf,
    required this.keyOf,
    required this.itemBuilder,
    this.controller,
    this.padding,
    this.physics,
    this.footer,
  });

  @override
  Widget build(BuildContext context) {
    final shared = sharedFilterProject(context, items, textOf);
    final visible = shared.visible;
    return FeedListView(
      controller: controller,
      padding: padding,
      physics: physics,
      itemCount: visible.length + (footer == null ? 0 : 1),
      itemBuilder: (context, index) {
        if (index >= visible.length) return footer!;
        final item = visible[index];
        return itemBuilder(
          context,
          item,
          index,
        ).foldedBy(shared.foldReason(item), key: ValueKey(('fold', keyOf(item))));
      },
    );
  }
}

/// Stops automatic paging after [limit] pages in a row that the filters hid entirely, so a broad filter cannot
/// page through a whole timeline unseen. A genuine scroll or an explicit Load more continues.
class SharedFilterPagingGuard {
  final int limit;
  int _checkedUpTo = 0;
  int _hiddenPages = 0;
  bool _held = false;
  SharedFilterPagingGuard({this.limit = 2});

  bool get held => _held;

  /// Whether the next page may load. [hiddenFrom] says whether every item loaded after an index is hidden.
  bool allowFetch(int loaded, bool Function(int from) hiddenFrom) {
    if (_held) return false;
    if (loaded > _checkedUpTo) {
      _hiddenPages = hiddenFrom(_checkedUpTo) ? _hiddenPages + 1 : 0;
      _checkedUpTo = loaded;
    }
    _held = _hiddenPages >= limit;
    return !_held;
  }

  /// True when paging was held and may continue now.
  bool release() {
    if (!_held) return false;
    _held = false;
    _hiddenPages = 0;
    return true;
  }
}
