import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_models.dart';

enum SubstackLoadedOrder { newest, oldest, popular }

class SubstackLoadedOptions {
  final String query;
  final SubstackLoadedOrder order;
  const SubstackLoadedOptions({this.query = '', this.order = SubstackLoadedOrder.newest});
  SubstackLoadedOptions copy({String? query, SubstackLoadedOrder? order}) =>
      SubstackLoadedOptions(query: query ?? this.query, order: order ?? this.order);
}

class SubstackHomeOptions {
  final Map<String, SubstackLoadedOptions> feeds;
  final int librarySection;
  final String libraryQuery;
  const SubstackHomeOptions({this.feeds = const {}, this.librarySection = 0, this.libraryQuery = ''});
}

/// Session choices survive tab/source switches; a query never triggers a request.
class SubstackHomeControlsStore extends Store<SubstackHomeOptions> {
  SubstackHomeControlsStore() : super(const SubstackHomeOptions());
  SubstackLoadedOptions options(String slot) => state.feeds[slot] ?? const SubstackLoadedOptions();
  void configure(String slot, SubstackLoadedOptions options) => update(
    SubstackHomeOptions(
      feeds: {...state.feeds, slot: options},
      librarySection: state.librarySection,
      libraryQuery: state.libraryQuery,
    ),
  );
  void library({int? section, String? query}) => update(
    SubstackHomeOptions(
      feeds: state.feeds,
      librarySection: section ?? state.librarySection,
      libraryQuery: query ?? state.libraryQuery,
    ),
  );
}

List<SubstackPost> filterSubstackLoaded(List<SubstackPost> posts, SubstackLoadedOptions options) {
  final terms = options.query.trim().toLowerCase().split(RegExp(r'\s+')).where((term) => term.isNotEmpty).toList();
  final result = posts.where((post) {
    final text = '${post.title} ${post.excerpt ?? ''} ${post.publicationName} ${post.authorName ?? ''}'.toLowerCase();
    return terms.every(text.contains);
  }).toList();
  final positions = {for (var i = 0; i < result.length; i++) result[i]: i};
  result.sort((a, b) {
    if (options.order == SubstackLoadedOrder.popular) {
      final popularity = (b.reactionCount ?? 0).compareTo(a.reactionCount ?? 0);
      if (popularity != 0) return popularity;
    }
    final left = a.publishedAt, right = b.publishedAt;
    if (left == null && right != null) return 1;
    if (left != null && right == null) return -1;
    final order = left == null || right == null ? 0 : left.compareTo(right);
    if (order == 0) return positions[a]!.compareTo(positions[b]!);
    return options.order == SubstackLoadedOrder.oldest ? order : -order;
  });
  return result;
}

class SubstackLoadedControls extends StatefulWidget {
  final String slot;
  final bool autofocus;
  const SubstackLoadedControls({super.key, required this.slot, this.autofocus = false});
  @override
  State<SubstackLoadedControls> createState() => _SubstackLoadedControlsState();
}

class _SubstackLoadedControlsState extends State<SubstackLoadedControls> {
  late final TextEditingController _query;
  @override
  void initState() {
    super.initState();
    _query = TextEditingController(text: context.read<SubstackHomeControlsStore>().options(widget.slot).query);
  }

  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final store = context.read<SubstackHomeControlsStore>();
    final options = store.options(widget.slot);
    if (_query.text != options.query) _query.text = options.query;
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _query,
              autofocus: widget.autofocus,
              onChanged: (query) => store.configure(widget.slot, options.copy(query: query)),
              decoration: InputDecoration(
                hintText: l10n.plugin_mastodon_loaded_search,
                prefixIcon: const Icon(Icons.search),
                isDense: true,
                suffixIcon: options.query.isEmpty
                    ? null
                    : IconButton(
                        tooltip: l10n.plugin_mastodon_clear_search,
                        onPressed: () => store.configure(widget.slot, options.copy(query: '')),
                        icon: const Icon(Icons.clear),
                      ),
              ),
            ),
          ),
          PopupMenuButton<SubstackLoadedOrder>(
            tooltip: l10n.plugin_mastodon_sort,
            initialValue: options.order,
            icon: const Icon(Icons.sort),
            onSelected: (order) => store.configure(widget.slot, options.copy(order: order)),
            itemBuilder: (_) => [
              for (final order in SubstackLoadedOrder.values)
                CheckedPopupMenuItem(
                  value: order,
                  checked: options.order == order,
                  child: Text(switch (order) {
                    SubstackLoadedOrder.newest => l10n.plugin_mastodon_order_newest,
                    SubstackLoadedOrder.oldest => l10n.plugin_mastodon_order_oldest,
                    SubstackLoadedOrder.popular => l10n.plugin_pixiv_search_sort_popular,
                  }),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
