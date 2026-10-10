import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/utils/json.dart';
import 'package:xta/utils/local_json_store.dart';

/// Where the opened illustrations are kept; novels get a key of their own.
const pixivIllustHistoryKey = 'pixiv-history:illusts';

/// Where the opened novels are kept.
const pixivNovelHistoryKey = 'pixiv-history:novels';

/// The most works a history keeps; the oldest fall off first.
const pixivHistoryLimit = 500;

/// One opened work as the history remembers it.
class PixivHistoryEntry {
  final int id;
  final String title;
  final int userId;
  final String userName;
  final String thumbUrl;

  /// Tag names, so a muted tag still gates a work reopened from history.
  final List<String> tags;
  final DateTime viewedAt;

  /// The image's size and bookmarks when it was opened, for the tile.
  final int width;
  final int height;
  final int bookmarks;
  final bool bookmarked;

  const PixivHistoryEntry({
    required this.id,
    required this.title,
    required this.userId,
    required this.userName,
    required this.thumbUrl,
    required this.viewedAt,
    this.tags = const [],
    this.width = 0,
    this.height = 0,
    this.bookmarks = 0,
    this.bookmarked = false,
  });

  factory PixivHistoryEntry.of(PixivIllust illust, DateTime viewedAt) => PixivHistoryEntry(
    id: illust.id,
    title: illust.title,
    userId: illust.userId,
    userName: illust.userName,
    thumbUrl: illust.thumbnailUrl,
    tags: [for (final tag in illust.tags) tag.name],
    viewedAt: viewedAt,
    width: illust.width,
    height: illust.height,
    bookmarks: illust.totalBookmarks,
    bookmarked: illust.isBookmarked,
  );

  /// A novel as its history row shows it: the cover stands in for the thumbnail.
  factory PixivHistoryEntry.ofNovel(PixivNovel novel, DateTime viewedAt) => PixivHistoryEntry(
    id: novel.id,
    title: novel.title,
    userId: novel.user.id,
    userName: novel.user.name,
    thumbUrl: novel.coverUrl ?? '',
    tags: [for (final tag in novel.tags) tag.name],
    viewedAt: viewedAt,
    bookmarks: novel.totalBookmarks,
    bookmarked: novel.isBookmarked,
  );

  /// A stored entry, or null when it lacks the id or the thumbnail to show it.
  static PixivHistoryEntry? fromJson(Json json) {
    final id = json['id'].integer;
    final thumb = json['thumbUrl'].string ?? '';
    if (id == null || id <= 0 || thumb.isEmpty) {
      return null;
    }
    return PixivHistoryEntry(
      id: id,
      title: json['title'].string ?? '',
      userId: json['userId'].integer ?? 0,
      userName: json['userName'].string ?? '',
      thumbUrl: thumb,
      tags: [for (final tag in json['tags'].list) ?tag.string],
      viewedAt: DateTime.tryParse(json['viewedAt'].string ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      width: json['width'].integer ?? 0,
      height: json['height'].integer ?? 0,
      bookmarks: json['bookmarks'].integer ?? 0,
      bookmarked: json['bookmarked'].boolean == true,
    );
  }

  Map<String, Object> toJson() => {
    'id': id,
    'title': title,
    'userId': userId,
    'userName': userName,
    'thumbUrl': thumbUrl,
    'tags': tags,
    'viewedAt': viewedAt.toUtc().toIso8601String(),
    'width': width,
    'height': height,
    'bookmarks': bookmarks,
    'bookmarked': bookmarked,
  };

  /// Whether the title or the author's name contains [query], ignoring case.
  bool matches(String query) {
    final needle = query.trim().toLowerCase();
    return needle.isEmpty || title.toLowerCase().contains(needle) || userName.toLowerCase().contains(needle);
  }

  /// Enough of the work to open its detail at once; the detail fetches the rest.
  PixivIllust toIllust() => PixivIllust(
    id: id,
    title: title,
    caption: '',
    type: 'illust',
    thumbnailUrl: thumbUrl,
    pageCount: 1,
    userId: userId,
    userName: userName,
    userAccount: '',
    tags: [for (final tag in tags) PixivTag(name: tag)],
    width: width,
    height: height,
    totalBookmarks: bookmarks,
    isBookmarked: bookmarked,
  );
}

/// [entries] with [entry] on top: newest first, each work once, at most [limit].
List<PixivHistoryEntry> pixivHistoryWith(
  List<PixivHistoryEntry> entries,
  PixivHistoryEntry entry, {
  int limit = pixivHistoryLimit,
}) => List.unmodifiable([entry, ...entries.where((old) => old.id != entry.id)].take(limit));

/// The entries whose title or author contains [query].
List<PixivHistoryEntry> pixivHistoryFiltered(List<PixivHistoryEntry> entries, String query) => query.trim().isEmpty
    ? entries
    : [
        for (final entry in entries)
          if (entry.matches(query)) entry,
      ];

List<PixivHistoryEntry> _decodeHistory(Object? stored) {
  final ids = <int>{};
  return List.unmodifiable(
    [
      for (final item in Json(stored).list)
        if (PixivHistoryEntry.fromJson(item) case final entry? when ids.add(entry.id)) entry,
    ].take(pixivHistoryLimit),
  );
}

/// The works opened on this device, newest first, kept in an on-device JSON
/// file rather than preferences, so settings backups never carry them.
class PixivHistoryStore extends Store<List<PixivHistoryEntry>> {
  final JsonStore storage;
  final String key;
  Future<void>? _loaded;

  PixivHistoryStore({JsonStore? storage, this.key = pixivIllustHistoryKey})
    : storage = storage ?? LocalJsonStore.shared,
      super(const []);

  /// Reads the file once; later calls wait for that first read.
  Future<void> load() => _loaded ??= execute(() async => _decodeHistory(await storage.read(key)));

  Future<void> record(PixivHistoryEntry entry) => _change((entries) => pixivHistoryWith(entries, entry));

  Future<void> remove(int id) => _change((entries) => List.unmodifiable(entries.where((entry) => entry.id != id)));

  Future<void> clear() => _change((_) => const []);

  Future<void> _change(List<PixivHistoryEntry> Function(List<PixivHistoryEntry>) change) async {
    await load();
    final next = change(state);
    update(next);
    try {
      await storage.write(key, [for (final entry in next) entry.toJson()]);
    } on Exception {
      // The list on screen is still right; the next change writes it again.
    }
  }
}

/// The novels opened on this device, in a file of their own; a type of its
/// own too, so both histories can be provided side by side.
class PixivNovelHistoryStore extends PixivHistoryStore {
  PixivNovelHistoryStore({super.storage}) : super(key: pixivNovelHistoryKey);
}

/// Adds [illust] to the history unless the reader paused it or no history is
/// provided (a test, or a screen outside the app).
void recordPixivVisit(BuildContext context, PixivIllust illust) =>
    _record(context, context.read<PixivHistoryStore?>(), PixivHistoryEntry.of(illust, DateTime.now()));

/// Adds [novel] to the novel history, under the same conditions as works.
void recordPixivNovelVisit(BuildContext context, PixivNovel novel) =>
    _record(context, context.read<PixivNovelHistoryStore?>(), PixivHistoryEntry.ofNovel(novel, DateTime.now()));

void _record(BuildContext context, PixivHistoryStore? history, PixivHistoryEntry entry) {
  final paused = PrefService.of(context, listen: false).get<bool>(optionPluginPixivHistoryPaused) == true;
  if (history == null || paused) return;
  unawaited(history.record(entry));
}
