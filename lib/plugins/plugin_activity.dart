import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
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

Future<void> openPluginActivity<T>(
  BuildContext context, {
  required String title,
  required String postUrl,
  required Future<PluginActivityPage<T>> Function(String?) loader,
  required String Function(T) idOf,
  required Widget Function(BuildContext, T) itemBuilder,
  required String Function(L10n, Object) errorLabel,
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
  const _ActivityScreen({
    required this.title,
    required this.postUrl,
    required this.loader,
    required this.idOf,
    required this.itemBuilder,
    required this.errorLabel,
  });
  @override
  State<_ActivityScreen<T>> createState() => _ActivityScreenState<T>();
}

class _ActivityScreenState<T> extends State<_ActivityScreen<T>> {
  late final _store = PluginActivityStore<T>(widget.loader, widget.idOf)..load();
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
          child: ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            itemCount: state.items.length + 1,
            itemBuilder: (context, index) {
              if (index < state.items.length) return widget.itemBuilder(context, state.items[index]);
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
                      OutlinedButton(
                        onPressed: () => _store.load(more: true),
                        child: Text(l10n.plugin_reddit_load_more),
                      ),
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}
