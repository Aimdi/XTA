import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_download_index.dart';

import 'support/pixiv_reader_harness.dart';

void main() {
  PrefServiceCache prefs([String stored = '[]']) => PrefServiceCache(cache: {optionPluginPixivDownloadIndex: stored});

  test('records pages and answers what is saved', () async {
    final index = PixivDownloadIndex(prefs());
    addTearDown(index.destroy);
    final work = pixivWork(pages: 4);

    await index.record(120, [0, 2]);

    expect(index.isSaved(120, 0), isTrue);
    expect(index.isSaved(120, 1), isFalse);
    expect(index.isSaved(121, 0), isFalse);
    expect(index.savedAmong(work, [0, 1, 2, 3]), [0, 2]);
    expect(index.savedCount(work), 2);
  });

  test('is kept in settings, newest last, and read back on the next launch', () async {
    final store = prefs();
    final index = PixivDownloadIndex(store);
    addTearDown(index.destroy);
    await index.record(1, [0]);
    await index.record(2, [0]);
    await index.record(1, [0]);

    expect(jsonDecode(store.get<String>(optionPluginPixivDownloadIndex)!), ['2_p0', '1_p0']);
    final reopened = PixivDownloadIndex(store);
    addTearDown(reopened.destroy);
    expect(reopened.isSaved(2, 0), isTrue);
  });

  test('forgets the oldest pages beyond the cap', () async {
    final stored = jsonEncode([for (var id = 0; id < pixivDownloadIndexCap; id++) '${id}_p0']);
    final index = PixivDownloadIndex(prefs(stored));
    addTearDown(index.destroy);
    expect(index.state, hasLength(pixivDownloadIndexCap));

    await index.record(999999, [0, 1]);

    expect(index.state, hasLength(pixivDownloadIndexCap));
    expect(index.isSaved(0, 0), isFalse);
    expect(index.isSaved(1, 0), isFalse);
    expect(index.isSaved(2, 0), isTrue);
    expect(index.isSaved(999999, 1), isTrue);
  });

  test('a broken or reshaped stored list reads as what it validly holds', () {
    PixivDownloadIndex open(String stored) {
      final index = PixivDownloadIndex(prefs(stored));
      addTearDown(index.destroy);
      return index;
    }

    expect(open('not json').state, isEmpty);
    expect(open('{"120_p0": true}').state, isEmpty);
    expect(open('["120_p0", 5, null, "x_p1", "../1_p0", "7_p2", "7_p2"]').state, {'120_p0', '7_p2'});
  });

  test('clear forgets everything, in memory and in settings', () async {
    final store = prefs('["1_p0"]');
    final index = PixivDownloadIndex(store);
    addTearDown(index.destroy);
    await index.clear();
    expect(index.state, isEmpty);
    expect(store.get<String>(optionPluginPixivDownloadIndex), '[]');
  });

  test('cappedPixivIndex folds repeats and keeps the newest', () {
    expect(cappedPixivIndex(['a', 'b', 'a']), {'a', 'b'});
    final many = [for (var i = 0; i < pixivDownloadIndexCap + 3; i++) '${i}_p0'];
    expect(cappedPixivIndex(many).first, '3_p0');
  });
}
