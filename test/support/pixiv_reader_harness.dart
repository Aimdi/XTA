import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_destination.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_download_index.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';

import 'fixture_images.dart';

String _page(int id, int page, String size) =>
    'https://i.pximg.net/$size/img/2026/07/01/00/00/00/${id}_p${page}_master1200.jpg';

/// A multi-page work whose every URL points at Pixiv's CDN; nothing is fetched in tests.
PixivIllust pixivWork({
  int id = 120,
  int pages = 8,
  String type = 'manga',
  bool ai = false,
  String title = 'Sommerfest',
  List<PixivTag> tags = const [],
}) => PixivIllust(
  id: id,
  title: title,
  caption: '',
  type: type,
  thumbnailUrl: _page(id, 0, 'c/540x540_70/img-master'),
  pageUrls: [for (var i = 0; i < pages; i++) _page(id, i, 'c/600x1200_90/img-master')],
  originalUrls: [
    for (var i = 0; i < pages; i++) 'https://i.pximg.net/img-original/img/2026/07/01/00/00/00/${id}_p$i.png',
  ],
  pageThumbUrls: [for (var i = 0; i < pages; i++) _page(id, i, 'c/540x540_70/img-master')],
  pageCount: pages,
  width: 1200,
  height: 1700,
  userId: 42,
  userName: 'Mika',
  userAccount: 'mika',
  isAi: ai,
  tags: tags,
);

class FakePixivClient extends PixivClient {
  final PixivIllust? detail;
  final List<PixivIllust> authorWorks;
  final PixivUgoira? ugoira;
  final Uint8List? archiveBytes;

  /// Holds the author's works back until completed, to leave a screen mid-load.
  final Future<void>? authorWorksGate;
  final calls = <String>[];

  FakePixivClient(
    super.prefs, {
    this.detail,
    this.authorWorks = const [],
    this.ugoira,
    this.archiveBytes,
    this.authorWorksGate,
  });

  @override
  Future<PixivIllust> illustDetail(int illustId) async => detail ?? pixivWork(id: illustId);

  @override
  Future<PixivIllustPage> related(int illustId, {String? nextUrl, bool? includeR18}) async =>
      const PixivIllustPage(illusts: []);

  @override
  Future<PixivIllustPage> userIllusts(
    int userId, {
    PixivWorkType type = PixivWorkType.illust,
    bool ownList = false,
    String? nextUrl,
  }) async {
    calls.add('userIllusts:$userId');
    await authorWorksGate;
    return PixivIllustPage(illusts: authorWorks);
  }

  @override
  Future<void> followUser(int userId, {String restrict = 'public'}) async => calls.add('follow:$userId:$restrict');

  @override
  Future<void> unfollowUser(int userId) async => calls.add('unfollow:$userId');

  @override
  Future<PixivUgoira> ugoiraMetadata(int illustId) async {
    calls.add('ugoira:$illustId');
    return ugoira!;
  }

  @override
  Future<Uint8List> ugoiraArchive(String url) async {
    calls.add('archive:$url');
    return archiveBytes!;
  }
}

/// Records what the screens ask to save instead of touching the download queue.
class FakePixivDownloader extends PixivDownloader {
  final pages = <int>[];
  final requests = <DownloadRequest>[];
  final cancelled = <Uri>[];
  Future<bool> Function(DownloadRequest request)? onSave;
  String? folder = 'content://tree/pictures';

  FakePixivDownloader();

  @override
  Future<bool> savePage(BuildContext context, PixivIllust illust, int page) async {
    pages.add(page);
    return true;
  }

  @override
  Future<bool> save(DownloadRequest request) async {
    requests.add(request);
    return onSave?.call(request) ?? true;
  }

  /// Files written whole, such as ugoira exports.
  final files = <({String treeUri, String fileName, String? subfolder, Uint8List bytes})>[];

  @override
  Future<bool> saveBytes({
    required String treeUri,
    required String fileName,
    required Uint8List bytes,
    String? subfolder,
  }) async {
    files.add((treeUri: treeUri, fileName: fileName, subfolder: subfolder, bytes: bytes));
    return true;
  }

  @override
  void cancel(Uri uri) => cancelled.add(uri);

  @override
  Future<DownloadDestination?> batchDestination(BasePrefService prefs) async =>
      folder == null ? null : DownloadDestination.folder(folder!);

  @override
  Future<String?> batchFolder(BasePrefService prefs) async => folder;
}

class PixivHarness {
  final PrefServiceCache prefs;
  final FakePixivClient client;
  final FakePixivDownloader downloader;
  final PixivDownloadIndex downloads;

  PixivHarness(this.prefs, this.client, this.downloader, this.downloads);
}

Future<PixivHarness> pumpPixiv(
  WidgetTester tester,
  Widget home, {
  FakePixivClient Function(PrefServiceCache prefs)? client,
  Size size = const Size(390, 844),
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,

  /// Fakes for a feature's own API classes, so a batch need not edit this harness.
  List<SingleChildWidget> extraProviders = const [],
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  failImageDiskCache();
  ignoreFixtureImageFailures();
  VisibilityDetectorController.instance.updateInterval = Duration.zero;
  addTearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));
  final prefs = PrefServiceCache(defaults: {optionPluginPixivRefreshToken: 'fixture-only'});
  final harness = PixivHarness(
    prefs,
    client?.call(prefs) ?? FakePixivClient(prefs),
    FakePixivDownloader(),
    PixivDownloadIndex(prefs),
  );
  final mute = PixivMuteStore(prefs);
  final bookmarks = PixivBookmarkStore();
  final follows = PixivFollowStore(harness.client);
  final history = PixivSearchHistory(prefs);
  addTearDown(mute.destroy);
  addTearDown(bookmarks.destroy);
  addTearDown(follows.destroy);
  addTearDown(history.destroy);
  addTearDown(harness.downloads.destroy);
  await tester.pumpWidget(
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider<PixivClient>.value(value: harness.client),
          Provider<PixivMuteStore>.value(value: mute),
          Provider<PixivBookmarkStore>.value(value: bookmarks),
          Provider<PixivFollowStore>.value(value: follows),
          Provider<PixivSearchHistory>.value(value: history),
          Provider<PixivDownloader>.value(value: harness.downloader),
          Provider<PixivDownloadIndex>.value(value: harness.downloads),
          ...extraProviders,
        ],
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
            child: Directionality(textDirection: direction, child: child!),
          ),
          home: home,
        ),
      ),
    ),
  );
  await settlePixiv(tester);
  return harness;
}

Future<void> settlePixiv(WidgetTester tester) => settleFixtureImages(tester);

Future<void> disposePixiv(WidgetTester tester) => disposeFixtureScreen(tester);
