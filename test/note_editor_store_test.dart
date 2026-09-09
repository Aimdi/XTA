import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/note_editor_store.dart';

void main() {
  const photo = LocalPostMedia(id: 'photo', name: 'photo.jpg', mime: 'image/jpeg');

  test('cancel detection includes attachments and restored original body', () async {
    final store = NoteEditorStore(body: 'Original', media: [photo]);
    addTearDown(store.destroy);
    expect(store.dirty, isFalse);
    store.setBody('Changed');
    expect(store.dirty, isTrue);
    store.setBody('Original');
    expect(store.dirty, isFalse);
    store.removeMedia('photo');
    expect(store.dirty, isTrue);
    expect(store.initialMedia, [photo]);
    await store.attach(() async => photo);
    expect(store.dirty, isFalse);
  });

  test('failed save retains draft and attachments and can be retried', () async {
    final store = NoteEditorStore(media: [photo]);
    addTearDown(store.destroy);
    store.setBody('Keep this draft');
    expect(await store.save(() async => throw StateError('disk full')), isFalse);
    expect(store.state.saveFailed, isTrue);
    expect(store.state.saved, isFalse);
    expect(store.state.busy, isFalse);
    expect(store.state.body, 'Keep this draft');
    expect(store.state.media, [photo]);
    expect(await store.save(() async {}), isTrue);
    expect(store.state.saveFailed, isFalse);
    expect(store.state.saved, isTrue);
  });

  test('save blocks duplicate saves and attachment mutation until complete', () async {
    final store = NoteEditorStore(body: 'Note', media: [photo]);
    addTearDown(store.destroy);
    final pending = Completer<void>();
    final saving = store.save(() => pending.future);
    var duplicateCalls = 0;
    expect(
      await store.save(() async {
        duplicateCalls++;
      }),
      isFalse,
    );
    await store.attach(() async {
      duplicateCalls++;
      return photo;
    });
    store.removeMedia('photo');
    expect(duplicateCalls, 0);
    expect(store.state.media, [photo]);
    pending.complete();
    expect(await saving, isTrue);
  });

  test('cancelled and failed pickers preserve existing attachments', () async {
    final store = NoteEditorStore(media: [photo]);
    addTearDown(store.destroy);
    await store.attach(() async => null);
    expect(store.dirty, isFalse);
    await store.attach(() async => throw StateError('unreadable file'));
    expect(store.state.attachFailed, isTrue);
    expect(store.state.busy, isFalse);
    expect(store.state.media, [photo]);
    expect(store.dirty, isFalse);
  });
}
