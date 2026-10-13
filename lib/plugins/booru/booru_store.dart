import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_load_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:sqflite/sqflite.dart';

/// A followed tag's id as a group member. The prefix keeps a subreddit or saved
/// search called `art` a different member from the tag `art`.
String booruMemberId(String tag) => '$pluginIdBooru:$tag';

/// The tag a group member id names. Ids without the prefix are tags already.
String booruMemberTag(String id) {
  const prefix = '$pluginIdBooru:';
  return id.startsWith(prefix) ? id.substring(prefix.length) : id;
}

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
        whereArgs: [booruMemberId(id)],
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

/// The Following tab's single page, counting the followed tags that failed
/// to load. When every one failed, the first failure is thrown, so the tab
/// shows it with Retry rather than an empty feed.
Future<BooruPostPage> loadBooruFollowing(
  BooruClient client,
  List<String> tags, {
  required int page,
}) async {
  if (page > 1) {
    return BooruPostPage(posts: const [], page: page, hasMore: false);
  }
  final followed = await client.followedPosts(tags);
  if (followed.asked > 0 && followed.failed == followed.asked) {
    throw followed.error!;
  }
  return BooruPostPage(
    posts: followed.posts,
    page: 1,
    hasMore: false,
    failedQueries: followed.failed,
  );
}

/// Where loading a feed's next page stands, for the footer under its grid.
enum BooruPaging { idle, loading, failed, end }

class BooruPagingStore extends Store<BooruPaging> {
  BooruPagingStore() : super(BooruPaging.idle);
}

/// How many of the queries behind a feed's first page failed to load.
class BooruFailedQueriesStore extends Store<int> {
  BooruFailedQueriesStore() : super(0);
}

/// Paginated post list for Latest / Search / Following.
class BooruFeedStore extends Store<List<BooruPost>>
    with BooruClosableStore<List<BooruPost>> {
  final BooruClient client;
  final Future<BooruPostPage> Function({required int page}) loader;

  /// Where loading the next page stands; the grid's footer shows it.
  final paging = BooruPagingStore();

  /// How many queries behind the shown first page failed (Following).
  final failedQueries = BooruFailedQueriesStore();

  var _page = 0;
  var _hasMore = true;
  String? _loadedFor;

  /// Bumped by every refresh; a load started before the latest refresh is
  /// dropped when it returns. [_landed] is the last one whose answer is in.
  var _generation = 0;
  var _landed = 0;

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
    if (closed) return;
    final generation = ++_generation;
    _page = 0;
    _hasMore = true;
    _loadedFor = client.feedKey;
    _setPaging(BooruPaging.idle);
    await execute(() async {
      final ({BooruPostPage page, int number}) first;
      try {
        first = await _firstPage();
      } catch (_) {
        if (_isCurrent(generation)) _land(generation, failed: 0);
        rethrow;
      }
      // Overtaken by a newer refresh: what is shown stays until that lands.
      if (!_isCurrent(generation)) return state;
      _page = first.number;
      _hasMore = first.page.hasMore;
      _setPaging(_hasMore ? BooruPaging.idle : BooruPaging.end);
      _land(generation, failed: first.page.failedQueries);
      return first.page.posts;
    });
  }

  /// The first page with posts: the rating and mute filters can empty a raw
  /// page, so a few are skipped while the host still has more.
  Future<({BooruPostPage page, int number})> _firstPage() async {
    var page = const BooruPostPage(posts: [], page: 0, hasMore: true);
    var number = 0;
    while (!closed && number < 4 && page.hasMore && page.posts.isEmpty) {
      number += 1;
      page = await loader(page: number);
    }
    return (page: page, number: number);
  }

  void _land(int generation, {required int failed}) {
    _landed = generation;
    failedQueries.update(failed);
  }

  bool _isCurrent(int generation) => !closed && generation == _generation;

  /// The next page; a stale feed starts over from the first page instead,
  /// so another site's posts are never appended to it. After a failure it
  /// waits for [retryMore], however often the grid asks, and while a refresh
  /// is on its way it waits for that.
  Future<void> loadMore() async {
    final generation = _generation;
    if (closed || _landed != generation) return;
    if (!_hasMore || paging.state != BooruPaging.idle) return;
    if (isStale) return refresh();
    _setPaging(BooruPaging.loading);
    try {
      await _appendNextPage(generation);
      if (_isCurrent(generation)) {
        _setPaging(_hasMore ? BooruPaging.idle : BooruPaging.end);
      }
    } catch (_) {
      if (_isCurrent(generation)) _setPaging(BooruPaging.failed);
    }
  }

  /// Asks again for the page that failed.
  Future<void> retryMore() async {
    if (paging.state != BooruPaging.failed) return;
    _setPaging(BooruPaging.idle);
    await loadMore();
  }

  /// Appends the next page with posts not shown yet, skipping pages the
  /// filters emptied while the host still has more. Dropped if a refresh
  /// started meanwhile.
  Future<void> _appendNextPage(int generation) async {
    var repeats = 0;
    for (var attempt = 0; attempt < 3 && _hasMore; attempt++) {
      final next = _page + 1;
      final page = await loader(page: next);
      if (!_isCurrent(generation)) return;
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
    if (!closed) paging.update(value);
  }

  @override
  Future<void> destroy() async {
    await super.destroy();
    await paging.destroy();
    await failedQueries.destroy();
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
