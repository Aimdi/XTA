import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_reader_screen.dart';
import 'package:xta/plugins/ehviewer/eh_reader_store.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';

import 'fixture_images.dart';

const ehFixtureGid = 9;

/// A gallery of [total] pages on a fake e-hentai.org that serves [sheetSize]
/// preview tiles per sheet and records every request.
class EhFakeSite {
  final int total;
  final int sheetSize;
  final Set<int> failingPages;
  final requests = <Uri>[];

  EhFakeSite({this.total = 30, this.sheetSize = 20, Set<int>? failingPages}) : failingPages = failingPages ?? {};

  EhGallery get gallery => EhGallery(gid: ehFixtureGid, token: 'tok', title: 'Sommerfest', pageCount: total);

  late final client = MockClient((request) async {
    requests.add(request.url);
    final image = RegExp(r'^/s/\w+/\d+-(\d+)$').firstMatch(request.url.path);
    if (image != null) return _imagePage(int.parse(image[1]!), request.url.queryParameters['nl']);
    if (request.url.path.startsWith('/g/')) return _sheet(int.tryParse(request.url.queryParameters['p'] ?? '') ?? 0);
    return http.Response('', 404);
  });

  static String tokenOf(int page) => 'pt$page';

  static String linkTo(int page) => 'https://e-hentai.org/s/${tokenOf(page)}/$ehFixtureGid-$page';

  static EhPreview preview(int page) => EhPreview(pageToken: tokenOf(page), page: page);

  /// Like the site, a sheet past the end answers with the last one.
  http.Response _sheet(int index) {
    final sheet = min(index, (total - 1) ~/ sheetSize);
    final first = sheet * sheetSize + 1;
    final tiles = [
      for (var page = first; page <= min(first + sheetSize - 1, total); page++)
        '<a href="${linkTo(page)}"><div></div></a>',
    ];
    return http.Response('<h1 id="gn">Sommerfest</h1><div id="gdt">${tiles.join()}</div>', 200);
  }

  http.Response _imagePage(int page, String? reloadKey) {
    if (failingPages.contains(page)) return http.Response('', 500);
    final server = reloadKey == null ? 'a' : 'b';
    return http.Response(
      '<img id="img" src="https://$server.hath.example/h/$page/0$page.webp" />'
      '${page > 1 ? '<a id="prev" href="${linkTo(page - 1)}"></a>' : ''}'
      '${page < total ? '<a id="next" href="${linkTo(page + 1)}"></a>' : ''}'
      '<a href="#" onclick="return nl(\'nl-$page\')">Reload broken image</a>',
      200,
    );
  }

  Iterable<Uri> get sheetRequests => requests.where((uri) => uri.path.startsWith('/g/'));

  Iterable<Uri> imageRequests(int page) => requests.where((uri) => uri.path.endsWith('-$page'));
}

/// Previews for pages 1 to [last], as a gallery screen would pass them.
List<EhPreview> ehFixturePreviews(int last) => [for (var page = 1; page <= last; page++) EhFakeSite.preview(page)];

/// The page counter in the reader's bottom bar showing [page].
Finder ehReaderCounter(int page, {int total = 30}) =>
    find.descendant(of: find.byKey(const ValueKey('eh-reader-counter')), matching: find.text('Page $page of $total'));

/// Remembers pages instead of writing the history table.
class FakeEhHistory extends EhHistoryStore {
  final pages = <int>[];

  @override
  Future<void> remember(EhGallery gallery, {required int page}) async => pages.add(page);
}

class EhReaderHarness {
  final EhFakeSite site;
  final FakeEhHistory history;
  final BasePrefService prefs;

  EhReaderHarness(this.site, this.history, this.prefs);
}

Future<EhReaderHarness> pumpEhReader(
  WidgetTester tester, {
  EhFakeSite? site,
  int initialPage = 1,
  EhReadingMode mode = EhReadingMode.leftToRight,
  List<EhPreview> previews = const [],
  Size size = const Size(390, 844),
  double textScale = 1,
  bool failImages = true,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  if (failImages) failImageDiskCache();
  ignoreFixtureImageFailures();
  VisibilityDetectorController.instance.updateInterval = Duration.zero;
  addTearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));
  final fake = site ?? EhFakeSite();
  final prefs = PrefServiceCache(defaults: {optionPluginEhReadingMode: mode.name, optionPluginEhKeepScreenOn: false});
  final history = FakeEhHistory();
  addTearDown(history.destroy);
  await tester.pumpWidget(
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider<EhClient>.value(value: EhClient(prefs, httpClient: fake.client)),
          Provider<EhHistoryStore>.value(value: history),
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
            child: child!,
          ),
          home: EhReaderScreen(gallery: fake.gallery, initialPage: initialPage, previews: previews),
        ),
      ),
    ),
  );
  // Images that really load are still spinning here; their own settle waits for them.
  await (failImages ? settleFixtureImages(tester) : tester.pump());
  return EhReaderHarness(fake, history, prefs);
}
