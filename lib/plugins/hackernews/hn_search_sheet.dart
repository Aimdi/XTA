import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_card.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

Future<void> showHnSearchSheet(BuildContext context, {String? initialQuery}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => HnSearchSheet(initialQuery: initialQuery),
  );
}

class HnSearchSheet extends StatefulWidget {
  final String? initialQuery;

  const HnSearchSheet({super.key, this.initialQuery});

  @override
  State<HnSearchSheet> createState() => _HnSearchSheetState();
}

class _HnSearchSheetState extends State<HnSearchSheet> {
  late final TextEditingController _controller;
  late final _HnSearchStore _store;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    _store = _HnSearchStore(context.read<HackerNewsClient>());
    final query = widget.initialQuery?.trim() ?? '';
    if (query.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _submit(query));
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _store.destroy();
    super.dispose();
  }

  Future<void> _submit(String query) async {
    await context.read<HnSearchHistoryStore>().remember(query);
    if (!mounted) return;
    await _store.search(query);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final history = context.read<HnSearchHistoryStore>();
    return SizedBox(
      height: MediaQuery.sizeOf(context).height * 0.88,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
            child: SearchBar(
              controller: _controller,
              hintText: l10n.plugin_hn_search_hint,
              autoFocus: (widget.initialQuery ?? '').isEmpty,
              textInputAction: TextInputAction.search,
              leading: const Icon(Icons.search),
              onSubmitted: _submit,
            ),
          ),
          Expanded(
            child: ScopedBuilder<_HnSearchStore, List<HnStory>>(
              store: _store,
              onLoading: (_) =>
                  const Center(child: CircularProgressIndicator()),
              // A bare exception string told the reader nothing they could act
              // on, and no way to try the search again.
              onError: (_, error) => FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: l10n.plugin_hn_search_hint,
                onRetry: () => _submit(_store.query),
              ),
              onState: (_, stories) {
                if (_store.query.isEmpty) {
                  return ScopedBuilder<HnSearchHistoryStore, List<String>>(
                    store: history,
                    onState: (_, items) => ListView(
                      children: [
                        for (final item in items)
                          ListTile(
                            leading: const Icon(Icons.history),
                            title: Text(item),
                            onTap: () {
                              _controller.text = item;
                              _submit(item);
                            },
                          ),
                      ],
                    ),
                  );
                }
                if (stories.isEmpty) {
                  return EmptyPane(
                    icon: Icons.search_off,
                    message: l10n.plugin_hn_search_empty,
                  );
                }
                return FeedListView(
                  itemCount: stories.length,
                  itemBuilder: (context, index) =>
                      HnStoryCard(story: stories[index]),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _HnSearchStore extends Store<List<HnStory>> {
  final HackerNewsClient client;
  var query = '';

  _HnSearchStore(this.client) : super(const []);

  Future<void> search(String value) async {
    query = value.trim();
    if (query.isEmpty) {
      update(const []);
      return;
    }
    await execute(() async {
      final page = await client.search(query);
      return page.stories;
    });
  }
}
