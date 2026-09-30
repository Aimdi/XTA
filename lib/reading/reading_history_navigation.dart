import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_history.dart';
import 'package:xta/plugins/mastodon/mastodon_history.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/reddit/reddit_history.dart';
import 'package:xta/plugins/rss/rss_history.dart';
import 'package:xta/plugins/substack/substack_history.dart';
import 'package:xta/plugins/threads/threads_history.dart';
import 'package:xta/reading/reading_history_entry.dart';
import 'package:xta/tweet/article_screen.dart';
import 'package:xta/tweet/tweet_history.dart';
import 'package:xta/utils/urls.dart';

const readingHistoryWebSource = 'web';

typedef ReadingHistoryOpener = Future<void> Function(BuildContext context, ReadingHistoryEntry entry);

ReadingHistoryEntry webHistoryEntry({required String url, String? title, String? text}) => ReadingHistoryEntry(
  source: readingHistoryWebSource,
  kind: ReadingHistoryKind.article,
  nativeId: readingHistoryUrl(url) ?? url,
  url: url,
  title: title ?? '',
  text: text ?? '',
);

Future<void> _openWeb(BuildContext context, ReadingHistoryEntry entry) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => ArticleScreen(url: entry.url ?? entry.nativeId, title: entry.title.isEmpty ? null : entry.title),
  ),
);

final Map<String, ReadingHistoryOpener> _openers = {
  'x': openTweetHistoryEntry,
  'mastodon': openMastodonHistoryEntry,
  'bluesky': openBlueskyHistoryEntry,
  'threads': openThreadsHistoryEntry,
  'reddit': openRedditHistoryEntry,
  'rss': openRssHistoryEntry,
  'substack': openSubstackHistoryEntry,
  readingHistoryWebSource: _openWeb,
};

/// Reopens an entry through its source's own screen, else its public link.
Future<void> openReadingHistoryEntry(BuildContext context, ReadingHistoryEntry entry) async {
  final opener = _openers[entry.source];
  if (opener != null) return opener(context, entry);
  final url = entry.url;
  if (url != null) return openUri(context, url);
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).history_open_failed)));
}

String readingHistorySourceLabel(BuildContext context, String source) => source == readingHistoryWebSource
    ? L10n.of(context).history_source_web
    : pluginById(source)?.title(context) ?? source;
