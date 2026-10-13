import 'dart:async';
import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_feed_cache.dart';
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

/// Why a feed's latest refresh failed while its posts from before stay
/// shown; a null error otherwise. A record, because a store's state cannot
/// be updated to null.
class BooruRefreshFailureStore extends Store<({Object? error})> {
  BooruRefreshFailureStore() : super((error: null));
}

/// Where a feed's shown posts stand, for a failed refresh to go back to.
typedef _Shown = ({int page, bool hasMore, BooruPaging paging});

/// The first page with posts, its number, and every page asked for on the
/// way to it.
typedef _FirstPage = ({
  BooruPostPage page,
  int number,
  List<BooruPostPage> pages,
});

/// How many of a feed's posts the blacklist hid, and whether the reader
/// asked to see them anyway.
class BooruHiddenStore extends Store<({int count, bool shown})> {
  BooruHiddenStore() : super((count: 0, shown: false));
}

/// Paginated post list for Latest / Search / Following.
class BooruFeedStore extends Store<List<BooruPost>>
    with BooruClosableStore<List<BooruPost>> {
  final BooruClient client;
  final Future<BooruPostPage> Function({required int page}) loader;

  /// Where the first page is kept on the device, so that a launch without
  /// a network still has the last one to show; [cacheKey] names what the
  /// feed asks for besides the site and filters. Nothing is kept without
  /// both.
  final BooruFeedCache? cache;
  final String Function()? cacheKey;

  /// Where loading the next page stands; the grid's footer shows it.
  final paging = BooruPagingStore();

  /// How many queries behind the shown first page failed (Following).
  final failedQueries = BooruFailedQueriesStore();

  /// Why the latest refresh failed while the posts from before stay shown.
  final refreshFailure = BooruRefreshFailureStore();

  /// The posts the blacklist hid from the pages loaded since the last
  /// refresh; [showHidden] puts them back in their places.
  final hidden = BooruHiddenStore();
  final _muted = <BooruPost>[];
  final _order = <String, int>{};
  var _showMuted = false;

  var _page = 0;
  var _hasMore = true;
  String? _loadedFor;

  /// Bumped by every refresh; a load started before the latest refresh is
  /// dropped when it returns. [_landed] is the last one whose answer is in.
  var _generation = 0;
  var _landed = 0;

  /// Starts out loading: until the first page has been asked for and
  /// answered, an empty list means "not loaded yet", not "no posts".
  BooruFeedStore(this.client, this.loader, {this.cache, this.cacheKey})
    : super(const []) {
    setLoading(true);
  }

  bool get hasMore => _hasMore;

  /// Whether the site, rating cap or blacklist changed since the feed loaded.
  bool get isStale => _loadedFor != null && _loadedFor != client.feedKey;

  /// Loads the feed when it is empty or stale.
  Future<void> ensureFresh() async {
    if (state.isEmpty || isStale) await refresh();
  }

  /// Loads the first page again. If that fails while this feed's posts are
  /// shown, they stay and [refreshFailure] says why. When [changed], the
  /// feed asks for something else now (another day, other tags), so the
  /// posts shown no longer answer it and a failure is the feed's error.
  Future<void> refresh({bool changed = false}) async {
    if (closed) return;
    final generation = ++_generation;
    final shown = changed ? null : _shown();
    final cacheId = _cacheId();
    _page = 0;
    _hasMore = true;
    _loadedFor = client.feedKey;
    _setPaging(BooruPaging.idle);
    await execute(() async {
      final _FirstPage first;
      try {
        first = await _firstPage();
      } catch (error) {
        if (!_isCurrent(generation)) rethrow;
        if (shown != null) {
          _keep(generation, shown, error);
          return state;
        }
        final kept = cacheId == null ? null : await cache?.read(cacheId);
        if (!_isCurrent(generation)) rethrow;
        if (kept == null) {
          _land(generation, failed: 0);
          rethrow;
        }
        _keep(generation, _shownOf(kept), error);
        return kept.posts;
      }
      // Overtaken by a newer refresh: what is shown stays until that lands.
      if (!_isCurrent(generation)) return state;
      _page = first.number;
      _hasMore = first.page.hasMore;
      _setPaging(_hasMore ? BooruPaging.idle : BooruPaging.end);
      _land(generation, failed: first.page.failedQueries);
      if (cacheId != null && first.page.posts.isNotEmpty) {
        unawaited(cache?.write(cacheId, _pageOf(first)));
      }
      _forgetHidden();
      return _take(first.pages, known: {});
    }, delay: generation == 1 ? Duration.zero : _debounce);
  }

  /// How long a refresh waits for a newer one before it asks. The first
  /// has nothing to wait for.
  static const _debounce = Duration(milliseconds: 50);

  /// The first page with posts: the rating and mute filters can empty a raw
  /// page, so a few are skipped while the host still has more. [pages] holds
  /// every page asked for, for what the blacklist hid on them.
  Future<_FirstPage> _firstPage() async {
    var page = const BooruPostPage(posts: [], page: 0, hasMore: true);
    final pages = <BooruPostPage>[];
    while (!closed && pages.length < 4 && page.hasMore && page.posts.isEmpty) {
      page = await loader(page: pages.length + 1);
      pages.add(page);
    }
    return (page: page, number: pages.length, pages: pages);
  }

  void _land(int generation, {required int failed}) {
    _landed = generation;
    failedQueries.update(failed);
    refreshFailure.update((error: null));
  }

  /// Null when none of this feed's posts are shown. A next page on its way
  /// is dropped by the refresh, so the footer goes back to idle, not to
  /// loading.
  _Shown? _shown() => state.isEmpty || isStale
      ? null
      : (
          page: _page,
          hasMore: _hasMore,
          paging: paging.state == BooruPaging.loading
              ? BooruPaging.idle
              : paging.state,
        );

  /// What the feed's copy on the device is kept under: its own key with the
  /// site and filters.
  String? _cacheId() => switch ((cache, cacheKey)) {
    (_?, final key?) => '${key()}\n${client.feedKey}',
    _ => null,
  };

  static BooruPostPage _pageOf(_FirstPage first) => BooruPostPage(
    posts: first.page.posts,
    page: first.number,
    hasMore: first.page.hasMore,
  );

  static _Shown _shownOf(BooruPostPage kept) => (
    page: kept.page,
    hasMore: kept.hasMore,
    paging: kept.hasMore ? BooruPaging.idle : BooruPaging.end,
  );

  /// Goes back to the posts [shown] before a refresh that failed with
  /// [error].
  void _keep(int generation, _Shown shown, Object error) {
    _landed = generation;
    _page = shown.page;
    _hasMore = shown.hasMore;
    _setPaging(shown.paging);
    refreshFailure.update((error: error));
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
      final fresh = _take([page], known: {..._shownKeys(), ..._mutedKeys()});
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
    await refreshFailure.destroy();
    await hidden.destroy();
  }

  /// Puts the posts the blacklist hid back in their places.
  void showHidden() {
    if (closed || _showMuted) return;
    _showMuted = true;
    update([...state, ..._muted]..sort(_hostOrder));
    _publishHidden();
  }

  /// Hides the blacklisted posts [showHidden] put back.
  void hideHidden() {
    if (closed || !_showMuted) return;
    _showMuted = false;
    final muted = _mutedKeys();
    update([
      for (final post in state)
        if (!muted.contains(post.key)) post,
    ]);
    _publishHidden();
  }

  /// The posts of [pages] to show, in the host's order, noting those the
  /// blacklist hid. Posts [known] already, shown or hidden, are skipped: new
  /// uploads shift a host's pages, so a page can repeat earlier posts.
  List<BooruPost> _take(
    Iterable<BooruPostPage> pages, {
    required Set<String> known,
  }) {
    final fresh = [
      for (final page in pages)
        for (final post in page.allowed)
          if (known.add(post.key)) post,
    ];
    final visible = {
      for (final page in pages)
        for (final post in page.posts) post.key,
    };
    for (final post in fresh) {
      _order.putIfAbsent(post.key, () => _order.length);
    }
    _muted.addAll(fresh.where((post) => !visible.contains(post.key)));
    _publishHidden();
    return _showMuted
        ? fresh
        : [
            for (final post in fresh)
              if (visible.contains(post.key)) post,
          ];
  }

  /// A refresh starts over, with what the blacklist hid hidden again.
  void _forgetHidden() {
    _muted.clear();
    _order.clear();
    _showMuted = false;
  }

  void _publishHidden() {
    if (!closed) hidden.update((count: _muted.length, shown: _showMuted));
  }

  Set<String> _shownKeys() => {for (final post in state) post.key};

  Set<String> _mutedKeys() => {for (final post in _muted) post.key};

  int _hostOrder(BooruPost a, BooruPost b) =>
      (_order[a.key] ?? 0).compareTo(_order[b.key] ?? 0);
}
