import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';

enum SubstackSearchTab { publications, posts }

const substackSearchHistoryKey = 'plugin.substack.search_history';
const _searchTimeout = Duration(seconds: 30);
const _postPageSize = 20;

String substackSearchPublicationKey(SubstackPublication publication) =>
    '${publication.id}\n${publication.baseUrl.toLowerCase()}';
String substackSearchPostKey(SubstackPost post) =>
    '${post.publicationBaseUrl.toLowerCase()}\n${post.id.isEmpty ? post.slug : post.id}';

class SubstackSearchSlice {
  final SubstackPublication publication;
  final List<SubstackPost> posts;
  final int offset;
  final bool hasMore;
  final Object? error;
  const SubstackSearchSlice(
    this.publication, {
    this.posts = const [],
    this.offset = 0,
    this.hasMore = true,
    this.error,
  });
}

class SubstackSearchState {
  final String query;
  final SubstackSearchTab tab;
  final SubstackCategory? category;
  final List<SubstackCategory> categories;
  final bool categoriesLoading;
  final Object? categoriesError;
  final List<SubstackPublication> publications;
  final List<SubstackPost> posts;
  final List<SubstackSearchSlice> slices;
  final bool loading;
  final bool loadingMore;
  final bool hasMore;
  final int nextPage;
  final Object? error;
  final bool retryMore;
  final bool direct;
  const SubstackSearchState({
    this.query = '',
    this.tab = SubstackSearchTab.publications,
    this.category,
    this.categories = const [],
    this.categoriesLoading = false,
    this.categoriesError,
    this.publications = const [],
    this.posts = const [],
    this.slices = const [],
    this.loading = false,
    this.loadingMore = false,
    this.hasMore = false,
    this.nextPage = 0,
    this.error,
    this.retryMore = false,
    this.direct = false,
  });

  int get failedCount => slices.where((slice) => slice.error != null).length;

  SubstackSearchState copyWith({
    String? query,
    SubstackSearchTab? tab,
    SubstackCategory? category,
    bool clearCategory = false,
    List<SubstackCategory>? categories,
    bool? categoriesLoading,
    Object? categoriesError,
    bool clearCategoriesError = false,
    List<SubstackPublication>? publications,
    List<SubstackPost>? posts,
    List<SubstackSearchSlice>? slices,
    bool? loading,
    bool? loadingMore,
    bool? hasMore,
    int? nextPage,
    Object? error,
    bool clearError = false,
    bool? retryMore,
    bool? direct,
  }) => SubstackSearchState(
    query: query ?? this.query,
    tab: tab ?? this.tab,
    category: clearCategory ? null : category ?? this.category,
    categories: categories ?? this.categories,
    categoriesLoading: categoriesLoading ?? this.categoriesLoading,
    categoriesError: clearCategoriesError ? null : categoriesError ?? this.categoriesError,
    publications: publications ?? this.publications,
    posts: posts ?? this.posts,
    slices: slices ?? this.slices,
    loading: loading ?? this.loading,
    loadingMore: loadingMore ?? this.loadingMore,
    hasMore: hasMore ?? this.hasMore,
    nextPage: nextPage ?? this.nextPage,
    error: clearError ? null : error ?? this.error,
    retryMore: retryMore ?? this.retryMore,
    direct: direct ?? this.direct,
  );
}

/// Public discovery and followed-publication article search, with bounded reads.
class SubstackSearchStore extends Store<SubstackSearchState> {
  final SubstackClient client;
  final List<SubstackPublication> Function() followed;
  var _request = 0;
  var _categoryRequest = 0;
  var _closed = false;
  String? _postSources;
  SubstackSearchStore(this.client, {List<SubstackPublication> Function()? followed})
    : followed = followed ?? (() => const []),
      super(const SubstackSearchState());

  bool _current(int request) => !_closed && request == _request;

