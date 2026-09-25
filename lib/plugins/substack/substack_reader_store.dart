import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_models.dart';

const _unchanged = Object();

class SubstackReaderState {
  final SubstackPost post;
  final Object? error;
  final bool loading;
  final bool empty;
  final bool paywalled;
  final bool partial;
  final String? speakText;
  final bool liveSite;
  final bool refreshing;
  final Object? refreshError;
  final SubstackArticleDocument? document;

  const SubstackReaderState({
    required this.post,
    this.error,
    this.loading = true,
    this.empty = false,
    this.paywalled = false,
    this.partial = false,
    this.speakText,
    this.liveSite = false,
    this.refreshing = false,
    this.refreshError,
    this.document,
  });
}

class SubstackReaderStore extends Store<SubstackReaderState> {
  SubstackReaderStore(SubstackPost post) : super(SubstackReaderState(post: post));
  int _generation = 0;
  bool _closed = false;
  int get generation => _generation;
  bool accepts(int generation) => !_closed && generation == _generation;

  Future<SubstackPost?> load(Future<SubstackPost> Function() resolve, {bool refresh = false}) async {
    final request = ++_generation;
    if (_closed) return null;
    change(error: null, refreshError: null, loading: !refresh, refreshing: refresh);
    try {
      final post = await resolve();
      if (!accepts(request)) return null;
      change(post: post, refreshing: false);
      return post;
    } catch (error) {
      if (accepts(request)) {
        change(error: refresh ? null : error, refreshError: refresh ? error : null, loading: false, refreshing: false);
      }
      return null;
    }
  }

  void change({
    SubstackPost? post,
    Object? error = _unchanged,
    bool? loading,
    bool? empty,
    bool? paywalled,
    bool? partial,
    String? speakText,
    bool? liveSite,
    bool? refreshing,
    Object? refreshError = _unchanged,
    Object? document = _unchanged,
  }) {
    if (_closed) return;
    update(
      SubstackReaderState(
        post: post ?? state.post,
        error: identical(error, _unchanged) ? state.error : error,
        loading: loading ?? state.loading,
        empty: empty ?? state.empty,
        paywalled: paywalled ?? state.paywalled,
        partial: partial ?? state.partial,
        speakText: speakText ?? state.speakText,
        liveSite: liveSite ?? state.liveSite,
        refreshing: refreshing ?? state.refreshing,
        refreshError: identical(refreshError, _unchanged) ? state.refreshError : refreshError,
        document: identical(document, _unchanged) ? state.document : document as SubstackArticleDocument?,
      ),
    );
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    _generation++;
    await super.destroy();
  }
}

class SubstackArticleNavigationStore extends Store<String> {
  final SubstackArticleDocument document;
  SubstackArticleNavigationStore(this.document) : super('');

  void search(String query) => update(query);
  List<SubstackArticlePassage> get results => state.trim().isEmpty ? document.headings : document.search(state);
}

/// A deep link starts with a slug ID; the full API post uses a numeric ID.
String substackArticleReadingId(SubstackPost post) =>
    'substack:${post.publication.id}:${post.slug.isEmpty ? post.id : post.slug}';
