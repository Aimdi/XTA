import 'package:pref/pref.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// Records every bookmark call instead of reaching Pixiv; pass it to
/// `pumpPixiv(extraProviders: [Provider<PixivBookmarkApi?>.value(value: api)])`.
class FakePixivBookmarkApi extends PixivBookmarkApi {
  final calls = <String>[];
  PixivBookmarkDetail detailResult;
  final Map<String, List<PixivBookmarkTag>> tagLists;

  /// Thrown by the next detail, add or delete when set.
  Object? failDetail;
  Object? failWrite;

  FakePixivBookmarkApi({
    PixivClient? client,
    this.detailResult = const PixivBookmarkDetail(isBookmarked: false, restrict: 'public'),
    this.tagLists = const {},
  }) : super(client ?? PixivClient(PrefServiceCache()));

  @override
  Future<PixivBookmarkDetail> detail(int illustId) async {
    calls.add('detail:$illustId');
    final failure = failDetail;
    failDetail = null;
    if (failure != null) throw failure;
    return detailResult;
  }

  @override
  Future<void> add(int illustId, {required String restrict, List<String> tags = const []}) async {
    _write();
    calls.add('add:$illustId:$restrict:${tags.join(' ')}');
  }

  @override
  Future<void> delete(int illustId) async {
    _write();
    calls.add('delete:$illustId');
  }

  void _write() {
    final failure = failWrite;
    failWrite = null;
    if (failure != null) throw failure;
  }

  @override
  Future<PixivPage<PixivBookmarkTag>> tags({required String restrict, String? nextUrl}) async {
    calls.add('tags:$restrict');
    return PixivPage(tagLists[restrict] ?? const []);
  }

  List<String> get writes => calls.where((call) => call.startsWith('add') || call.startsWith('delete')).toList();
}

PixivException pixivNetworkFailure() => PixivException(PixivErrorKind.network, 'offline');