  Future<void> loadCategories() async {
    if (_closed || state.categoriesLoading) return;
    final request = ++_categoryRequest;
    final selection = _request;
    update(state.copyWith(categoriesLoading: true, clearCategoriesError: true));
    try {
      final categories = await client.fetchCategories().timeout(_searchTimeout);
      if (_closed || request != _categoryRequest) return;
      update(state.copyWith(categories: categories, categoriesLoading: false));
      if (_request == selection &&
          state.query.isEmpty &&
          state.tab == SubstackSearchTab.publications &&
          state.category == null &&
          categories.isNotEmpty) {
        await browse(categories.first);
      }
    } catch (error) {
      if (!_closed && request == _categoryRequest) {
        update(state.copyWith(categoriesLoading: false, categoriesError: error));
      }
    }
  }

  Future<void> browse(SubstackCategory category) async {
    if (_closed) return;
    final same = state.category?.id == category.id && state.query.isEmpty;
    _request++;
    update(
      state.copyWith(
        query: '',
        tab: SubstackSearchTab.publications,
        category: category,
        publications: same ? null : [],
        posts: [],
        slices: [],
        hasMore: false,
        nextPage: 0,
        direct: false,
      ),
    );
    await _loadPublications();
  }

  Future<void> search(String input, {SubstackSearchTab? tab}) async {
    if (_closed) return;
    final query = input.trim();
    final ref = resolveSubstackPostRef(query);
    final selected = ref != null ? SubstackSearchTab.posts : tab ?? state.tab;
    final same = query == state.query && selected == state.tab;
    _request++;
    update(
      state.copyWith(
        query: query,
        tab: selected,
        clearCategory: true,
        publications: same ? null : [],
        posts: same ? null : [],
        slices: same ? null : [],
        direct: ref != null,
        hasMore: false,
        nextPage: 0,
        loading: false,
        loadingMore: false,
        clearError: true,
      ),
    );
    if (query.isEmpty) {
      if (selected == SubstackSearchTab.publications) {
        if (state.categories.isEmpty) {
          await loadCategories();
        } else {
          await browse(state.categories.first);
        }
      }
      return;
    }
    if (ref != null) {
      await _loadDirectPost();
    } else if (selected == SubstackSearchTab.publications) {
      await _loadPublications();
    } else {
      await _loadPosts();
    }
  }

  Future<void> select(SubstackSearchTab tab) => search(state.query, tab: tab);
  Future<void> refresh() =>
      state.category != null && state.query.isEmpty ? browse(state.category!) : search(state.query);

  Future<void> loadMore() async {
    if (_closed || state.loading || state.loadingMore || !state.hasMore) return;
    if (state.tab == SubstackSearchTab.publications) {
      await _loadPublications(more: true);
    } else {
      await _loadPosts(more: true);
    }
  }

  Future<void> retryFailedPosts() => _loadPosts(retryFailed: true);

  Future<void> _loadPublications({bool more = false}) async {
    final request = ++_request;
    final before = state;
    final page = more ? before.nextPage : 0;
    update(state.copyWith(loading: !more, loadingMore: more, clearError: true));
    try {
      final result = before.category != null
          ? (
              publications: await client
                  .fetchCategoryPublications(before.category!.id, page: page)
                  .timeout(_searchTimeout),
              pageable: true,
            )
          : await _findPublications(before.query, page);
      if (!_current(request)) return;
      final items = _uniquePublications([if (more) ...before.publications, ...result.publications]);
      final added = items.length - (more ? before.publications.length : 0);
      update(
        state.copyWith(
          publications: items,
          loading: false,
          loadingMore: false,
          hasMore: result.pageable && added > 0,
          nextPage: page + 1,
          retryMore: false,
        ),
      );
    } catch (error) {
      if (_current(request)) {
        update(
          state.copyWith(
            loading: false,
            loadingMore: false,
            error: error,
            hasMore: before.hasMore,
            nextPage: before.nextPage,
            retryMore: more,
          ),
        );
      }
    }
  }

  Future<({List<SubstackPublication> publications, bool pageable})> _findPublications(String query, int page) async {
    if (_directPublicationInput(query)) {
      return (publications: [await client.resolvePublication(query).timeout(_searchTimeout)], pageable: false);
    }
    Object? failure;
    var items = const <SubstackPublication>[];
    try {
      items = await client.searchPublications(query, page: page).timeout(_searchTimeout);
    } catch (error) {
      failure = error;
    }
    if (items.isNotEmpty) return (publications: items, pageable: true);
    if (page == 0 && RegExp(r'^@?[A-Za-z0-9_-]+$').hasMatch(query)) {
      try {
        return (publications: [await client.resolvePublication(query).timeout(_searchTimeout)], pageable: false);
      } catch (error) {
        if (failure != null) throw failure;
        if (error is! SubstackNotPublicationException) rethrow;
      }
    }
    if (failure != null) throw failure;
    return (publications: const <SubstackPublication>[], pageable: false);
  }

