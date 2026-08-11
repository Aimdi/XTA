import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/ui/errors.dart';

class BooruSearchScreen extends StatefulWidget {
  final String? initialQuery;

  const BooruSearchScreen({super.key, this.initialQuery});

  @override
  State<BooruSearchScreen> createState() => _BooruSearchScreenState();
}

class _BooruSearchScreenState extends State<BooruSearchScreen> {
  late final TextEditingController _controller;
  BooruFeedStore? _results;
  var _submitted = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
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

    final client = context.read<BooruClient>();
    _results?.destroy();
    final store = BooruFeedStore(
      client,
      ({required page}) => client.search(query, page: page),
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
    final raw = prefs.get<String>(optionPluginBooruSearchHistory) ?? '[]';
    var history = <String>[];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        history = decoded.whereType<String>().toList();
      }
    } catch (_) {}
    history.remove(query);
    history.insert(0, query);
    if (history.length > 20) history = history.take(20).toList();
    await prefs.set(optionPluginBooruSearchHistory, jsonEncode(history));
  }

  List<String> _history() {
    final prefs = PrefService.of(context, listen: false);
    final raw = prefs.get<String>(optionPluginBooruSearchHistory) ?? '[]';
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) return decoded.whereType<String>().toList();
    } catch (_) {}
    return const [];
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final tags = context.read<BooruTagsStore>();

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _controller,
          autofocus: true,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            hintText: l10n.plugin_booru_search_hint,
            border: InputBorder.none,
          ),
          onSubmitted: (_) => _search(),
        ),
        actions: [
          IconButton(icon: const Icon(Icons.search), onPressed: _search),
          IconButton(
            tooltip: l10n.plugin_booru_follow_tag,
            icon: const Icon(Icons.person_add_alt_1_outlined),
            onPressed: () async {
              final tag = normaliseBooruTag(_controller.text);
              if (tag == null) return;
              await tags.add(tag);
              if (!context.mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(l10n.plugin_booru_followed(tag))),
              );
            },
          ),
        ],
      ),
      body: !_submitted
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
          : ScopedBuilder<BooruFeedStore, List<BooruPost>>.transition(
              store: _results!,
              onLoading: (_) =>
                  const Center(child: CircularProgressIndicator()),
              onError: (_, error) => FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: l10n.plugin_booru_load_error,
                onRetry: () => _results!.refresh(),
              ),
              onState: (context, posts) {
                if (posts.isEmpty) {
                  return Center(child: Text(l10n.plugin_booru_empty_search));
                }
                return BooruPostGrid(
                  posts: posts,
                  onRefresh: _results!.refresh,
                  loadingMore: _results!.loadingMore,
                  onNearEnd: _results!.loadMore,
                );
              },
            ),
    );
  }
}
