enum OfflineKind { article, media }

enum OfflineAvailability { available, partial, unavailable }

class OfflineMediaSource {
  final String url;
  final bool video;
  const OfflineMediaSource(this.url, {this.video = false});
}

class OfflineFile {
  final String name;
  final String url;
  final String mime;
  final int bytes;
  const OfflineFile({required this.name, required this.url, required this.mime, required this.bytes});
  bool get video => mime.startsWith('video/');
  Map<String, dynamic> toJson() => {'name': name, 'url': url, 'mime': mime, 'bytes': bytes};
  factory OfflineFile.fromJson(Map<String, dynamic> json) => OfflineFile(
    name: json['name'] as String,
    url: json['url'] as String,
    mime: json['mime'] as String,
    bytes: json['bytes'] as int,
  );
}

class OfflineEntry {
  final String id;
  final String directory;
  final String title;
  final String source;
  final String? url;
  final OfflineKind kind;
  final int articleBytes;
  final int totalMedia;
  final List<OfflineFile> files;
  final bool sensitive;
  final DateTime retainedAt;
  const OfflineEntry({
    required this.id,
    required this.directory,
    required this.title,
    required this.source,
    required this.kind,
    required this.totalMedia,
    required this.files,
    required this.retainedAt,
    this.url,
    this.articleBytes = 0,
    this.sensitive = false,
  });

  int get bytes => articleBytes + files.fold<int>(0, (total, file) => total + file.bytes);
  bool get hasArticle => kind == OfflineKind.article && articleBytes > 0;
  bool get canOpen => hasArticle || (kind == OfflineKind.media && files.isNotEmpty);
  OfflineAvailability get availability {
    if (!canOpen) return OfflineAvailability.unavailable;
    return files.length == totalMedia ? OfflineAvailability.available : OfflineAvailability.partial;
  }

  OfflineEntry verified({required int articleBytes, required List<OfflineFile> files}) => OfflineEntry(
    id: id,
    directory: directory,
    title: title,
    source: source,
    kind: kind,
    totalMedia: totalMedia,
    files: List.unmodifiable(files),
    retainedAt: retainedAt,
    url: url,
    articleBytes: articleBytes,
    sensitive: sensitive,
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'directory': directory,
    'title': title,
    'source': source,
    'kind': kind.name,
    'totalMedia': totalMedia,
    'files': files.map((f) => f.toJson()).toList(),
    'retainedAt': retainedAt.toIso8601String(),
    'url': url,
    'articleBytes': articleBytes,
    'sensitive': sensitive,
  };

  factory OfflineEntry.fromJson(Map<String, dynamic> json) => OfflineEntry(
    id: json['id'] as String,
    directory: json['directory'] as String,
    title: json['title'] as String,
    source: json['source'] as String,
    kind: OfflineKind.values.byName(json['kind'] as String),
    totalMedia: json['totalMedia'] as int,
    files: (json['files'] as List).map((f) => OfflineFile.fromJson(Map<String, dynamic>.from(f as Map))).toList(),
    retainedAt: DateTime.parse(json['retainedAt'] as String),
    url: json['url'] as String?,
    articleBytes: json['articleBytes'] as int? ?? 0,
    sensitive: json['sensitive'] == true,
  );
}

class OfflineState {
  final List<OfflineEntry> entries;
  final Set<String> busy;
  final Set<String> failed;
  final bool loading;
  final bool storageError;
  const OfflineState({
    this.entries = const [],
    this.busy = const {},
    this.failed = const {},
    this.loading = true,
    this.storageError = false,
  });
  int get bytes => entries.fold(0, (total, entry) => total + entry.bytes);
  OfflineEntry? entry(String id) {
    for (final entry in entries) {
      if (entry.id == id) return entry;
    }
    return null;
  }
}
