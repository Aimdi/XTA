import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:sqflite/sqflite.dart';

/// Followed tags and saved searches (a followed entry may hold several tags),
/// stored so they can be group members.
class BooruTagsStore extends Store<List<String>> {
  BooruTagsStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<String>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableBooruSubscription,
      orderBy: 'name COLLATE NOCASE',
    );
    return rows.map((e) => e['name'] as String).toList(growable: false);
  }

  // Writes happen before execute(): it drops a call superseded within its
  // debounce window, which would silently lose the write with it.
  Future<void> add(String tag) async {
    await _write(tag);
    await load();
  }

  Future<void> remove(String tag) async {
    final id = normaliseBooruQuery(tag);
    if (id != null) {
      final database = await Repository.writable();
      await database.delete(
        tableBooruSubscription,
        where: 'id = ?',
        whereArgs: [id],
      );
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'profile_id = ?',
        whereArgs: [id],
      );
    }
    await load();
  }

  Future<void> _write(String tag) async {
    final normalised = normaliseBooruQuery(tag);
    if (normalised == null) return;

    final database = await Repository.writable();
    await database.insert(
      tableBooruSubscription,
      BooruSubscription(
        id: normalised,
        name: normalised,
        createdAt: DateTime.now(),
        inFeed: true,
      ).toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }
}

/// Local blacklist — preference-backed, like Pixiv muted tags. An entry is a
/// tag or several tags that hide a post only together.
class BooruMuteStore extends Store<Set<String>> {
  final BasePrefService prefs;

  BooruMuteStore(this.prefs) : super(const {});

  Future<void> load() async {
    await execute(
      () async =>
          parseBooruBlacklist(prefs.get<String>(optionPluginBooruMutedTags)),
    );
  }

  Future<void> _persist(Set<String> tags) async {
    final sorted = tags.toList()..sort();
    await prefs.set(optionPluginBooruMutedTags, jsonEncode(sorted));
    update(tags);
  }

  Future<void> mute(String tag) async {
    final normalised = normaliseBooruQuery(tag);
    if (normalised == null) return;
    await _persist({...state, normalised});
  }

  Future<void> unmute(String tag) async {
    final normalised = normaliseBooruQuery(tag);
    if (normalised == null) return;
    await _persist({
      for (final t in state)
        if (t != normalised) t,
    });
  }
}

/// The Following tab's single page. Followed tags that failed to load are
/// counted in [failed]; when every one failed, the first failure is thrown,
/// so the tab shows it with Retry rather than an empty feed.
Future<BooruPostPage> loadBooruFollowing(
  BooruClient client,
  List<String> tags,
  Store<int> failed, {
  required int page,
}) async {
  if (page > 1) {
    return BooruPostPage(posts: const [], page: page, hasMore: false);
  }
  final followed = await client.followedPosts(tags);
  final allFailed = followed.asked > 0 && followed.failed == followed.asked;
  failed.update(allFailed ? 0 : followed.failed);
  if (allFailed) throw followed.error!;
  return BooruPostPage(posts: followed.posts, page: 1, hasMore: false);
}

/// Where loading a feed's next page stands, for the footer under its grid.
enum BooruPaging { idle, loading, failed, end }

class BooruPagingStore extends Store<BooruPaging> {
  BooruPagingStore() : super(BooruPaging.idle);
}

/// Paginated post list for Latest / Search / Following.
class BooruFeedStore extends Store<List<BooruPost>> {
  final BooruClient client;
  final Future<BooruPostPage> Function({required int page}) loader;

  /// Where loading the next page stands; the grid's footer shows it.
  final paging = BooruPagingStore();

  var _page = 0;
  var _hasMore = true;
  var _closed = false;
  String? _loadedFor;

  /// Starts out loading: until the first page has been asked for and
  /// answered, an empty list means "not loaded yet", not "no posts".
  BooruFeedStore(this.client, this.loader) : super(const []) {
    setLoading(true);
  }

  bool get hasMore => _hasMore;

  /// Whether the site, rating cap or blacklist changed since the feed loaded.
  bool get isStale => _loadedFor != null && _loadedFor != client.feedKey;

  /// Loads the feed when it is empty or stale.
  Future<void> ensureFresh() async {
    if (state.isEmpty || isStale) await refresh();
  }

  Future<void> refresh() async {
    _page = 0;
    _hasMore = true;
    _loadedFor = client.feedKey;
    _setPaging(BooruPaging.idle);
    await execute(() async {
      // Rating / mute filters can zero the first raw page. Skip a few so Latest
      // does not open empty while the host still has posts.
      var posts = <BooruPost>[];
      var pageNo = 0;
      var hasMore = true;
      for (
        var attempt = 0;
        attempt < 4 && hasMore && posts.isEmpty;
        attempt++
      ) {
        pageNo += 1;
        final page = await loader(page: pageNo);
        posts = page.posts;
        hasMore = page.hasMore;
      }
      _page = pageNo;
      _hasMore = hasMore;
      _setPaging(hasMore ? BooruPaging.idle : BooruPaging.end);
      return posts;
    });
  }

  /// The next page; a stale feed starts over from the first page instead,
  /// so another site's posts are never appended to it. After a failure it
  /// waits for [retryMore], however often the grid asks.
  Future<void> loadMore() async {
    if (!_hasMore || paging.state != BooruPaging.idle) return;
    if (isStale) return refresh();
    _setPaging(BooruPaging.loading);
    try {
      await _appendNextPage();
      _setPaging(_hasMore ? BooruPaging.idle : BooruPaging.end);
    } catch (_) {
      _setPaging(BooruPaging.failed);
    }
  }

  /// Asks again for the page that failed.
  Future<void> retryMore() async {
    if (paging.state != BooruPaging.failed) return;
    _setPaging(BooruPaging.idle);
    await loadMore();
  }

  /// Appends the next page with posts not shown yet, skipping pages the
  /// filters emptied while the host still has more.
  Future<void> _appendNextPage() async {
    var repeats = 0;
    for (var attempt = 0; attempt < 3 && _hasMore; attempt++) {
      final next = _page + 1;
      final page = await loader(page: next);
      if (_closed) return;
      _page = next;
      _hasMore = page.hasMore;
      final fresh = _unseen(page.posts);
      if (fresh.isNotEmpty) {
        update([...state, ...fresh]);
        return;
      }
      if (page.posts.isNotEmpty) repeats++;
    }
    // A host that ignores the page number sends the same posts forever.
    if (repeats == 3) _hasMore = false;
  }

  void _setPaging(BooruPaging value) {
    if (!_closed) paging.update(value);
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    await paging.destroy();
    await super.destroy();
  }

  /// New uploads shift a host's pages, so a page can repeat posts already
  /// shown.
  List<BooruPost> _unseen(List<BooruPost> posts) {
    final seen = {for (final post in state) post.key};
    return [
      for (final post in posts)
        if (seen.add(post.key)) post,
    ];
  }
}
