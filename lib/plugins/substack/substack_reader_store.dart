import 'package:flutter_triple/flutter_triple.dart';
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

  const SubstackReaderState({required this.post, this.error, this.loading = true,
    this.empty = false, this.paywalled = false, this.partial = false,
    this.speakText, this.liveSite = false});
}

class SubstackReaderStore extends Store<SubstackReaderState> {
  SubstackReaderStore(SubstackPost post) : super(SubstackReaderState(post: post));

  void change({SubstackPost? post, Object? error = _unchanged, bool? loading,
    bool? empty, bool? paywalled, bool? partial, String? speakText, bool? liveSite}) {
    update(SubstackReaderState(
      post: post ?? state.post,
      error: identical(error, _unchanged) ? state.error : error,
      loading: loading ?? state.loading,
      empty: empty ?? state.empty,
      paywalled: paywalled ?? state.paywalled,
      partial: partial ?? state.partial,
      speakText: speakText ?? state.speakText,
      liveSite: liveSite ?? state.liveSite,
    ));
  }
}

/// A deep link starts with a slug ID; the full API post uses a numeric ID.
String substackArticleReadingId(SubstackPost post) =>
  'substack:${post.publication.id}:${post.slug.isEmpty ? post.id : post.slug}';
