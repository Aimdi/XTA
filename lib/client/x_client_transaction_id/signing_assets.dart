import 'dart:async';
import 'dart:io' show HttpException;

import 'package:html/dom.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/utils/request_budget.dart';

/// Discovers the current sign.o import without evaluating downloaded JavaScript.
/// X also continues serving the older ondemand.s bundles.
class SigningAssets {
  // Limits distinct assets; the shared transport may retry a transient failure.
  static const maxAssets = 16;
  static const concurrency = 4;
  final RequestBudget budget;
  final _visited = <Uri>{};
  int _requests = 0;

  SigningAssets(this.budget);

  static bool trusted(Uri uri) =>
      uri.scheme == 'https' &&
      uri.host == 'abs.twimg.com' &&
      uri.userInfo.isEmpty &&
      uri.port == 443 &&
      !uri.hasQuery &&
      !uri.hasFragment &&
      !uri.pathSegments.any((segment) => segment == '.' || segment == '..') &&
      RegExp(r'^/(?:x-web|responsive-web/client-web)/[a-zA-Z0-9_./~-]+\.js$').hasMatch(uri.path);

  static bool signer(Uri uri) =>
      trusted(uri) && RegExp(r'^(?:sign\.o|ondemand\.s)[.-][a-zA-Z0-9_-]+\.js$').hasMatch(uri.pathSegments.last);

  Future<String> read(Uri uri) async {
    if (!trusted(uri) || _requests >= maxAssets) {
      throw const FormatException('X transaction signing asset limit exceeded');
    }
    _requests++;
    final response = await getXResponse(uri, timeout: budget.remaining, followRedirects: false);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException('X signing bundle returned HTTP ${response.statusCode}', uri: uri);
    }
    return response.body;
  }

  Future<Uri?> discover(Document page) async {
    final linked = <Uri>{};
    // Entry scripts follow long preload lists in some pages. Inspect them first.
    final elements = [
      ...page.querySelectorAll('script[src]'),
      ...page.querySelectorAll('link[rel="modulepreload"][href]'),
    ];
    for (final element in elements) {
      final uri = Uri.tryParse(element.attributes['src'] ?? element.attributes['href'] ?? '');
      if (uri != null && trusted(uri)) linked.add(uri);
    }
    for (final uri in linked) {
      if (signer(uri)) return uri;
    }
    final candidates = linked.where(_visited.add).take(maxAssets - 1 - _requests).toList();
    if (candidates.isEmpty) return null;
    return _scan(candidates);
  }

  Future<Uri?> _scan(List<Uri> candidates) {
    final result = Completer<Uri?>();
    var cursor = 0;
    var loaded = 0;
    Object? firstError;
    Future<void> worker() async {
      while (!result.isCompleted && cursor < candidates.length) {
        final uri = candidates[cursor++];
        try {
          final source = await read(uri);
          loaded++;
          if (result.isCompleted) return;
          final match = _findImport(uri, source);
          if (match != null) result.complete(match);
        } catch (error) {
          firstError ??= error;
        }
      }
    }

    unawaited(
      Future.wait(List.generate(concurrency, (_) => worker())).then((_) {
        if (result.isCompleted) return;
        if (loaded == 0 && firstError != null) {
          result.completeError(firstError!);
        } else {
          result.complete(null);
        }
      }),
    );
    return result.future;
  }

  static Uri? _findImport(Uri parent, String source) {
    final references = RegExp(r'''["']([^"'\s]+\.js)["']''').allMatches(source);
    for (final reference in references) {
      final uri = parent.resolve(reference.group(1)!);
      if (signer(uri)) return uri;
    }
    return null;
  }
}
