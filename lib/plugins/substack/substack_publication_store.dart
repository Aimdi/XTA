import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';

enum SubstackPublicationFilter { all, unread, free, podcasts, videos }

enum SubstackPublicationOrder { newest, oldest, popular }

class SubstackPublicationPage {
  final List<SubstackPost> posts;
  final int offset;
  final bool loading;
  final bool canLoadMore;
  final Object? error;
  final bool failedMore;

  const SubstackPublicationPage({
    this.posts = const [],
    this.offset = 0,
    this.loading = false,
    this.canLoadMore = false,
    this.error,
    this.failedMore = false,
  });

  SubstackPublicationPage pending() =>
      SubstackPublicationPage(posts: posts, offset: offset, loading: true, canLoadMore: canLoadMore);

  SubstackPublicationPage failed(Object error, bool more) =>
      SubstackPublicationPage(posts: posts, offset: offset, canLoadMore: canLoadMore, error: error, failedMore: more);
}

class SubstackPublicationState {
  final SubstackPublication publication;
  final SubstackPublicationPage archive;
  final SubstackPublicationPage search;
  final String query;
  final SubstackPublicationFilter filter;
  final SubstackPublicationOrder order;
  final bool expanded;

  const SubstackPublicationState({
    required this.publication,
    this.archive = const SubstackPublicationPage(),
    this.search = const SubstackPublicationPage(),
    this.query = '',
    this.filter = SubstackPublicationFilter.all,
    this.order = SubstackPublicationOrder.newest,
    this.expanded = false,
  });

  SubstackPublicationPage get page => query.isEmpty ? archive : search;

  SubstackPublicationState copy({
    SubstackPublication? publication,
    SubstackPublicationPage? archive,
    SubstackPublicationPage? search,
    String? query,
    SubstackPublicationFilter? filter,
    SubstackPublicationOrder? order,
    bool? expanded,
  }) => SubstackPublicationState(
    publication: publication ?? this.publication,
    archive: archive ?? this.archive,
    search: search ?? this.search,
    query: query ?? this.query,
    filter: filter ?? this.filter,
    order: order ?? this.order,
    expanded: expanded ?? this.expanded,
  );
}

class SubstackPublicationStore extends Store<SubstackPublicationState> {
  static const pageSize = 20;
  final SubstackClient client;
  final Duration debounce;
  Timer? _timer;
  var _archiveRequest = 0;
  var _searchRequest = 0;
  var _closed = false;

  SubstackPublicationStore(
    this.client,
    SubstackPublication publication, {
    this.debounce = const Duration(milliseconds: 300),
  }) : super(SubstackPublicationState(publication: publication));

  void filter(SubstackPublicationFilter value) => update(state.copy(filter: value));

  void order(SubstackPublicationOrder value) => update(state.copy(order: value));

  void toggleDescription() => update(state.copy(expanded: !state.expanded));

  void search(String input) {
    final query = input.trim();
    if (query == state.query || _closed) return;
    _timer?.cancel();
    _searchRequest++;
    update(
      state.copy(
        query: query,
        search: SubstackPublicationPage(loading: query.isNotEmpty),
      ),
    );
    if (query.isNotEmpty) _timer = Timer(debounce, () => _fetch(search: true, more: false));
  }

  Future<void> refresh() {
    _timer?.cancel();
    return _fetch(search: state.query.isNotEmpty, more: false);
  }

  Future<void> loadMore() async {
    if (state.page.loading || !state.page.canLoadMore) return;
    await _fetch(search: state.query.isNotEmpty, more: true);
  }

  Future<void> retry() async {
    if (state.page.loading) return;
    _timer?.cancel();
    await _fetch(search: state.query.isNotEmpty, more: state.page.failedMore);
  }

  void _publishPage(bool search, SubstackPublicationPage page) {
    update(search ? state.copy(search: page) : state.copy(archive: page));
  }

  Future<void> _fetch({required bool search, required bool more}) async {
    if (_closed) return;
    final query = state.query;
    if (search && query.isEmpty) return;
    final request = search ? ++_searchRequest : ++_archiveRequest;
    final prior = search ? state.search : state.archive;
    final offset = more ? prior.offset : 0;
    _publishPage(search, prior.pending());
    try {
      final page = search
          ? await client.searchPosts(state.publication, query, limit: pageSize, offset: offset)
          : await client.fetchPosts(state.publication, limit: pageSize, offset: offset);
      if (!_current(search, request)) return;
      _publishPage(search, _merge(prior, page, more, offset));
    } catch (error) {
      if (_current(search, request)) _publishPage(search, prior.failed(error, more));
    }
  }

