import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/ui/errors.dart';

class EhSearchScreen extends StatefulWidget {
  final String? initialQuery;

  const EhSearchScreen({super.key, this.initialQuery});

  @override
  State<EhSearchScreen> createState() => _EhSearchScreenState();
}

class _EhSearchScreenState extends State<EhSearchScreen> {
  late final TextEditingController _controller;
  EhFeedStore? _results;
  var _submitted = false;
  late Set<EhCategory> _categories;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    _categories = Set.of(context.read<EhClient>().includedCategories);
    if ((widget.initialQuery ?? '').trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _search());
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _results?.destroy();
    super.dispose();
  }

  Future<void> _search() async {
    final query = _controller.text.trim();
    if (query.isEmpty) return;
    final client = context.read<EhClient>();
    _results?.destroy();
    final store = EhFeedStore(
      ({pageUrl}) =>
          client.search(query, pageUrl: pageUrl, categories: _categories),
    );
    setState(() {
      _results = store;
      _submitted = true;
    });
    await _remember(query);
    await store.refresh();
  }

  Future<void> _remember(String query) async {
    final prefs = PrefService.of(context, listen: false);
    final raw = prefs.get<String>(optionPluginEhSearchHistory) ?? '[]';
    var history = <String>[];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) history = decoded.whereType<String>().toList();
    } catch (_) {}
    history.remove(query);
    history.insert(0, query);
    if (history.length > 20) history = history.take(20).toList();
    await prefs.set(optionPluginEhSearchHistory, jsonEncode(history));
  }

  List<String> _history() {
    final raw =
        PrefService.of(
          context,
          listen: false,
        ).get<String>(optionPluginEhSearchHistory) ??
        '[]';
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) return decoded.whereType<String>().toList();
    } catch (_) {}
    return const [];
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _controller,
          autofocus: true,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            hintText: l10n.plugin_eh_search_hint,
            border: InputBorder.none,
            suffixIcon: IconButton(
              tooltip: l10n.search,
              icon: const Icon(Icons.search),
              onPressed: _search,
            ),
          ),
          onSubmitted: (_) => _search(),
        ),
      ),
      body: Column(
        children: [
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              children: [
                for (final cat in EhCategory.values)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: FilterChip(
                      label: Text(cat.label),
                      selected: _categories.contains(cat),
                      onSelected: (selected) {
                        setState(() {
                          if (selected) {
                            _categories.add(cat);
                          } else if (_categories.length > 1) {
                            _categories.remove(cat);
                          }
                        });
                      },
                    ),
                  ),
              ],
            ),
          ),
          Expanded(
            child: !_submitted
                ? ListView(
                    children: [
                      for (final query in _history())
                        ListTile(
                          leading: const Icon(Icons.history),
                          title: Text(query),
                          onTap: () {
                            _controller.text = query;
                            _search();
                          },
                        ),
                    ],
                  )
                : ScopedBuilder<EhFeedStore, List<EhGallery>>.transition(
                    store: _results!,
                    onLoading: (_) =>
                        const Center(child: CircularProgressIndicator()),
                    onError: (_, error) => FullPageErrorWidget(
                      error: error,
                      stackTrace: null,
                      prefix: ehErrorMessage(l10n, error),
                      onRetry: () => _results!.refresh(),
                    ),
                    onState: (context, galleries) {
                      if (galleries.isEmpty) {
                        return Center(child: Text(l10n.plugin_eh_empty_search));
                      }
                      return EhGalleryGrid(
                        galleries: galleries,
                        onRefresh: _results!.refresh,
                        loadingMore: _results!.loadingMore,
                        onNearEnd: _results!.loadMore,
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
