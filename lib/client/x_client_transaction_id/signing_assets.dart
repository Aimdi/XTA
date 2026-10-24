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
  static const maxImportDepth = 3;
  static final _page = Uri.https('x.com', '/');
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

  /// The module that imports the signer, by the names X has given it since
  /// September 2026. It sits beside large startup bundles that do not.
  static bool importer(Uri uri) =>
      RegExp(r'^(?:startup-dynamic|client-transaction-id-plugin)[.-]').hasMatch(uri.pathSegments.last);

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
    // The sign-in page starts its entry script from inline code instead of a tag.
    for (final script in page.querySelectorAll('script:not([src])')) {
      linked.addAll(_SigningAssetSearch._references(_page, script.text, _SigningAssetSearch._literal));
    }
    for (final uri in linked) {
      if (signer(uri)) return uri;
    }
    return _SigningAssetSearch(this).run(linked);
  }
}

class _SigningAssetSearch {
  final SigningAssets assets;
  final _queue = <({Uri uri, int depth})>[];
  final _result = Completer<Uri?>();
  int _active = 0;
  int _activeImporters = 0;
  int _loaded = 0;
  Object? _firstError;

  _SigningAssetSearch(this.assets);

  Future<Uri?> run(Iterable<Uri> roots) {
    _enqueue(roots, 0);
    _pump();
    return _result.future;
  }

  void _enqueue(Iterable<Uri> uris, int depth) {
    if (depth > SigningAssets.maxImportDepth) return;
    final fresh = uris.where((uri) => !assets._visited.contains(uri)).toSet();
    final next = [
      ...fresh.where(SigningAssets.importer),
      ...fresh.where((uri) => !SigningAssets.importer(uri)),
    ].take(SigningAssets.maxAssets).where(assets._visited.add).toList();
    // Real module imports precede unrelated HTML preloads and Vite lookup tables.
    _queue.insertAll(0, next.map((uri) => (uri: uri, depth: depth)));
  }

  void _pump() {
    while (!_result.isCompleted &&
        _active < SigningAssets.concurrency &&
        assets._requests < SigningAssets.maxAssets - 1 &&
        _queue.isNotEmpty) {
      final importer = SigningAssets.importer(_queue.first.uri);
      // While an importer loads, the bundles around it wait: it usually names
      // the signer, and they are the bulk of what the search downloads.
      if (_activeImporters > 0 && !importer) break;
      final candidate = _queue.removeAt(0);
      _active++;
      if (importer) _activeImporters++;
      unawaited(
        _visit(candidate).whenComplete(() {
          _active--;
          if (importer) _activeImporters--;
          _pump();
        }),
      );
    }
    if (_active != 0 || _result.isCompleted) return;
    if (_loaded == 0 && _firstError != null) {
      _result.completeError(_firstError!);
    } else {
      _result.complete(null);
    }
  }

  Future<void> _visit(({Uri uri, int depth}) candidate) async {
    try {
      final source = await assets.read(candidate.uri);
      _loaded++;
      if (_result.isCompleted) return;
      final signer = _references(candidate.uri, source, _literal).where(SigningAssets.signer).firstOrNull;
      if (signer != null) {
        _result.complete(signer);
      } else {
        _enqueue(_references(candidate.uri, source, _moduleImport), candidate.depth + 1);
      }
    } catch (error) {
      _firstError ??= error;
    }
  }

  // Template literals are accepted only when constant: never evaluate ${...}.
  static final _literal = RegExp(r'''(["'`])([^"'`\s$]+\.js)\1''');
  static final _moduleImport = RegExp(r'''(?:\bfrom\s*|\bimport\s*\(?\s*)(["'`])([^"'`\s$]+\.js)\1''');

  static Iterable<Uri> _references(Uri parent, String source, RegExp pattern) sync* {
    for (final match in pattern.allMatches(source)) {
      final reference = Uri.tryParse(match.group(2)!);
      if (reference == null) continue;
      final uri = parent.resolveUri(reference);
      if (SigningAssets.trusted(uri)) yield uri;
    }
  }
}
