import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_reader_screen.dart';
import 'package:xta/reading/reading_history_entry.dart';

ReadingHistoryEntry rssHistoryEntry(RssItem item, {ReadingHistoryKind kind = ReadingHistoryKind.article}) =>
    ReadingHistoryEntry(
      source: pluginIdRss,
      kind: kind,
      nativeId: item.id,
      url: item.link,
      author: item.author ?? item.feedTitle,
      title: item.title,
      text: item.excerpt ?? '',
      extra: {'feedId': item.feedId, 'feedTitle': item.feedTitle},
    );

/// Opens the reader on what history kept; an article saved offline is found by the same item id.
Future<void> openRssHistoryEntry(BuildContext context, ReadingHistoryEntry entry) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => RssReaderScreen(
      item: RssItem(
        id: entry.nativeId,
        title: entry.title,
        feedId: entry.extra['feedId'] ?? '',
        feedTitle: entry.extra['feedTitle'] ?? entry.author,
        link: entry.url,
        excerpt: entry.text.isEmpty ? null : entry.text,
      ),
    ),
  ),
);
