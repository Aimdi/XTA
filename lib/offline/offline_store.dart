import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:html/dom.dart' show Element;
import 'package:html/parser.dart' as html;
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:xta/offline/offline_article.dart';
import 'package:xta/offline/offline_models.dart';
import 'package:xta/offline/offline_transfer.dart';

export 'offline_models.dart';

class OfflineStore extends Store<OfflineState> {
  static final shared = OfflineStore();
  static const maxItems = 200;
  static const maxMedia = 64;
  static const maxArticleBytes = 2 * 1024 * 1024;
  static const maxArticleImageBytes = 24 * 1024 * 1024;
  static const maxManifestBytes = 1024 * 1024;
  static const defaultStorageLimit = 500 * 1024 * 1024;
  final Future<Directory> Function() _directory;
  final http.Client _client;
  final bool _ownsClient;
  final int storageLimit;
  final int imageLimit;
  final int videoLimit;
  final _generations = <String, int>{};
  Future<void>? _loaded;
  Future<void> _writes = Future.value();
  Directory? _root;
  bool _disposed = false;

  OfflineStore({Future<Directory> Function()? directory, http.Client? client,
    this.storageLimit = defaultStorageLimit, this.imageLimit = 8 * 1024 * 1024,
    this.videoLimit = 50 * 1024 * 1024})
      : _directory = directory ?? _defaultDirectory, _client = client ?? http.Client(),
        _ownsClient = client == null, super(const OfflineState());

  static Future<Directory> _defaultDirectory() async =>
    Directory(p.join((await getApplicationSupportDirectory()).path, 'xta_offline_v1'));

  void _emit({List<OfflineEntry>? entries, Set<String>? busy, Set<String>? failed,
    bool? loading, bool? storageError}) {
    if (_disposed) return;
    update(OfflineState(entries: List.unmodifiable(entries ?? state.entries),
      busy: Set.unmodifiable(busy ?? state.busy), failed: Set.unmodifiable(failed ?? state.failed),
      loading: loading ?? state.loading, storageError: storageError ?? state.storageError));
  }

  Future<void> load() => _loaded ??= _load();

  Future<void> refresh() => _queue(() async {
    _loaded = null;
    _emit(loading: true);
    await load();
  });

  Future<void> _load() async {
    try {
      _root = await _directory();
      await _root!.create(recursive: true);
      final manifest = File(p.join(_root!.path, 'manifest.json'));
      final entries = <OfflineEntry>[];
      if (await manifest.exists()) {
        if (await manifest.length() > maxManifestBytes) throw const FormatException('Oversized manifest');
        final decoded = jsonDecode(await manifest.readAsString());
        if (decoded is! Map || decoded['version'] != 1 || decoded['entries'] is! List) {
          throw const FormatException('Invalid manifest');
        }
        final raw = decoded['entries'] as List;
        if (raw.length > maxItems) throw const FormatException('Too many entries');
        for (final value in raw) {
          try {
            final entry = OfflineEntry.fromJson(Map<String, dynamic>.from(value as Map));
            if (!_safeEntry(entry) || entries.any((e) => e.id == entry.id)) continue;
            entries.add(await _verify(entry));
          } catch (_) { /* A damaged row does not hide the rest of the library. */ }
        }
      }
      await _cleanOrphans(entries);
      _emit(entries: entries, loading: false, storageError: false);
    } catch (_) {
      _emit(loading: false, storageError: true);
      _loaded = null;
    }
  }

  bool _safeEntry(OfflineEntry entry) =>
    RegExp(r'^[a-f0-9]{64}-[0-9]+$').hasMatch(entry.directory) &&
    entry.id.isNotEmpty && entry.id.length < 8192 && entry.files.length <= maxMedia &&
    entry.totalMedia >= entry.files.length && entry.totalMedia <= 10000 &&
    entry.articleBytes >= 0 && entry.articleBytes <= maxArticleBytes &&
    entry.files.every((f) => RegExp(r'^media-[0-9]+$').hasMatch(f.name) &&
      f.bytes > 0 && f.bytes <= max(videoLimit, imageLimit));