  Future<void> _loadDirectPost() async {
    final request = ++_request;
    final query = state.query;
    update(state.copyWith(loading: true, clearError: true, hasMore: false));
    try {
      final publication = await client.resolvePublication(query).timeout(_searchTimeout);
      if (!_current(request)) return;
      update(state.copyWith(publications: [publication]));
      final ref = resolveSubstackPostRef(query)!;
      final post = await client.fetchPost(publication, ref.slug).timeout(_searchTimeout);
      if (_current(request)) update(state.copyWith(posts: [post], loading: false));
    } catch (error) {
      if (_current(request)) update(state.copyWith(loading: false, error: error));
    }
  }

  Future<void> _loadPosts({bool more = false, bool retryFailed = false}) async {
    if (_closed || (retryFailed && (state.loading || state.loadingMore))) return;
    final identity = _followedIdentity();
    if ((more || retryFailed) && identity != _postSources) {
      await _loadPosts();
      return;
    }
    final request = ++_request;
    _postSources = identity;
    final query = state.query;
    final before = state.slices;
    final oldByPublication = {for (final slice in before) substackSearchPublicationKey(slice.publication): slice};
    final sources = more || retryFailed
        ? before
        : [
            for (final pub in _uniquePublications(followed()))
              SubstackSearchSlice(pub, posts: oldByPublication[substackSearchPublicationKey(pub)]?.posts ?? const []),
          ];
    update(
      state.copyWith(
        loading: !more && !retryFailed,
        loadingMore: more || retryFailed,
        clearError: true,
        slices: sources,
      ),
    );
    final loaded = sources.toList();
    final targets = [
      for (var i = 0; i < sources.length; i++)
        if (retryFailed ? sources[i].error != null : !more || sources[i].hasMore) i,
    ];
    for (var start = 0; start < targets.length; start += 4) {
      if (!_current(request)) return;
      final batch = targets.skip(start).take(4);
      await Future.wait([
        for (final index in batch)
          _searchSlice(sources[index], query, replace: !more && !retryFailed).then((slice) => loaded[index] = slice),
      ]);
      if (!_current(request) || !_acceptFollowedSources(identity)) return;
      update(state.copyWith(slices: List.unmodifiable(loaded), posts: _mergedPosts(loaded)));
    }
    if (!_current(request) || !_acceptFollowedSources(identity)) return;
    update(
      state.copyWith(
        slices: loaded,
        posts: _mergedPosts(loaded),
        loading: false,
        loadingMore: false,
        hasMore: loaded.any((slice) => slice.hasMore && slice.error == null),
      ),
    );
  }

  String _followedIdentity() {
    final keys = _uniquePublications(followed()).map(substackSearchPublicationKey).toList()..sort();
    return keys.join('\n');
  }

  bool _acceptFollowedSources(String identity) {
    if (identity == _followedIdentity()) return true;
    _request++;
    update(
      state.copyWith(
        posts: [],
        slices: [],
        loading: false,
        loadingMore: false,
        hasMore: false,
        error: SubstackClientException('Followed publications changed'),
        retryMore: false,
      ),
    );
    return false;
  }

