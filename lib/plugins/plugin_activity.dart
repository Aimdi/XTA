import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/conversation_sort.dart';
import 'package:xta/ui/sort_menu_button.dart';
import 'package:xta/utils/urls.dart';

class PluginActivityPage<T> {
  final List<T> items;
  final String? cursor;
  const PluginActivityPage(this.items, {this.cursor});
}

class PluginActivityState<T> {
  final List<T> items;
  final String? cursor;
  final bool loading;
  final Object? error;
  const PluginActivityState({this.items = const [], this.cursor, this.loading = false, this.error});
}

class PluginActivityStore<T> extends Store<PluginActivityState<T>> {
  final Future<PluginActivityPage<T>> Function(String?) loader;
  final String Function(T) idOf;
  var _closed = false;
  PluginActivityStore(this.loader, this.idOf) : super(PluginActivityState<T>());

  Future<void> load({bool more = false}) async {
    if (_closed || state.loading || (more && state.cursor == null)) return;
    final previous = state;
    update(PluginActivityState(items: previous.items, cursor: previous.cursor, loading: true));
    try {
      final page = await loader(more ? previous.cursor : null);
      if (_closed) return;
      final items = {
        for (final item in [...(more ? previous.items : <T>[]), ...page.items]) idOf(item): item,
      };
      update(
        PluginActivityState(
          items: items.values.toList(),
          cursor: page.cursor?.isNotEmpty != true || (page.cursor == previous.cursor && more) ? null : page.cursor,
        ),
      );
    } catch (error) {
      if (!_closed) update(PluginActivityState(items: previous.items, cursor: previous.cursor, error: error));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}

/// An on-device order for an activity list, remembered for the session in
/// [ConversationSortStore]. Only what is loaded is sorted.
class PluginActivitySort<T, S> {
  final S Function(ConversationSorts sorts) chosen;
  final void Function(ConversationSortStore store, S sort) select;
  final List<S> Function(List<T> items) optionsFor;
  final List<T> Function(List<T> items, S sort) arrange;
  final String Function(L10n l10n, S sort) labelOf;
  final IconData Function(S sort) iconOf;
  const PluginActivitySort({
    required this.chosen,
    required this.select,
    required this.optionsFor,
    required this.arrange,
    required this.labelOf,
    required this.iconOf,
  });

  /// [items] in the chosen order, and the control to change it when more than
  /// one order fits what is loaded.
  ({List<T> items, Widget? control}) apply(ConversationSortStore store, List<T> items) {
    final options = optionsFor(items);
    final sort = effectiveSort(chosen(store.state), options);
    return (
      items: arrange(items, sort),
      control: options.length < 2 || items.length < 2
          ? null
          : SortMenuButton<S>(
              value: sort,
              options: options,
              labelOf: labelOf,
              iconOf: iconOf,
              onSelected: (sort) => select(store, sort),
            ),
    );
  }
}

/// Quotes have no server ranking on Bluesky or Mastodon: by date or likes.
PluginActivitySort<T, QuoteSort> pluginQuoteSort<T>({
  required DateTime? Function(T quote) postedAt,
  required int Function(T quote) likes,
}) => PluginActivitySort(
  chosen: (sorts) => sorts.quotes,
  select: (store, sort) => store.selectQuotes(sort),
  optionsFor: (_) => pluginQuoteSorts,
  arrange: (items, sort) => orderQuotes(items, sort, postedAt: postedAt, likes: likes),
  labelOf: quoteSortLabel,
  iconOf: quoteSortIcon,
);

/// The network's own order, plus follower order when the payload carries
/// follower counts.
PluginActivitySort<T, ReposterSort> pluginReposterSort<T>({required int? Function(T person) followers}) =>
    PluginActivitySort(
      chosen: (sorts) => sorts.reposters,
      select: (store, sort) => store.selectReposters(sort),
      optionsFor: (items) => reposterSortsFor(items, followers: followers),
      arrange: (items, sort) => sortReposters(items, sort, followers: followers),
      labelOf: reposterSortLabel,
      iconOf: reposterSortIcon,
    );

Future<void> openPluginActivity<T>(
  BuildContext context, {
  required String title,
  required String postUrl,
  required Future<PluginActivityPage<T>> Function(String?) loader,
  required String Function(T) idOf,
  required Widget Function(BuildContext, T) itemBuilder,
  required String Function(L10n, Object) errorLabel,
  PluginActivitySort<T, Object?>? sort,
}) => Navigator.push<void>(
  context,
  MaterialPageRoute(
    builder: (_) => _ActivityScreen<T>(
      title: title,
      postUrl: postUrl,
      loader: loader,
      idOf: idOf,
      itemBuilder: itemBuilder,
      errorLabel: errorLabel,
      sort: sort,
    ),
  ),
);

class _ActivityScreen<T> extends StatefulWidget {
  final String title;
  final String postUrl;
  final Future<PluginActivityPage<T>> Function(String?) loader;
  final String Function(T) idOf;
  final Widget Function(BuildContext, T) itemBuilder;
  final String Function(L10n, Object) errorLabel;
  final PluginActivitySort<T, Object?>? sort;
  const _ActivityScreen({
    required this.title,
    required this.postUrl,
    required this.loader,
    required this.idOf,
    required this.itemBuilder,
    required this.errorLabel,
    this.sort,
  });
  @override
  State<_ActivityScreen<T>> createState() => _ActivityScreenState<T>();
}

class _ActivityScreenState<T> extends State<_ActivityScreen<T>> {
  late final _store = PluginActivityStore<T>(widget.loader, widget.idOf)..load();
  late final _sorts = widget.sort == null ? null : context.read<ConversationSortStore>();
  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: [
          IconButton(
            tooltip: l10n.open_in_browser,
            icon: const Icon(Icons.open_in_new),
            onPressed: () => openUri(context, widget.postUrl),
          ),
        ],
      ),
      body: ScopedBuilder<PluginActivityStore<T>, PluginActivityState<T>>(
        store: _store,
        onState: (context, state) => RefreshIndicator(
          onRefresh: _store.load,
          child: switch (_sorts) {
            final sorts? => ScopedBuilder<ConversationSortStore, ConversationSorts>(
              store: sorts,
              onState: (context, _) => _list(context, state, widget.sort!.apply(sorts, state.items)),
            ),
            null => _list(context, state, (items: state.items, control: null)),
          },
        ),
      ),
    );
  }

  Widget _list(BuildContext context, PluginActivityState<T> state, ({List<T> items, Widget? control}) sorted) {
    final l10n = L10n.of(context);
    final control = sorted.control;
    final leading = control == null ? 0 : 1;
    return ListView.builder(
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: leading + sorted.items.length + 1,
      itemBuilder: (context, index) {
        if (index < leading) return Align(alignment: AlignmentDirectional.centerStart, child: control);
        if (index - leading < sorted.items.length) return widget.itemBuilder(context, sorted.items[index - leading]);
        return Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              if (state.loading)
                const CircularProgressIndicator()
              else if (state.error case final error?) ...[
                Text(widget.errorLabel(l10n, error), textAlign: TextAlign.center),
                TextButton(
                  onPressed: () => _store.load(more: state.items.isNotEmpty && state.cursor != null),
                  child: Text(l10n.retry),
                ),
              ] else if (state.items.isEmpty)
                Text(l10n.no_results)
              else if (state.cursor != null)
                OutlinedButton(onPressed: () => _store.load(more: true), child: Text(l10n.plugin_reddit_load_more)),
            ],
          ),
        );
      },
    );
  }
}
