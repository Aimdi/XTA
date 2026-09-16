import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/note_editor_store.dart';
import 'package:xta/utils/local_json_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('draft restores text, attachment identity and target after interruption', () async {
    final dir = await Directory.systemTemp.createTemp('draft-test');
    final storage = LocalJsonStore(directory: () async => dir);
    final first = NoteEditorStore(draftKey: 'new', postId: 'original', storage: storage);
    await first.restoreDraft();
    first.setBody('a thought');
    await first.attach(() async => const LocalPostMedia(id: 'image', name: 'image.png', mime: 'image/png'));
    await first.persistDraft();
    await first.destroy();
    final next = NoteEditorStore(draftKey: 'new', postId: 'different', storage: storage);
    await next.restoreDraft();
    expect(next.state.body, 'a thought');
    expect(next.state.media.single.id, 'image');
    expect(next.postId, 'original');
    expect(await next.save(() async => throw StateError('disk full')), isFalse);
    expect(await storage.read('draft:new'), isNotNull);
    expect(await next.save(() async {}), isTrue);
    expect(await storage.read('draft:new'), isNull);
    await next.destroy();
    await dir.delete(recursive: true);
  });
  test('discard is scoped and cannot be resurrected by a queued write', () async {
    final dir = await Directory.systemTemp.createTemp('draft-test');
    final storage = LocalJsonStore(directory: () async => dir);
    await storage.write('draft:saved:other', {'body': 'other'});
    final store = NoteEditorStore(draftKey: 'saved:one', storage: storage);
    await store.restoreDraft();
    store.setBody('discard me');
    expect(await store.discard(), isTrue);
    await store.persistDraft();
    expect(await storage.read('draft:saved:one'), isNull);
    expect(await storage.read('draft:saved:other'), isNotNull);
    await store.destroy();
    await dir.delete(recursive: true);
  });
}