  Future<OfflineEntry> _verify(OfflineEntry entry) async {
    var articleBytes = 0;
    final articleFile = File(p.join(_root!.path, entry.directory, 'article.json'));
    if (entry.articleBytes > 0 && await articleFile.exists() && await articleFile.length() == entry.articleBytes) {
      try {
        final article = OfflineArticle.fromJson(Map<String, dynamic>.from(jsonDecode(await articleFile.readAsString()) as Map));
        if (article.id == entry.id && article.bodyHtml.trim().isNotEmpty) articleBytes = entry.articleBytes;
      } catch (_) { /* Only verified text is shown as retained. */ }
    }
    final files = <OfflineFile>[];
    for (final f in entry.files) {
      final file = File(p.join(_root!.path, entry.directory, f.name));
      if (await file.exists() && await file.length() == f.bytes) files.add(f);
    }
    return entry.verified(articleBytes: articleBytes, files: files);
  }

  Future<void> _cleanOrphans(List<OfflineEntry> entries) async {
    final retained = entries.map((e) => e.directory).toSet();
    await for (final entity in _root!.list(followLinks: false)) {
      final name = p.basename(entity.path);
      if (entity is Directory && !retained.contains(name)) await entity.delete(recursive: true);
      if (entity is File && name.endsWith('.tmp')) await entity.delete();
    }
  }

  Future<void> _queue(Future<void> Function() action) {
    final result = _writes.then((_) => action());
    _writes = result.catchError((Object _) {});
    return result;
  }

  Future<void> _writeManifest(List<OfflineEntry> entries) async {
    final data = utf8.encode(jsonEncode({'version': 1, 'entries': entries.map((e) => e.toJson()).toList()}));
    if (data.length > maxManifestBytes) throw const FileSystemException('Offline index full');
    final temporary = File(p.join(_root!.path, 'manifest.tmp'));
    await temporary.writeAsBytes(data, flush: true);
    await temporary.rename(p.join(_root!.path, 'manifest.json'));
  }

  Future<void> keepArticle(OfflineArticle article) => _keep(
    id: article.id, title: article.title, source: article.source, url: article.url,
    article: article, media: articleImageSources(article));

  Future<void> keepMedia({required String id, required String title, required String source,
    required List<OfflineMediaSource> media, String? url, bool sensitive = false}) =>
    _keep(id: id, title: title, source: source, url: url, media: media, sensitive: sensitive);

  Future<void> _keep({required String id, required String title, required String source,
    required List<OfflineMediaSource> media, String? url, OfflineArticle? article,
    bool sensitive = false}) {
    final generation = (_generations[id] ?? 0) + 1;
    _generations[id] = generation;
    _emit(busy: {...state.busy, id}, failed: {...state.failed}..remove(id));
    return _queue(() async {
      Directory? staging;
      try {
        await load();
        if (state.storageError || _root == null) throw const FileSystemException('Offline storage unavailable');
        if (_cancelled(id, generation)) return;
        if ((state.entries.length >= maxItems && state.entry(id) == null) || media.length > 10000) throw const FileSystemException('Offline library full');
        final name = '${sha256.convert(utf8.encode(id))}-${DateTime.now().microsecondsSinceEpoch}';
        staging = Directory(p.join(_root!.path, '$name.tmp'));
        await staging.create();
        // Keep the previous copy until the replacement is durable; both must fit.
        final available = storageLimit - state.bytes;
        final bodyBytes = article == null ? <int>[] : utf8.encode(jsonEncode(article.toJson()));
        if (bodyBytes.length > maxArticleBytes || bodyBytes.length > available ||
            (article != null && article.bodyHtml.trim().isEmpty)) throw const FileSystemException('Article unavailable or too large');
        if (article != null) await File(p.join(staging.path, 'article.json')).writeAsBytes(bodyBytes, flush: true);
        final mediaBudget = article == null ? available : min(available - bodyBytes.length, maxArticleImageBytes);
        final files = await _retainFiles(staging, media, mediaBudget, id, generation);
        if (_cancelled(id, generation)) return;
        if (article == null && files.isEmpty) throw const FileSystemException('No offline media retained');
        final entry = OfflineEntry(id: id, directory: name, title: title, source: source,
          url: url, kind: article == null ? OfflineKind.media : OfflineKind.article,
          articleBytes: bodyBytes.length, totalMedia: media.length, files: List.unmodifiable(files),
          sensitive: sensitive, retainedAt: DateTime.now());
        await _commit(staging, entry);
        staging = null;
      } catch (_) {
        if (!_cancelled(id, generation)) _emit(failed: {...state.failed, id});
      } finally {
        if (staging != null && await staging.exists()) await staging.delete(recursive: true);
        if (_generations[id] == generation) _emit(busy: {...state.busy}..remove(id));
      }
    });
  }

