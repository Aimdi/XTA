import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/reader_preference_writes.dart';
import 'package:xta/reading/reading_history_entry.dart';
import 'package:xta/utils/local_json_store.dart';

const readingHistoryStorageKey = 'reading-history';
const readingHistoryEnabledKey = 'reading.history.enabled';
const readingHistoryLimit = 500;

class ReadingHistoryState {
  final List<ReadingHistoryEntry> entries;
  final bool enabled;
  final bool loaded;
  const ReadingHistoryState({this.entries = const [], this.enabled = true, this.loaded = false});

  List<ReadingHistoryEntry> search(String query, {String? source}) => [
    for (final entry in entries)
      if ((source == null || entry.source == source) && entry.matches(query)) entry,
  ];

  Set<String> get sources => {for (final entry in entries) entry.source};
}

/// What the reader has actually viewed, newest first, kept on this device only.
///
/// Owned by the storage it writes to, never by a screen, so every reader and search share one history.
class ReadingHistoryStore extends Store<ReadingHistoryState> {
  static final _instances = Expando<ReadingHistoryStore>();
  final JsonStore storage;
  final BasePrefService prefs;
  final int limit;
  final DateTime Function() clock;
  final _suppressed = <String>{};
  Future<void>? _loading;
  Future<bool>? _writing;
  bool _dirty = false;
  int _epoch = 0;
  bool _closed = false;

  ReadingHistoryStore(
    this.storage,
    this.prefs, {
    this.limit = readingHistoryLimit,
    DateTime Function()? clock,
  }) : clock = clock ?? DateTime.now,
       super(ReadingHistoryState(enabled: prefs.get<bool>(readingHistoryEnabledKey) ?? true));

  static ReadingHistoryStore forPrefs(BasePrefService prefs, {JsonStore? storage}) {
    final store = storage ?? LocalJsonStore.shared;
    final existing = _instances[prefs];
    if (existing != null && identical(existing.storage, store)) return existing;
    return _instances[prefs] = ReadingHistoryStore(store, prefs);
  }

  /// Bumped by clearing; a view that began before it is not recorded afterwards.
  int get epoch => _epoch;

  Future<void> load() => _loading ??= _read();

  Future<void> _read() async {
    List<ReadingHistoryEntry> stored = const [];
    try {
      final raw = await storage.read(readingHistoryStorageKey);
      if (raw is Map && raw['version'] == 1 && raw['entries'] is List) {
        stored = (raw['entries'] as List).map(ReadingHistoryEntry.fromJson).nonNulls.take(limit).toList();
      }
    } catch (_) {
      // Unreadable history starts empty rather than blocking the reader.
    }
    if (_closed) return;
    final recorded = state.entries;
    final keys = {for (final entry in recorded) entry.key};
    update(
      ReadingHistoryState(
        entries: List.unmodifiable([...recorded, ...stored.where((entry) => !keys.contains(entry.key))].take(limit)),
        enabled: state.enabled,
        loaded: true,
      ),
    );
  }

  /// Adds [entry] at the top, replacing an earlier view of the same item.
  void record(ReadingHistoryEntry entry, {int? epoch}) {
    if (_closed || !state.enabled || !entry.valid || _suppressed.contains(entry.key)) return;
    if (epoch != null && epoch != _epoch) return;
    final viewed = entry.viewedAgain(clock());
    final rest = state.entries.where((old) => old.key != entry.key);
    _publish([viewed, ...rest].take(limit).toList());
    flush();
  }

  void _publish(List<ReadingHistoryEntry> entries) =>
      update(ReadingHistoryState(entries: List.unmodifiable(entries), enabled: state.enabled, loaded: state.loaded));

  /// Writes the current history. Changes made while a write is running are written right after it,
  /// so a burst of views costs two writes. False when the device refused the latest one.
  Future<bool> flush() {
    _dirty = true;
    return _writing ??= _writeWhileDirty().whenComplete(() => _writing = null);
  }

  Future<bool> _writeWhileDirty() async {
    await load();
    var written = false;
    while (_dirty) {
      _dirty = false;
      try {
        await storage.write(readingHistoryStorageKey, {
          'version': 1,
          'entries': [for (final entry in state.entries) entry.toJson()],
        });
        written = true;
      } catch (_) {
        written = false;
      }
    }
    return written;
  }

  /// Removes one item; a card still on screen does not record it again this session.
  Future<bool> remove(String key) async {
    await load();
    _suppressed.add(key);
    _publish(state.entries.where((entry) => entry.key != key).toList());
    return flush();
  }

  /// Erases everything. False when the device could not confirm the erase, so the reader is told.
  Future<bool> clear() async {
    await load();
    ++_epoch;
    _publish(const []);
    return flush();
  }

  /// Pausing stops new records, including any already waiting, and keeps what is stored.
  Future<bool> setEnabled(bool enabled) async {
    final saved = await ReaderPreferenceWrites.enqueue(prefs, () async {
      try {
        return await prefs.put(readingHistoryEnabledKey, enabled);
      } catch (_) {
        return false;
      }
    });
    if (saved && !_closed) {
      if (!enabled) ++_epoch;
      update(ReadingHistoryState(entries: state.entries, enabled: enabled, loaded: state.loaded));
    }
    return saved;
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    if (identical(_instances[prefs], this)) _instances[prefs] = null;
    await _writing;
    await super.destroy();
  }
}
