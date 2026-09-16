import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/utils/local_json_store.dart';

class FeedSnapshotCache {
  final JsonStore storage;
  FeedSnapshotCache({JsonStore? storage}) : storage = storage ?? LocalJsonStore.shared;
  String key(String source, List<String> ids) =>
      'feed:$source:${sha256.convert(utf8.encode(jsonEncode(ids.toList()..sort())))}';
  Future<({List<InterleavedItem> items, DateTime? at})> read(String key) async {
    final raw = await storage.read(key);
    if (raw is! Map || raw['posts'] is! List) return (items: const [], at: null);
    final at = DateTime.tryParse('${raw['at']}');
    if (at == null || DateTime.now().difference(at) > const Duration(days: 7)) return (items: const [], at: null);
    final items = <InterleavedItem>[];
    for (final row in (raw['posts'] as List).take(100)) {
      if (row is! Map) continue;
      final post = PluginLinkPost.fromArchive(row['snapshot']);
      final date = DateTime.tryParse('${row['date']}');
      if (post == null || date == null) continue;
      items.add(
        InterleavedItem(
          date: date,
          id: row['id'] is String ? row['id'] as String : null,
          source: post.source,
          linkUrl: row['link'] is String ? row['link'] as String : null,
          snapshot: post.archive.content,
          build: (_) => PluginLinkPostCard(post: post),
        ),
      );
    }
    return (items: items, at: at);
  }

  Future<void> write(String key, List<InterleavedItem> items) async {
    final posts = [
      for (final item in items.take(100))
        if (item.snapshot != null)
          {'id': item.id, 'date': item.date.toIso8601String(), 'link': item.linkUrl, 'snapshot': item.snapshot},
    ];
    try {
      await storage.write(key, {'at': DateTime.now().toIso8601String(), 'posts': posts});
      final snapshots = await storage.readPrefix('feed:');
      if (snapshots.length > 40) {
        final oldest = snapshots.keys.toList()
          ..sort((a, b) => '${(snapshots[a] as Map?)?['at']}'.compareTo('${(snapshots[b] as Map?)?['at']}'));
        for (final stale in oldest.take(snapshots.length - 40)) await storage.remove(stale);
      }
    } catch (_) {
      /* A cache write must not turn a successful read into an error. */
    }
  }
}