  bool _cancelled(String id, int generation) => _disposed || _generations[id] != generation;

  Future<List<OfflineFile>> _retainFiles(Directory staging, List<OfflineMediaSource> media,
    int budget, String id, int generation) async {
    final files = <OfflineFile>[];
    for (var i = 0; i < min(media.length, maxMedia); i++) {
      if (_cancelled(id, generation) || budget <= 0) break;
      try {
        final file = await retainOfflineMedia(client: _client, source: media[i],
          target: File(p.join(staging.path, 'media-$i')),
          limit: min(budget, media[i].video ? videoLimit : imageLimit),
          cancelled: () => _cancelled(id, generation));
        files.add(file);
        budget -= file.bytes;
      } catch (_) { /* Partial image availability is reported by the manifest. */ }
    }
    return files;
  }

  Future<void> _commit(Directory staging, OfflineEntry entry) async {
    final finalDirectory = await staging.rename(p.join(_root!.path, entry.directory));
    final old = state.entry(entry.id);
    final next = [entry, ...state.entries.where((e) => e.id != entry.id)];
    try {
      await _writeManifest(next);
    } catch (_) {
      await finalDirectory.delete(recursive: true);
      rethrow;
    }
    _emit(entries: next, failed: {...state.failed}..remove(entry.id));
    if (old != null) await _deleteEntryDirectory(old);
  }

  Future<void> _deleteEntryDirectory(OfflineEntry entry) async {
    final directory = Directory(p.join(_root!.path, entry.directory));
    if (await directory.exists()) await directory.delete(recursive: true);
  }

  Future<void> remove(String id) {
    _generations[id] = (_generations[id] ?? 0) + 1;
    _emit(busy: {...state.busy}..remove(id), failed: {...state.failed}..remove(id));
    return _queue(() async {
      try {
        await load();
        final entry = state.entry(id);
        if (entry == null || _root == null) return;
        await _deleteEntryDirectory(entry);
        final next = state.entries.where((e) => e.id != id).toList();
        await _writeManifest(next);
        _emit(entries: next);
      } catch (_) { await _reflectStorageFailure(); }
    });
  }

  Future<void> clear() async {
    for (final id in _generations.keys.toList()) { _generations[id] = _generations[id]! + 1; }
    _emit(busy: {}, failed: {});
    await _queue(() async {
      try {
        await load();
        if (_root == null) return;
        for (final entry in state.entries) { await _deleteEntryDirectory(entry); }
        await _writeManifest([]);
        await _cleanOrphans([]);
        _emit(entries: [], storageError: false);
      } catch (_) { await _reflectStorageFailure(); }
    });
  }

  Future<void> _reflectStorageFailure() async {
    final actual = <OfflineEntry>[];
    for (final entry in state.entries) {
      try { actual.add(await _verify(entry)); }
      catch (_) { actual.add(entry.verified(articleBytes: 0, files: [])); }
    }
    _emit(entries: actual, storageError: true);
  }

