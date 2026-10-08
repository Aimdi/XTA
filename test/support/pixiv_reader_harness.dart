import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira.dart';

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
  Future<PixivIllustPage> userIllusts(int userId, {String? nextUrl}) async {
    calls.add('userIllusts:$userId');
    await authorWorksGate;
    return PixivIllustPage(illusts: authorWorks);
  }

  @override
  Future<PixivUser> userDetail(int userId) async => PixivUser(id: userId, name: 'Mika', account: 'mika', comment: '');

  @override
  Future<List<String>> bookmarkFolders() async => const ['Favs'];

  @override
  Future<void> addBookmark(int illustId, {String restrict = 'public', String? folder}) async {
    calls.add('bookmark:$illustId:$restrict:${folder ?? '-'}');
  }

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
  Future<void> savePage(BuildContext context, PixivIllust illust, int page) async => pages.add(page);

  @override
  Future<bool> save(DownloadRequest request) async {
    requests.add(request);
    return onSave?.call(request) ?? true;
  }

  @override
  void cancel(Uri uri) => cancelled.add(uri);

  @override
  Future<String?> batchFolder(BasePrefService prefs) async => folder;
}

class PixivHarness {
  final PrefServiceCache prefs;
  final FakePixivClient client;
  final FakePixivDownloader downloader;

  PixivHarness(this.prefs, this.client, this.downloader);
}

Future<PixivHarness> pumpPixiv(
  WidgetTester tester,
  Widget home, {
  FakePixivClient Function(PrefServiceCache prefs)? client,
  Size size = const Size(390, 844),
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  failPixivImageCache();
  _ignoreFixtureImageFailures();
  VisibilityDetectorController.instance.updateInterval = Duration.zero;
  addTearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));
  final prefs = PrefServiceCache(defaults: {optionPluginPixivRefreshToken: 'fixture-only'});
  final harness = PixivHarness(prefs, client?.call(prefs) ?? FakePixivClient(prefs), FakePixivDownloader());
  final mute = PixivMuteStore(prefs);
  final bookmarks = PixivBookmarkStore();
  addTearDown(mute.destroy);
  addTearDown(bookmarks.destroy);
  await tester.pumpWidget(
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider<PixivClient>.value(value: harness.client),
          Provider<PixivMuteStore>.value(value: mute),
          Provider<PixivBookmarkStore>.value(value: bookmarks),
          Provider<PixivDownloader>.value(value: harness.downloader),
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

/// The image disk cache asks a platform channel for its folder; refusing at once keeps
/// every fetch inside the test clock (the test HTTP client then answers 400).
void failPixivImageCache() {
  const pathProvider = MethodChannel('plugins.flutter.io/path_provider');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(pathProvider, (_) async => throw PlatformException(code: 'unavailable'));
  addTearDown(() => messenger.setMockMethodCallHandler(pathProvider, null));
}

FlutterExceptionHandler? _testErrorHandler;

/// Every fixture image fails (the test HTTP client answers 400); a page scrolled away
/// before its failure arrives would otherwise be reported as a test error.
void _ignoreFixtureImageFailures() {
  final handler = _testErrorHandler = FlutterError.onError;
  FlutterError.onError = (details) {
    if (details.library != 'image resource service') handler?.call(details);
  };
}

/// Lets failed image fetches and their single retry finish, then the UI settle.
Future<void> settlePixiv(WidgetTester tester) async {
  for (var i = 0; i < 4; i++) {
    await tester.pump(const Duration(milliseconds: 150));
  }
  await tester.pumpAndSettle();
}

/// Ends a test: unmounts, drains timers and gives the test its error handler back.
Future<void> disposePixiv(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox());
  await tester.pump(const Duration(seconds: 1));
  FlutterError.onError = _testErrorHandler;
}
