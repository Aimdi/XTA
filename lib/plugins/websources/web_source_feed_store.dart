/// One list of a web source, page by page.
library;

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_items.dart';

class WebSourceFeedStore extends Store<List<WebSourceItem>> {
  final WebSourceClient client;
  final WebSourceDefinition source;
  final WebSourceList list;

  /// What a search list looks for; null for a feed.
  final String? query;

  Uri? _next;
  var _page = 0;
  var _loadingMore = false;

  WebSourceFeedStore(this.client, this.source, this.list, {this.query}) : super(const []);

  bool get hasMore => _next != null;

  Future<void> refresh() => execute(() async {
    final url = webSourceListUrl(list, query: query);
    if (url == null) throw WebSourceFailure(WebSourceFailureKind.blocked, Uri());
    final page = await _load(url, list.firstPage);
    _page = list.firstPage;
    _next = page.next;
    return page.items;
  });

  /// The next page, after what is shown. A page that brings nothing new
  /// ends the list, so a site that repeats its last page is not read forever.
  Future<void> loadMore() async {
    final next = _next;
    if (next == null || _loadingMore || state.isEmpty) return;
    _loadingMore = true;
    try {
      final page = await _load(next, _page + 1);
      final known = {for (final item in state) item.key};
      final fresh = [
        for (final item in page.items)
          if (known.add(item.key)) item,
      ];
      _page++;
      _next = fresh.isEmpty ? null : page.next;
      update([...state, ...fresh]);
    } finally {
      _loadingMore = false;
    }
  }

  Future<WebSourcePage> _load(Uri url, int page) async =>
      webSourcePage(source, list, await client.page(source, url, list.type), url, page: page, query: query);
}