  Future<OfflineArticle?> article(String id, {String? canonicalUrl}) async {
    await load();
    var entry = state.entry(id);
    if (entry == null && canonicalUrl != null) {
      final canonical = _canonicalArticleUrl(canonicalUrl);
      for (final candidate in state.entries) {
        if (candidate.hasArticle && canonical != null && _canonicalArticleUrl(candidate.url) == canonical) {
          entry = candidate;
          break;
        }
      }
    }
    if (entry == null || !entry.hasArticle || _root == null) return null;
    try {
      final file = File(p.join(_root!.path, entry.directory, 'article.json'));
      if (await file.length() != entry.articleBytes) return null;
      final article = OfflineArticle.fromJson(Map<String, dynamic>.from(jsonDecode(await file.readAsString()) as Map));
      return article.id == entry.id ? article : null;
    } catch (_) { return null; }
  }

  Future<File?> mediaFile(OfflineEntry entry, OfflineFile media) async {
    await load();
    if (_root == null || !_safeEntry(entry) || !entry.files.contains(media)) return null;
    final file = File(p.join(_root!.path, entry.directory, media.name));
    return await file.exists() && await file.length() == media.bytes ? file : null;
  }

  Future<String> renderArticle(String id, String sanitizedDocument) async {
    await load();
    final entry = state.entry(id);
    if (entry == null || !entry.hasArticle) return sanitizedDocument;
    final document = html.parse(sanitizedDocument);
    document.head?.nodes.insert(0, Element.tag('meta')
      ..attributes['http-equiv'] = 'Content-Security-Policy'
      ..attributes['content'] = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; form-action 'none'");
    final files = {for (final file in entry.files) file.url: file};
    for (final image in document.querySelectorAll('img')) {
      final resolved = resolveOfflineUrl(image.attributes['src'], entry.url);
      final media = files[resolved];
      final file = media == null ? null : await mediaFile(entry, media);
      image.attributes.remove('srcset');
      image.attributes.remove('onerror');
      if (file == null) { image.attributes.remove('src'); continue; }
      final bytes = await file.readAsBytes();
      final mime = offlineImageMime(bytes);
      if (mime == null) { image.attributes.remove('src'); continue; }
      image.attributes['src'] = 'data:$mime;base64,${base64Encode(bytes)}';
    }
    for (final source in document.querySelectorAll('source,video,audio,iframe,object,embed')) { source.remove(); }
    return document.outerHtml;
  }

  @override
  Future<void> destroy() async {
    _disposed = true;
    if (_ownsClient) _client.close();
    await _writes;
    await super.destroy();
  }
}

String? _canonicalArticleUrl(String? raw) {
  final uri = Uri.tryParse(raw ?? '');
  if (uri == null || uri.host.isEmpty) return null;
  return uri.replace(fragment: '', path: uri.path.replaceAll(RegExp(r'/+$'), '')).toString();
}

String? resolveOfflineUrl(String? raw, String? base) {
  if (raw == null || raw.trim().isEmpty) return null;
  final uri = Uri.tryParse(raw.trim());
  if (uri == null) return null;
  final resolved = uri.hasScheme ? uri : Uri.tryParse(base ?? '')?.resolveUri(uri);
  return resolved != null && ['http', 'https'].contains(resolved.scheme) && resolved.host.isNotEmpty ? resolved.toString() : null;
}

List<OfflineMediaSource> articleImageSources(OfflineArticle article) {
  final document = html.parseFragment(article.bodyHtml);
  final urls = <String>{};
  final cover = resolveOfflineUrl(article.payload['imageUrl'] as String? ?? article.payload['cover_image'] as String?, article.url);
  if (cover != null) urls.add(cover);
  for (final image in document.querySelectorAll('img')) {
    final url = resolveOfflineUrl(image.attributes['src'] ?? image.attributes['data-src'], article.url);
    if (url != null) urls.add(url);
  }
  return urls.map(OfflineMediaSource.new).toList();
}
