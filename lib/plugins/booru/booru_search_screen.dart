import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_errors.dart';
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
  var _suggestions = const <BooruTagSuggestion>[];
  Timer? _suggestDebounce;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    _controller.addListener(_onQueryChanged);
    if ((widget.initialQuery ?? '').trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _search());
    }
  }

  @override
  void dispose() {
    _suggestDebounce?.cancel();
    _controller.removeListener(_onQueryChanged);
    _controller.dispose();
    _results?.destroy();
    super.dispose();
  }

  void _onQueryChanged() {
    _suggestDebounce?.cancel();
    final token = lastBooruTagToken(_controller.text) ?? '';
    if (token.length < 2 || _submitted) {
      if (_suggestions.isNotEmpty) setState(() => _suggestions = const []);
      return;
    }
    _suggestDebounce = Timer(const Duration(milliseconds: 280), () async {
      final suggestions = await context.read<BooruClient>().suggestTags(token);
      if (!mounted || _submitted) return;
      setState(() => _suggestions = suggestions);
    });
  }

  Future<void> _search([String? override]) async {
    final query = (override ?? _controller.text).trim();
    if (query.isEmpty) return;
    if (override != null) _controller.text = override;

    final client = context.read<BooruClient>();
    _results?.destroy();
    final store = BooruFeedStore(
      client,
      ({required page}) => client.search(query, page: page),
    );
    setState(() {
      _results = store;
      _submitted = true;
      _suggestions = const [];
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

  Future<void> _clearHistory() async {
    await PrefService.of(
      context,
      listen: false,
    ).set(optionPluginBooruSearchHistory, '[]');
    setState(() {});
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

  Future<void> _followLastTag() async {
    final tag = lastBooruTagToken(_controller.text);
    if (tag == null) return;
    await context.read<BooruTagsStore>().add(tag);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(L10n.of(context).plugin_booru_followed(tag))),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final followed = context.read<BooruTagsStore>().state;

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _controller,
          autofocus: true,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            hintText: l10n.plugin_booru_search_hint,
            border: InputBorder.none,
            suffixIcon: IconButton(
              tooltip: l10n.search,
              icon: const Icon(Icons.search),
              onPressed: _search,
            ),
          ),
          onChanged: (_) {
            if (_submitted) setState(() => _submitted = false);
          },
          onSubmitted: (_) => _search(),
        ),
        actions: [
          IconButton(
            tooltip: l10n.plugin_booru_follow_tag,
            icon: const Icon(Icons.person_add_alt_1_outlined),
            onPressed: _followLastTag,
          ),
        ],
      ),
      body: !_submitted
          ? ListView(
              children: [
                if (_suggestions.isNotEmpty) ...[
                  PrefTitle(title: Text(l10n.plugin_booru_suggestions)),
                  for (final suggestion in _suggestions)
                    ListTile(
                      leading: const Icon(Icons.sell_outlined),
                      title: Text(suggestion.name),
                      subtitle: suggestion.postCount == null
                          ? null
                          : Text(
                              l10n.plugin_booru_tag_count(
                                suggestion.postCount!,
                              ),
                            ),
                      onTap: () {
                        final parts = _controller.text.trim().split(
                          RegExp(r'\s+'),
                        );
                        if (parts.isEmpty) {
                          _search(suggestion.name);
                          return;
                        }
                        parts[parts.length - 1] = suggestion.name;
                        _search(parts.join(' '));
                      },
                    ),
                ],
                if (followed.isNotEmpty) ...[
                  PrefTitle(title: Text(l10n.plugin_booru_followed_tags)),
                  for (final tag in followed)
                    ListTile(
                      leading: const Icon(Icons.check),
                      title: Text(tag),
                      onTap: () => _search(tag),
                    ),
                ],
                PrefTitle(title: Text(l10n.plugin_booru_search_history)),
                if (_history().isEmpty)
                  ListTile(title: Text(l10n.plugin_booru_search_history_empty))
                else ...[
                  for (final query in _history())
                    ListTile(
                      leading: const Icon(Icons.history),
                      title: Text(query),
                      onTap: () => _search(query),
                    ),
                  ListTile(
                    leading: const Icon(Icons.delete_outline),
                    title: Text(l10n.plugin_booru_clear_history),
                    onTap: _clearHistory,
                  ),
                ],
              ],
            )
          : ScopedBuilder<BooruFeedStore, List<BooruPost>>(
              store: _results!,
              onLoading: (_) =>
                  const Center(child: CircularProgressIndicator()),
              onError: (_, error) => FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: booruErrorMessage(l10n, error),
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
