import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_opml.dart';
import 'package:xta/plugins/rss/rss_opml_controls.dart';
import 'package:xta/plugins/rss/rss_store.dart';

import 'support/reader_tools_harness.dart';

const _opml = '''<?xml version="1.0"?>
<opml version="2.0"><body>
  <outline text="News">
    <outline type="rss" text="Daily" xmlUrl="https://daily.example/feed"/>
    <outline type="rss" text="Already" xmlUrl="https://mine.example/feed/"/>
  </outline>
  <outline type="rss" text="Broken" xmlUrl="mailto:someone@example.com"/>
</body></opml>''';

const _mine = RssFeed(id: 'https://mine.example/feed', feedUrl: 'https://mine.example/feed', name: 'Mine');

class _Table extends RssFeedsTable {
  bool failSync = false;
  List<RssFeed> rows = const [];

  @override
  Future<List<RssFeed>> read() async => rows;

  @override
  Future<void> sync(List<RssFeed> feeds) async {
    if (failSync) throw StateError('database unavailable');
    rows = List.of(feeds);
  }
}

class _Files {
  RssOpmlFile? picked;
  final saved = <String, Uint8List>{};
  final shared = <String, Uint8List>{};

  late final io = RssOpmlIo(
    pick: () async => picked,
    save: (name, data) async {
      saved[name] = data;
      return true;
    },
    share: (name, data) async => shared[name] = data,
    parse: (source) async => parseRssOpml(source),
  );
}

RssOpmlFile _file(String text) {
  final bytes = utf8.encode(text);
  return RssOpmlFile(size: bytes.length, open: () => Stream.value(bytes));
}

class _Host {
  final prefs = PrefServiceCache(
    cache: {
      optionPluginRssFeeds: RssFeed.listToPrefs([_mine]),
    },
  );
  final table = _Table();
  final files = _Files();
  late final feeds = RssFeedsStore(prefs, table: table);
  late final tags = RssTagsStore(prefs);

  Widget app() => readerToolsApp(
    prefs,
    MultiProvider(
      providers: [
        Provider<RssFeedsStore>.value(value: feeds),
        Provider<RssTagsStore>.value(value: tags),
      ],
      child: RssOpmlIoScope(
        io: files.io,
        child: const Scaffold(body: SingleChildScrollView(child: RssOpmlSection())),
      ),
    ),
  );

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await feeds.destroy();
    await tags.destroy();
  }
}

Future<void> _tap(WidgetTester tester, Key key) async {
  await tester.tap(find.byKey(key));
  await tester.pump();
  await tester.pump();
}

void main() {
  testWidgets('importing a file follows its new feeds, keeps their folders as tags and says what happened', (
    tester,
  ) async {
    final host = _Host();
    host.files.picked = _file(_opml);
    await tester.pumpWidget(host.app());
    expect(tester.getSize(find.byKey(const ValueKey('rss-opml-import'))).height, greaterThanOrEqualTo(48));
    await _tap(tester, const ValueKey('rss-opml-import'));
    expect(find.text('Added 1 · Already followed 1 · Skipped 1'), findsOneWidget);
    expect(host.feeds.state.map((feed) => feed.name), ['Mine', 'Daily']);
    expect(host.tags.state, {
      'https://daily.example/feed': ['News'],
    });
    expect(host.table.rows, hasLength(2));
    await host.close(tester);
  });

  testWidgets('files that are too large or not OPML are refused with a reason', (tester) async {
    final host = _Host();
    host.files.picked = RssOpmlFile(size: rssOpmlMaxBytes + 1, open: () => throw StateError('must not be read'));
    await tester.pumpWidget(host.app());
    await _tap(tester, const ValueKey('rss-opml-import'));
    expect(find.text('That file is too large. XTA imports OPML files up to 2 MB.'), findsOneWidget);
    host.files.picked = _file('<html><body>not a feed list</body></html>');
    await _tap(tester, const ValueKey('rss-opml-import'));
    expect(find.text("That file isn't an OPML feed list XTA can read"), findsOneWidget);
    expect(RssFeed.listFromPrefs(host.prefs.get(optionPluginRssFeeds)).map((feed) => feed.id), [_mine.id]);
    await host.close(tester);
  });

  testWidgets('a file that is not UTF-8 text is refused', (tester) async {
    final host = _Host();
    host.files.picked = RssOpmlFile(size: 3, open: () => Stream.value(const [0xC3, 0x28, 0x3C]));
    await tester.pumpWidget(host.app());
    await _tap(tester, const ValueKey('rss-opml-import'));
    expect(find.text("That file isn't an OPML feed list XTA can read"), findsOneWidget);
    await host.close(tester);
  });

  testWidgets('feeds saved before groups could see them offer a retry', (tester) async {
    final host = _Host();
    host.files.picked = _file(_opml);
    host.table.failSync = true;
    await tester.pumpWidget(host.app());
    await _tap(tester, const ValueKey('rss-opml-import'));
    expect(find.textContaining("groups can't list them yet"), findsOneWidget);
    host.table.failSync = false;
    await tester.pumpAndSettle();
    await tester.tap(find.text('Retry'));
    await tester.pump();
    expect(host.table.rows, hasLength(2));
    expect(host.feeds.tableSyncPending, isFalse);
    await host.close(tester);
  });

  testWidgets('exporting saves or shares an OPML file with every followed feed', (tester) async {
    final host = _Host();
    await tester.pumpWidget(host.app());
    await _tap(tester, const ValueKey('rss-opml-export'));
    final name = host.files.saved.keys.single;
    expect(name, matches(RegExp(r'^xta-feeds-\d{4}-\d{2}-\d{2}\.opml$')));
    expect(find.text('Data exported to $name'), findsOneWidget);
    expect(parseRssOpml(utf8.decode(host.files.saved[name]!)).feeds.single.url, _mine.feedUrl);

    expect(tester.getSize(find.byKey(const ValueKey('rss-opml-share'))).width, greaterThanOrEqualTo(48));
    await _tap(tester, const ValueKey('rss-opml-share'));
    expect(host.files.shared.keys.single, name);
    await host.close(tester);
  });
}
