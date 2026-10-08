import 'package:flutter_triple/flutter_triple.dart';

enum ReaderSearchKind { saved, note, account, group, article }

class ReaderSearchDocument {
  final String id, title, text;
  final ReaderSearchKind kind;
  final Object target;
  const ReaderSearchDocument({
    required this.id,
    required this.title,
    required this.text,
    required this.kind,
    required this.target,
  });
  bool matches(String query) {
    final haystack = '$title $text'.toLowerCase();
    return query.toLowerCase().trim().split(RegExp(r'\s+')).every(haystack.contains);
  }
}

class ReaderSearchState {
  final List<ReaderSearchDocument> documents;
  final String query;
  final ReaderSearchKind? kind;
  final bool loading;
  final Object? error;
  const ReaderSearchState({this.documents = const [], this.query = '', this.kind, this.loading = false, this.error});
  List<ReaderSearchDocument> get results =>
      documents.where((e) => (kind == null || e.kind == kind) && e.matches(query)).toList();
}

class ReaderSearchStore extends Store<ReaderSearchState> {
  bool _closed = false;
  int _generation = 0;
  ReaderSearchStore() : super(const ReaderSearchState());
  void query(String value) => update(
    ReaderSearchState(
      documents: state.documents,
      query: value,
      kind: state.kind,
      loading: state.loading,
      error: state.error,
    ),
  );
  void filter(ReaderSearchKind? kind) => update(
    ReaderSearchState(
      documents: state.documents,
      query: state.query,
      kind: kind,
      loading: state.loading,
      error: state.error,
    ),
  );
  Future<void> load(List<ReaderSearchDocument> local, Future<List<ReaderSearchDocument>> Function() archive) async {
    final generation = ++_generation;
    update(ReaderSearchState(documents: local, query: state.query, kind: state.kind, loading: true));
    try {
      final rest = await archive();
      if (_closed || generation != _generation) return;
      update(ReaderSearchState(documents: [...local, ...rest], query: state.query, kind: state.kind));
    } catch (error) {
      if (!_closed && generation == _generation)
        update(ReaderSearchState(documents: local, query: state.query, kind: state.kind, error: error));
    }
  }

  void fail(Object error) {
    if (!_closed)
      update(ReaderSearchState(documents: state.documents, query: state.query, kind: state.kind, error: error));
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _generation++;
    return super.destroy();
  }
}
