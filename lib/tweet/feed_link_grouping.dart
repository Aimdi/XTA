import 'package:flutter/widgets.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/link_identity.dart';
import 'package:xta/tweet/link_stack.dart';

class _LinkEntry {
  final String key;
  final DateTime date;
  final String? link;
  final WidgetBuilder build;
  const _LinkEntry(this.key, this.date, this.link, this.build);
}

class FeedLinkGrouping {
  final Map<String, WidgetBuilder> chains;
  final List<InterleavedItem> plugins;
  final bool hasGroups;
  const FeedLinkGrouping(this.chains, this.plugins, this.hasGroups);

  factory FeedLinkGrouping.build(
    List<TweetChain> chains,
    List<InterleavedItem> plugins,
    Widget Function(BuildContext, TweetChain) buildChain,
  ) {
    final entries = [
      for (final chain in chains)
        _LinkEntry(
          'x:${chain.id}',
          newestDateOf(chain) ?? DateTime(0),
          _chainLink(chain),
          (context) => buildChain(context, chain),
        ),
      for (var i = 0; i < plugins.length; i++)
        _LinkEntry('plugin:$i', plugins[i].date, plugins[i].linkUrl, plugins[i].build),
    ]..sort((a, b) => b.date.compareTo(a.date));
    final replacements = <String, WidgetBuilder>{};
    final groups = repeatedLinks(entries, (entry) => entry.link);
    for (final group in groups.entries) {
      final posts = List<WidgetBuilder>.unmodifiable(group.value.map((entry) => entry.build));
      replacements[group.value.first.key] = (_) => LinkStack(key: ValueKey(group.key), posts: posts);
      for (final hidden in group.value.skip(1)) {
        replacements[hidden.key] = (_) => const SizedBox.shrink();
      }
    }
    return FeedLinkGrouping(
      {
        for (final chain in chains)
          if (replacements['x:${chain.id}'] case final build?) chain.id: build,
      },
      [for (var i = 0; i < plugins.length; i++) plugins[i].withBuilder(replacements['plugin:$i'] ?? plugins[i].build)],
      groups.isNotEmpty,
    );
  }
}

String? _chainLink(TweetChain chain) {
  // A conversation can discuss several links; keep it separate instead of hiding unrelated replies.
  if (chain.tweets.length != 1) return null;
  final outer = chain.tweets.single;
  final tweet = outer.retweetedStatusWithCard ?? outer;
  final links = <String, String>{};
  for (final url in tweet.entities?.urls ?? const []) {
    final canonical = canonicalArticleLink(url.expandedUrl);
    if (canonical != null) links[canonical] = url.expandedUrl!;
  }
  return links.length == 1 ? links.values.single : null;
}
