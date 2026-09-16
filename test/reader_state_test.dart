import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/archive/archive_notes.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/search/reader_search_store.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/local_json_store.dart';

class MemoryJson implements JsonStore {
  final values = <String, Object?>{};
  @override
  Future<Object?> read(String key) async => values[key];
  @override
  Future<void> write(String key, Object? value) async {
    values[key] = value;
  }

  @override
  Future<void> remove(String key) async {
    values.remove(key);
  }

  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async => {
    for (final e in values.entries)
      if (e.key.startsWith(prefix)) e.key: e.value,
  };
}

const candidate = DiscoveryAccount(
  source: DiscoverySource.bluesky,
  id: 'did:one',
  handle: 'one.test',
  name: 'Space',
  text: 'astronomy telescope research',
  postUrl: 'https://bsky.app/profile/one.test',
);
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('local search stays available while archive loads and keeps the latest query', () async {
    final store = ReaderSearchStore();
    final archive = Completer<List<ReaderSearchDocument>>();
    final load = store.load([
      const ReaderSearchDocument(
        id: 'local',
        title: 'Astronomy',
        text: 'notes',
        kind: ReaderSearchKind.note,
        target: 'local',
      ),
    ], () => archive.future);
    store.query('astronomy');
    expect(store.state.results.single.id, 'local');
    store.query('history');
    archive.complete([
      const ReaderSearchDocument(
        id: 'archive',
        title: 'History',
        text: 'retained article',
        kind: ReaderSearchKind.article,
        target: 'archive',
      ),
    ]);
    await load;
    expect(store.state.results.single.id, 'archive');
    store.filter(ReaderSearchKind.note);
    expect(store.state.results, isEmpty);
    await store.destroy();
  });
  test('highlight notes, selected tags and OCR survive reopening', () async {
    final storage = MemoryJson();
    final store = ArchiveNotesStore('article', storage: storage);
    await store.load();
    await Future.wait([
      store.highlight('quoted passage', 'My interpretation'),
      store.toggleTag('astronomy'),
      store.toggleTag('research'),
    ]);
    await store.extracted('Visible screenshot text');
    final restored = ArchiveNotesStore('article', storage: storage);
    await restored.load();
    expect(restored.state.highlights['quoted passage'], 'My interpretation');
    expect(restored.state.tags, {'astronomy', 'research'});
    expect(restored.state.searchable, contains('Visible screenshot text'));
    expect(archivePlainText('<style>hidden</style><p>Retained article</p><script>bad()</script>'), 'Retained article');
    expect(suggestedArchiveTags('Astronomy astronomy research the and'), contains('astronomy'));
    await store.destroy();
    await restored.destroy();
  });
  test('dismissal persists per group and undo restores suggestions', () async {
    final storage = MemoryJson();
    final store = GroupDiscoveryStore(storage: storage);
    await store.load(
      sources: [
        () async => [candidate],
      ],
      followed: {},
      groupName: 'Space',
      groupId: 'one',
    );
    await store.feedback(candidate, 0);
    expect(store.state.accounts, isEmpty);
    final restored = GroupDiscoveryStore(storage: storage);
    await restored.load(
      sources: [
        () async => [candidate],
      ],
      followed: {},
      groupName: 'Renamed',
      groupId: 'one',
    );
    expect(restored.state.accounts, isEmpty);
    await store.resetFeedback(undo: true);
    expect(store.state.accounts.single.key, candidate.key);
    await restored.load(
      sources: [
        () async => [candidate],
      ],
      followed: {},
      groupName: 'Other',
      groupId: 'two',
    );
    expect(restored.state.accounts.single.key, candidate.key);
    await store.destroy();
    await restored.destroy();
  });
  test('profile shows persisted content while refresh fails', () async {
    final storage = MemoryJson();
    final profile = Profile(
      UserWithExtra.fromArguments(idStr: '1', screenName: 'reader', name: 'Reader', possiblySensitive: true),
      [],
    );
    final first = ProfileModel(storage: storage, byId: (_) async => profile);
    await first.loadProfileById('1');
    await Future<void>.delayed(Duration.zero);
    final request = Completer<Profile>();
    final reopened = ProfileModel(storage: storage, byId: (_) => request.future);
    final loading = reopened.loadProfileById('1');
    await Future<void>.delayed(Duration.zero);
    expect(reopened.state.user.name, 'Reader');
    expect(reopened.state.user.possiblySensitive, isTrue);
    expect(reopened.state.refreshing, isTrue);
    request.completeError(TimeoutException('network'));
    await loading;
    expect(reopened.state.user.name, 'Reader');
    expect(reopened.state.refreshError, isA<TimeoutException>());
    await first.destroy();
    await reopened.destroy();
  });
}
