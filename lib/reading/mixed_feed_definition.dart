const mixedFeedPreferenceKey = 'reading.mixes.v1';
const mixedFeedMaxMixes = 50;
const mixedFeedMaxSources = 16;
const mixedFeedMaxName = 80;
const _maxKind = 64;
const _maxValue = 2048;
const _maxLabel = 200;
const _tabPrefix = 'mix:';

enum MixedFeedOrder { chronological, alternating }

enum MixedFeedProblem { emptyName, noSources, tooManySources }

String _bounded(String text, int max) => text.length > max ? text.substring(0, max) : text;

/// One source of a mix, by what it reads: a kind such as `rss.feed` and the native id it reads there (a feed id, a
/// tag, a feed URI). The label is what the reader saw when adding it; changing it never refetches.
class MixedFeedSource {
  final String kind;
  final String value;
  final String label;
  const MixedFeedSource({required this.kind, this.value = '', required this.label});

  /// What the source reads; two sources with the same key read the same posts.
  String get key => '$kind\u0000$value';

  Map<String, Object?> toJson() => {'kind': kind, 'value': value, 'label': label};

  static MixedFeedSource? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final kind = raw['kind'];
    final value = raw['value'] ?? '';
    final label = raw['label'] ?? '';
    if (kind is! String || kind.isEmpty || kind.length > _maxKind) return null;
    if (value is! String || value.length > _maxValue || label is! String) return null;
    return MixedFeedSource(kind: kind, value: value, label: _bounded(label, _maxLabel));
  }

  @override
  bool operator ==(Object other) =>
      other is MixedFeedSource && other.kind == kind && other.value == value && other.label == label;

  @override
  int get hashCode => Object.hash(kind, value, label);
}

/// A named timeline made of several sources, shown in one list.
class MixedFeedDefinition {
  final String id;
  final String name;
  final List<MixedFeedSource> sources;
  final MixedFeedOrder order;

  const MixedFeedDefinition({
    required this.id,
    required this.name,
    this.sources = const [],
    this.order = MixedFeedOrder.chronological,
  });

  /// The id of this mix on the Home feed strip.
  String get tabId => '$_tabPrefix$id';

  MixedFeedProblem? get problem {
    if (name.trim().isEmpty) return MixedFeedProblem.emptyName;
    if (sources.isEmpty) return MixedFeedProblem.noSources;
    if (sources.length > mixedFeedMaxSources) return MixedFeedProblem.tooManySources;
    return null;
  }

  MixedFeedDefinition copyWith({String? name, List<MixedFeedSource>? sources, MixedFeedOrder? order}) =>
      MixedFeedDefinition(
        id: id,
        name: name ?? this.name,
        sources: sources ?? this.sources,
        order: order ?? this.order,
      );

  Map<String, Object?> toJson() => {
    'id': id,
    'name': name,
    'order': order.name,
    'sources': [for (final source in sources) source.toJson()],
  };

  /// A saved mix, or null when it is unusable. Repeated sources are dropped.
  static MixedFeedDefinition? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final id = raw['id'];
    final name = raw['name'];
    final sources = raw['sources'];
    if (id is! String || id.isEmpty || id.length > _maxKind || name is! String || sources is! List) return null;
    final keys = <String>{};
    final mix = MixedFeedDefinition(
      id: id,
      name: _bounded(name.trim(), mixedFeedMaxName),
      order: MixedFeedOrder.values.asNameMap()[raw['order']] ?? MixedFeedOrder.chronological,
      sources: List.unmodifiable(
        sources
            .map(MixedFeedSource.fromJson)
            .nonNulls
            .where((source) => keys.add(source.key))
            .take(mixedFeedMaxSources),
      ),
    );
    return mix.problem == null ? mix : null;
  }
}

bool isMixedFeedTab(String tabId) => tabId.startsWith(_tabPrefix);

/// The mix a Home feed strip id names, or null when it names something else.
String? mixedFeedIdOfTab(String tabId) => isMixedFeedTab(tabId) ? tabId.substring(_tabPrefix.length) : null;
