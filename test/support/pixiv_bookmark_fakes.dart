import 'dart:async';

import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// Records every bookmark call instead of reaching Pixiv; pass [provider] to
/// `pumpPixiv(extraProviders: …)`.
class FakePixivBookmarkApi extends PixivBookmarkApi {
  final calls = <String>[];
  PixivBookmarkDetail detailResult;

  /// The reader's tags per visibility, page by page; every page but the last
  /// carries a next_url.
  final Map<String, List<List<PixivBookmarkTag>>> tagPages;

  /// Thrown by the next detail, add or delete when set.
  Object? failDetail;
  Object? failWrite;

  /// Tag pages (by index) that fail every time they are asked for.
  final failingTagPages = <int>{};

  /// When set, detail, tags, or add and delete wait for it, so a test can act
  /// while the call is on its way.
  Completer<void>? detailGate;
  Completer<void>? tagsGate;
  Completer<void>? writeGate;

  FakePixivBookmarkApi({
    PixivClient? client,
    this.detailResult = const PixivBookmarkDetail(isBookmarked: false, restrict: 'public'),
    Map<String, List<PixivBookmarkTag>> tagLists = const {},
    Map<String, List<List<PixivBookmarkTag>>>? tagPages,
  }) : tagPages =
           tagPages ??
           {
             for (final MapEntry(:key, :value) in tagLists.entries) key: [value],
           },
       super(client ?? PixivClient(PrefServiceCache()));

  @override
  Future<PixivBookmarkDetail> detail(int illustId) async {
    calls.add('detail:$illustId');
    await detailGate?.future;
    final failure = failDetail;
    failDetail = null;
    if (failure != null) throw failure;
    return detailResult;
  }

  @override
  Future<void> add(int illustId, {required String restrict, List<String> tags = const []}) async {
    await _write();
    calls.add('add:$illustId:$restrict:${tags.join(' ')}');
  }

  @override
  Future<void> delete(int illustId) async {
    await _write();
    calls.add('delete:$illustId');
  }

  Future<void> _write() async {
    await writeGate?.future;
    final failure = failWrite;
    failWrite = null;
    if (failure != null) throw failure;
  }

  @override
  Future<PixivPage<PixivBookmarkTag>> tags({required String restrict, String? nextUrl}) async {
    final index = nextUrl == null ? 0 : int.parse(nextUrl.split(':').last);
    calls.add(index == 0 ? 'tags:$restrict' : 'tags:$restrict:$index');
    await tagsGate?.future;
    if (failingTagPages.contains(index)) throw pixivNetworkFailure();
    final pages = tagPages[restrict] ?? const [];
    return PixivPage(
      index < pages.length ? pages[index] : const [],
      nextUrl: index + 1 < pages.length ? 'fake-tags:$restrict:${index + 1}' : null,
    );
  }

  SingleChildWidget get provider => Provider<PixivBookmarkApi>.value(value: this);

  List<String> get writes => calls.where((call) => call.startsWith('add') || call.startsWith('delete')).toList();
}

PixivException pixivNetworkFailure() => PixivException(PixivErrorKind.network, 'offline');
