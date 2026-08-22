import 'package:http/http.dart' as http;
import 'package:xta/utils/breadcrumbs.dart';

/// Notes every plugin request as a breadcrumb, then gets out of the way.
///
/// A crash log that says "RangeError in a masonry tile" is guesswork; one that
/// says the reader was on the Pixiv tab and the last fetch was
/// `pixiv GET app-api.pixiv.net/v1/illust/recommended` is a bug report. The
/// wrapper only records — it never retries, rewrites or swallows anything.
class BreadcrumbHttpClient extends http.BaseClient {
  final http.Client inner;
  final String plugin;
  final Breadcrumbs breadcrumbs;

  BreadcrumbHttpClient(
    this.plugin, {
    http.Client? inner,
    Breadcrumbs? breadcrumbs,
  }) : inner = inner ?? http.Client(),
       breadcrumbs = breadcrumbs ?? Breadcrumbs.instance;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    breadcrumbs.drop(Crumb.fetch, requestLabel(plugin, request));
    return inner.send(request);
  }

  @override
  void close() {
    inner.close();
    super.close();
  }
}

/// Host and path only. A query string carries search terms, tokens and cursors,
/// and this text is meant to be shareable without a second thought.
String requestLabel(String plugin, http.BaseRequest request) {
  final uri = request.url;
  return '$plugin ${request.method} ${uri.host}${uri.path}';
}
