import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

/// About as many pages as a year of heavy saving; the oldest are forgotten first.
const pixivDownloadIndexCap = 5000;

String pixivPageKey(int illustId, int page) => '${illustId}_p$page';

final _pageKey = RegExp(r'^\d{1,12}_p\d{1,4}$');

/// The saved pages stored under [optionPluginPixivDownloadIndex], oldest first.
Set<String> readPixivDownloadIndex(BasePrefService prefs) {
  Object? decoded;
  try {
    decoded = jsonDecode(prefs.get<String>(optionPluginPixivDownloadIndex) ?? '[]');
  } on FormatException {
    return const {};
  }
  final keys = [
    for (final item in Json(decoded).list)
      if (item.string case final key? when _pageKey.hasMatch(key)) key,
  ];
  return cappedPixivIndex(keys);
}

/// [keys] in order with repeats folded, keeping only the newest [pixivDownloadIndexCap].
Set<String> cappedPixivIndex(Iterable<String> keys) {
  final unique = <String>{...keys};
  return unique.length <= pixivDownloadIndexCap ? unique : unique.skip(unique.length - pixivDownloadIndexCap).toSet();
}

/// The Pixiv pages saved on this device, so a tile can show it and a second
/// save can ask first. Kept in a device-only setting that no backup carries;
/// it follows the setting, so a reset shows at once.
class PixivDownloadIndex extends Store<Set<String>> {
  final BasePrefService prefs;

  PixivDownloadIndex(this.prefs) : super(readPixivDownloadIndex(prefs)) {
    prefs.addKeyListener(optionPluginPixivDownloadIndex, load);
  }

  /// The app's index, or null where none is provided.
  static PixivDownloadIndex? maybeOf(BuildContext context) => context.read<PixivDownloadIndex?>();

  bool isSaved(int illustId, int page) => state.contains(pixivPageKey(illustId, page));

  /// Which of [pages] of [illust] are saved already.
  List<int> savedAmong(PixivIllust illust, Iterable<int> pages) => [
    for (final page in pages)
      if (isSaved(illust.id, page)) page,
  ];

  /// How many pages of [illust] are saved.
  int savedCount(PixivIllust illust) => savedAmong(illust, Iterable.generate(illust.viewerUrls.length)).length;

  /// Reads the stored index again.
  void load() {
    final stored = readPixivDownloadIndex(prefs);
    if (!setEquals(stored, state)) update(Set.unmodifiable(stored));
  }

  /// Remembers [pages] of [illustId] as saved now, newest last. It builds on
  /// the stored list rather than on [state].
  Future<void> record(int illustId, Iterable<int> pages) async {
    final added = [for (final page in pages) pixivPageKey(illustId, page)];
    if (added.isEmpty) return;
    final stored = readPixivDownloadIndex(prefs);
    final next = cappedPixivIndex([...stored.where((key) => !added.contains(key)), ...added]);
    update(Set.unmodifiable(next));
    await prefs.set(optionPluginPixivDownloadIndex, jsonEncode(next.toList()));
  }

  Future<void> clear() async {
    update(const <String>{});
    await prefs.set(optionPluginPixivDownloadIndex, '[]');
  }

  @override
  Future<void> destroy() async {
    prefs.removeKeyListener(optionPluginPixivDownloadIndex, load);
    await super.destroy();
  }
}