  bool _current(bool search, int request) => !_closed && request == (search ? _searchRequest : _archiveRequest);

  SubstackPublicationPage _merge(SubstackPublicationPage prior, List<SubstackPost> incoming, bool more, int offset) {
    final posts = {
      if (more)
        for (final post in prior.posts) publicationPostKey(post): post,
    };
    final oldLength = posts.length;
    for (final post in incoming) {
      posts[publicationPostKey(post)] = post;
    }
    return SubstackPublicationPage(
      posts: posts.values.toList(growable: false),
      offset: offset + pageSize,
      canLoadMore: incoming.length >= pageSize && posts.length > oldLength,
    );
  }

  Future<SubstackPublication?> enrich() async {
    final prior = state.publication;
    if (!publicationNameLooksGeneric(prior.name) &&
        prior.name.toLowerCase() != prior.subdomain.toLowerCase() &&
        (prior.description?.trim().isNotEmpty ?? false) &&
        (prior.logoUrl?.isNotEmpty ?? false)) {
      return null;
    }
    try {
      final fresh = await client.fetchPublication(Uri.parse(prior.baseUrl));
      if (_closed) return null;
      final merged = SubstackPublication(
        subdomain: prior.subdomain.isEmpty ? fresh.subdomain : prior.subdomain,
        baseUrl: prior.baseUrl,
        name: publicationNameLooksGeneric(fresh.name) ? prior.name : fresh.name,
        description: fresh.description ?? prior.description,
        logoUrl: fresh.logoUrl ?? prior.logoUrl,
      );
      update(state.copy(publication: merged));
      return merged;
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _timer?.cancel();
    return super.destroy();
  }
}

String publicationPostKey(SubstackPost post) => '${post.publicationBaseUrl}/${post.id.isEmpty ? post.slug : post.id}';

List<SubstackPost> visiblePublicationPosts(SubstackPublicationState state, Set<String> read) {
  final posts = state.page.posts
      .where(
        (post) => switch (state.filter) {
          SubstackPublicationFilter.all => true,
          SubstackPublicationFilter.unread => !read.contains(post.id),
          SubstackPublicationFilter.free => !post.isPaywalled,
          SubstackPublicationFilter.podcasts => post.isPodcast,
          SubstackPublicationFilter.videos => post.isVideo,
        },
      )
      .indexed
      .toList();
  posts.sort((a, b) {
    if (state.order == SubstackPublicationOrder.popular) {
      final rank = (b.$2.reactionCount ?? 0).compareTo(a.$2.reactionCount ?? 0);
      if (rank != 0) return rank;
    }
    final left = a.$2.publishedAt;
    final right = b.$2.publishedAt;
    if (left == null || right == null) {
      if (left != right) return left == null ? 1 : -1;
    } else {
      final rank = state.order == SubstackPublicationOrder.oldest ? left.compareTo(right) : right.compareTo(left);
      if (rank != 0) return rank;
    }
    return a.$1.compareTo(b.$1);
  });
  return posts.map((entry) => entry.$2).toList(growable: false);
}

class SubstackSimilarState {
  final List<SubstackRecommendation> recommendations;
  final bool loading;
  final Object? error;

  const SubstackSimilarState({this.recommendations = const [], this.loading = false, this.error});
}

class SubstackSimilarStore extends Store<SubstackSimilarState> {
  final SubstackClient client;
  final SubstackPublication publication;
  var _closed = false;

  SubstackSimilarStore(this.client, this.publication) : super(const SubstackSimilarState());

  Future<void> load() async {
    if (_closed || state.loading) return;
    update(SubstackSimilarState(recommendations: state.recommendations, loading: true));
    try {
      final results = await client.fetchSimilarPublications(publication);
      if (_closed) return;
      final seen = {publication.id};
      update(
        SubstackSimilarState(
          recommendations: [
            for (final result in results)
              if (seen.add(result.publication.id)) result,
          ],
        ),
      );
    } catch (error) {
      if (!_closed) update(SubstackSimilarState(recommendations: state.recommendations, error: error));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