  Future<SubstackSearchSlice> _searchSlice(SubstackSearchSlice before, String query, {bool replace = false}) async {
    try {
      final posts = await client
          .searchPosts(before.publication, query, limit: _postPageSize, offset: before.offset)
          .timeout(_searchTimeout);
      final previous = replace || before.offset == 0 ? const <SubstackPost>[] : before.posts;
      final merged = {for (final post in previous) substackSearchPostKey(post): post};
      final beforeCount = merged.length;
      for (final post in posts) {
        merged[substackSearchPostKey(post)] = post;
      }
      final added = merged.length - beforeCount;
      return SubstackSearchSlice(
        before.publication,
        posts: merged.values.toList(growable: false),
        offset: before.offset + _postPageSize,
        hasMore: added > 0,
      );
    } catch (error) {
      return SubstackSearchSlice(
        before.publication,
        posts: before.posts,
        offset: before.offset,
        hasMore: before.hasMore,
        error: error,
      );
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    _categoryRequest++;
    return super.destroy();
  }
}

bool _directPublicationInput(String query) =>
    !RegExp(r'\s').hasMatch(query) &&
    (query.contains('://') ||
        query.startsWith('@') ||
        RegExp(r'^[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+(?:/.*)?$').hasMatch(query));

List<SubstackPublication> _uniquePublications(Iterable<SubstackPublication> publications) {
  final seen = <String>{};
  return publications
      .where((pub) => seen.add(pub.id.isEmpty ? pub.baseUrl.toLowerCase() : pub.id))
      .toList(growable: false);
}

List<SubstackPost> _mergedPosts(List<SubstackSearchSlice> slices) {
  final seen = <String>{};
  final posts = [
    for (final slice in slices)
      for (final post in slice.posts)
        if (seen.add(substackSearchPostKey(post))) post,
  ];
  posts.sort((a, b) => (b.publishedAt ?? DateTime(1970)).compareTo(a.publishedAt ?? DateTime(1970)));
  return posts;
}

class SubstackPreviewState {
  final SubstackPublication? publication;
  final SubstackPost? post;
  final bool loading;
  final bool following;
  final bool followed;
  final Object? error;
  final Object? postError;
  const SubstackPreviewState({
    this.publication,
    this.post,
    this.loading = false,
    this.following = false,
    this.followed = false,
    this.error,
    this.postError,
  });
}

/// Preview first. Lookup completion never writes a local subscription.
class SubstackPreviewStore extends Store<SubstackPreviewState> {
  final SubstackClient client;
  var _request = 0;
  var _closed = false;
  String _input = '';
  bool _followedAny = false;
  bool get followedAny => _followedAny;
  SubstackPreviewStore(this.client) : super(const SubstackPreviewState());

  Future<void> lookup(String input) async {
    if (_closed || state.following) return;
    final request = ++_request;
    _input = input.trim();
    update(const SubstackPreviewState(loading: true));
    try {
      final publication = await client.resolvePublication(_input).timeout(_searchTimeout);
      if (_closed || request != _request) return;
      update(SubstackPreviewState(publication: publication));
      final ref = resolveSubstackPostRef(_input);
      if (ref != null) await _loadPost(publication, ref.slug, request);
    } catch (error) {
      if (!_closed && request == _request) update(SubstackPreviewState(error: error));
    }
  }

  Future<void> retryPost() async {
    final publication = state.publication;
    final ref = resolveSubstackPostRef(_input);
    if (_closed || publication == null || ref == null || state.loading || state.following) return;
    await _loadPost(publication, ref.slug, ++_request);
  }

  Future<void> _loadPost(SubstackPublication publication, String slug, int request) async {
    final followed = state.followed;
    update(SubstackPreviewState(publication: publication, loading: true, followed: followed));
    try {
      final post = await client.fetchPost(publication, slug).timeout(_searchTimeout);
      if (!_closed && request == _request) {
        update(SubstackPreviewState(publication: publication, post: post, followed: followed));
      }
    } catch (error) {
      if (!_closed && request == _request) {
        update(SubstackPreviewState(publication: publication, postError: error, followed: followed));
      }
    }
  }

  Future<bool> follow(Future<void> Function(SubstackPublication) save) async {
    final before = state;
    if (_closed || before.publication == null || before.following || before.loading || before.followed) return false;
    update(
      SubstackPreviewState(
        publication: before.publication,
        post: before.post,
        postError: before.postError,
        following: true,
      ),
    );
    try {
      await save(before.publication!);
      _followedAny = true;
      if (_closed) return true;
      update(
        SubstackPreviewState(
          publication: before.publication,
          post: before.post,
          postError: before.postError,
          followed: true,
        ),
      );
      return true;
    } catch (error) {
      if (!_closed) {
        update(
          SubstackPreviewState(
            publication: before.publication,
            post: before.post,
            postError: before.postError,
            error: error,
          ),
        );
      }
      return false;
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
