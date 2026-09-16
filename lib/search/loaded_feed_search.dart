import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/conversation.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/utils/reader_value_store.dart';

class LoadedFeedEntry {
  final String text;
  final WidgetBuilder build;
  const LoadedFeedEntry(this.text, this.build);
  bool matches(String query) => query.toLowerCase().trim().split(RegExp(r'\s+')).every(text.toLowerCase().contains);
}

String loadedTweetText(TweetWithCard tweet, [int depth = 0]) => [
  tweet.fullText ?? '',
  tweet.user?.name ?? '',
  tweet.user?.screenName ?? '',
  for (final url in tweet.entities?.urls ?? []) ...[url.expandedUrl ?? '', url.displayUrl ?? ''],
  if (depth < 3 && tweet.retweetedStatusWithCard != null) loadedTweetText(tweet.retweetedStatusWithCard!, depth + 1),
  if (depth < 3 && tweet.quotedStatusWithCard != null) loadedTweetText(tweet.quotedStatusWithCard!, depth + 1),
].join('\n');

String loadedPluginText(Object? snapshot, [int depth = 0]) {
  if (depth > 8) return '';
  if (snapshot is String) return snapshot;
  if (snapshot is List) return snapshot.map((value) => loadedPluginText(value, depth + 1)).join('\n');
  if (snapshot is Map) return snapshot.values.map((value) => loadedPluginText(value, depth + 1)).join('\n');
  return '';
}

List<LoadedFeedEntry> loadedFeedEntries(List<TweetChain> chains, List<InterleavedItem> plugins, String? username) => [
  for (final chain in chains)
    LoadedFeedEntry(
      chain.tweets.map(loadedTweetText).join('\n'),
      (_) => TweetConversation(
        key: ValueKey(chain.id),
        id: chain.id,
        tweets: chain.tweets,
        username: username,
        isPinned: chain.isPinned,
      ),
    ),
  for (final item in plugins)
    LoadedFeedEntry('${item.linkUrl ?? ''}\n${item.source ?? ''}\n${loadedPluginText(item.snapshot)}', item.build),
];

Future<void> showLoadedFeedSearch(BuildContext context, List<LoadedFeedEntry> entries) => Navigator.push<void>(
  context,
  MaterialPageRoute(builder: (_) => LoadedFeedSearch(entries: List.unmodifiable(entries))),
);

/// Searches a captured snapshot; neither typing nor scrolling requests pages.
class LoadedFeedSearch extends StatefulWidget {
  final List<LoadedFeedEntry> entries;
  const LoadedFeedSearch({super.key, required this.entries});
  @override
  State<LoadedFeedSearch> createState() => _LoadedFeedSearchState();
}

class _LoadedFeedSearchState extends State<LoadedFeedSearch> {
  final _query = ReaderValueStore<String>('');
  @override
  void dispose() {
    _query.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(L10n.of(context).reader_search_loaded)),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            autofocus: true,
            onChanged: _query.update,
            decoration: InputDecoration(
              prefixIcon: const Icon(Icons.search),
              labelText: L10n.of(context).search,
              helperText: L10n.of(context).reader_search_loaded_hint,
              helperMaxLines: 3,
            ),
          ),
        ),
        Expanded(
          child: ScopedBuilder<ReaderValueStore<String>, String>(
            store: _query,
            onState: (context, query) {
              final matches = widget.entries.where((entry) => entry.matches(query)).toList();
              if (matches.isEmpty) return Center(child: Text(L10n.of(context).no_results));
              return TweetContextScope(
                child: ListView.builder(
                  keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                  itemCount: matches.length,
                  itemBuilder: (context, index) => matches[index].build(context),
                ),
              );
            },
          ),
        ),
      ],
    ),
  );
}
