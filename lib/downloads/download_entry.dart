import 'package:path/path.dart' as p;

enum DownloadStatus { queued, downloading, choosingLocation, saving, completed, failed, cancelled, interrupted }

class DownloadEntry {
  final String id;
  final Uri uri;
  final String fileName;
  final String? treeUri;
  final DownloadStatus status;
  final DateTime createdAt;
  final int received;
  final int? total;
  final String? savedUri;

  const DownloadEntry({required this.id, required this.uri, required this.fileName, this.treeUri,
    required this.createdAt, this.status = DownloadStatus.queued, this.received = 0, this.total, this.savedUri});

  bool get active => switch (status) {
    DownloadStatus.queued || DownloadStatus.downloading || DownloadStatus.saving || DownloadStatus.choosingLocation => true,
    _ => false,
  };
  bool get canRetry => status == DownloadStatus.failed || status == DownloadStatus.interrupted || status == DownloadStatus.cancelled;
  bool get canCancel => active && status != DownloadStatus.choosingLocation;

  DownloadEntry copyWith({DownloadStatus? status, int? received, int? total, String? savedUri, bool reset = false}) =>
    DownloadEntry(id: id, uri: uri, fileName: fileName, treeUri: treeUri, createdAt: createdAt,
      status: status ?? this.status, received: reset ? 0 : received ?? this.received,
      total: reset ? null : total ?? this.total, savedUri: reset ? null : savedUri ?? this.savedUri);

  Map<String, Object?> toJson() => {'id': id, 'url': uri.toString(), 'name': fileName, 'tree': treeUri,
    'status': status.name, 'created': createdAt.toIso8601String(), 'received': received, 'total': total, 'saved': savedUri};

  static DownloadEntry? fromJson(Object? value) {
    if (value is! Map) return null;
    final id = value['id'];
    final url = value['url'];
    final name = value['name'];
    final created = value['created'];
    if (id is! String || !RegExp(r'^[a-zA-Z0-9_-]{1,100}$').hasMatch(id) || url is! String || url.length > 16000 ||
        name is! String || name.length > 240 || created is! String) return null;
    final uri = Uri.tryParse(url);
    final date = DateTime.tryParse(created);
    if (uri == null || !['http', 'https'].contains(uri.scheme) || uri.host.isEmpty || date == null) return null;
    final statuses = DownloadStatus.values.where((s) => s.name == value['status']);
    if (statuses.isEmpty) return null;
    final entry = DownloadEntry(id: id, uri: uri, fileName: safeDownloadName(name), createdAt: date,
      treeUri: value['tree'] is String ? value['tree'] as String : null,
      status: statuses.first, received: value['received'] is int ? (value['received'] as int).clamp(0, 1 << 53) : 0,
      total: value['total'] is int ? (value['total'] as int).clamp(0, 1 << 53) : null,
      savedUri: value['saved'] is String ? value['saved'] as String : null);
    return entry.active ? entry.copyWith(status: DownloadStatus.interrupted) : entry;
  }
}

String safeDownloadName(String value) {
  final name = p.basename(value.replaceAll('\\', '/').split('?').first)
    .replaceAll(RegExp(r'[\x00-\x1f<>:"|?*]'), '_');
  if (name.isEmpty || name == '.' || name == '..') return 'xta-media';
  return name.length > 240 ? name.substring(name.length - 240) : name;
}

Uri originalDownloadUri(Uri uri) {
  if (uri.host.toLowerCase() != 'pbs.twimg.com') return uri;
  if (uri.queryParameters.containsKey('format')) {
    return uri.replace(queryParameters: {...uri.queryParameters, 'name': 'orig'});
  }
  final path = uri.path.replaceFirst(RegExp(r':(small|medium|large|thumb|orig)$'), '');
  return uri.replace(path: '$path:orig');
}

class DownloadRequest {
  final Uri uri;
  final String fileName;
  final String? treeUri;
  const DownloadRequest({required this.uri, required this.fileName, this.treeUri});
}

class DownloadBatchResult {
  final int saved;
  final int total;
  const DownloadBatchResult({required this.saved, required this.total});
}
