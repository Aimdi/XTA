import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_opml.dart';
import 'package:xta/plugins/rss/rss_store.dart';

import 'support/reader_tools_harness.dart';

const _channelA = 'https://www.youtube.com/feeds/videos.xml?channel_id=A';
const _channelB = 'https://www.youtube.com/feeds/videos.xml?channel_id=B';

const _opml =
    '''<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>Reader export</title></head>
  <body>
    <outline text="Tech" title="Tech">
      <outline type="rss" text="Example" title="Example Feed" xmlUrl="HTTPS://Example.com/feed/#latest" htmlUrl="https://Example.com/"/>
      <outline text="Video">
        <outline type="rss" text="Channel A" xmlUrl="$_channelA"/>
        <outline type="rss" text="Channel B" xmlUrl="$_channelB"/>
      </outline>
    </outline>
    <outline text="News">
      <outline type="rss" title="Example again" xmlUrl="https://example.com/feed" category="/Daily,/World/Europe"/>
    </outline>
    <outline type="rss" text="Not the web" xmlUrl="ftp://example.com/feed"/>
    <outline type="rss" text="Broken escape" xmlUrl="https://example.com/%zz"/>
    <outline type="rss" xmlurl="https://lower.example/rss?a=1&amp;b=2"/>
  </body>
</opml>''';

const _mine = RssFeed(id: 'https://example.com/feed', feedUrl: 'https://example.com/feed', name: 'Mine');

/// The group table, in memory, with failures on demand.
class _Table extends RssFeedsTable {
  List<RssFeed> rows;
  bool failRead = false;
  bool failSync = false;
  int syncs = 0;
  _Table([this.rows = const []]);

  @override
  Future<List<RssFeed>> read() async => failRead ? throw StateError('database unavailable') : rows;

  @override
  Future<void> sync(List<RssFeed> feeds) async {
    syncs++;
    if (failSync) throw StateError('database unavailable');
    rows = List.of(feeds);
  }
}

PrefServiceCache _following(List<RssFeed> feeds) =>
    PrefServiceCache(cache: {optionPluginRssFeeds: RssFeed.listToPrefs(feeds)});

List<RssFeed> _saved(BasePrefService prefs) => RssFeed.listFromPrefs(prefs.get(optionPluginRssFeeds));

void main() {
  group('addresses', () {
    test('are stored with scheme and host lower-cased and without a fragment', () {
      expect(normalizeRssFeedUrl(' HTTPS://Example.COM/Feed?b=2&a=1#top '), 'https://example.com/Feed?b=2&a=1');
      expect(normalizeRssFeedUrl('http://example.com'), 'http://example.com/');
      expect(normalizeRssFeedUrl('https://example.com:8443/rss'), 'https://example.com:8443/rss');
      expect(normalizeRssFeedUrl('http://[::1]:8080/feed'), 'http://[::1]:8080/feed');
    });

    test('that are not http(s) or not well formed are refused', () {
      for (final bad in [
        '',
        'ftp://example.com/feed',
        'javascript:alert(1)',
        '/relative/feed',
        'https://exa mple.com/',
      ]) {
        expect(normalizeRssFeedUrl(bad), isNull, reason: bad);
      }
      expect(normalizeRssFeedUrl('https://example.com/%zz'), isNull);
      expect(normalizeRssFeedUrl('https://example.com/a%20b'), 'https://example.com/a%20b');
    });

    test('match across trailing slashes but not across queries', () {
      expect(rssFeedAddressKey('https://example.com/feed/'), rssFeedAddressKey('HTTPS://EXAMPLE.com/feed#x'));
      expect(rssFeedAddressKey(_channelA), isNot(rssFeedAddressKey(_channelB)));
    });

    test('that share their usual id get ids of their own', () {
      final first = allocateRssFeedIdentity(RssFeed(id: '', feedUrl: _channelA, name: 'A'), const {});
      final second = allocateRssFeedIdentity(RssFeed(id: '', feedUrl: _channelB, name: 'B'), rssFeedIdsInUse([first]));
      final again = allocateRssFeedIdentity(
        RssFeed(id: '', feedUrl: '$_channelA#x', name: 'A'),
        rssFeedIdsInUse([first]),
      );
      expect(first.id, 'https://www.youtube.com/feeds/videos.xml');
      expect(second.id, _channelB);
      expect(again.id, first.id);
    });
  });

  group('reading OPML', () {
    test('follows folders, merges duplicates and skips unusable addresses', () {
      final document = parseRssOpml(_opml);
      expect(document.feeds.map((feed) => feed.url), [
        'https://example.com/feed/',
        _channelA,
        _channelB,
        'https://lower.example/rss?a=1&b=2',
      ]);
      expect(document.duplicates, 1);
      expect(document.skipped, 2);
      final example = document.feeds.first;
      expect(example.title, 'Example Feed');
      expect(example.siteUrl, 'https://example.com/');
      expect(example.tags, ['Tech', 'News', 'Daily', 'World', 'Europe']);
      expect(document.feeds[1].tags, ['Video']);
      expect(document.feeds.last.title, 'lower.example');
      expect(document.feeds.last.tags, isEmpty);
    });

    test('tolerates a byte order mark and a plain doctype', () {
      expect(parseRssOpml('﻿$_opml').feeds, hasLength(4));
      final plain = _opml.replaceFirst('<opml', '<!DOCTYPE opml>\n<opml');
      expect(parseRssOpml(plain).feeds, hasLength(4));
    });

    test('refuses entity declarations, other documents, and files too large or too deep', () {
      void refuses(String source, RssOpmlProblem problem) => expect(
        () => parseRssOpml(source),
        throwsA(isA<RssOpmlException>().having((error) => error.problem, 'problem', problem)),
      );
      refuses(
        '<?xml version="1.0"?><!DOCTYPE opml [<!ENTITY boom "boom">]>'
        '<opml version="2.0"><body><outline xmlUrl="https://example.com/&boom;"/></body></opml>',
        RssOpmlProblem.declaresEntities,
      );
      refuses('<opml><body><outline></body></opml>', RssOpmlProblem.malformed);
      refuses('<rss version="2.0"><channel/></rss>', RssOpmlProblem.notOpml);
      refuses('<opml version="2.0"><head/></opml>', RssOpmlProblem.notOpml);
      refuses('<opml><body>${'<outline text="f">' * 40}${'</outline>' * 40}</body></opml>', RssOpmlProblem.tooDeep);
      refuses('<opml><body>${'<outline text="f"/>' * (rssOpmlMaxOutlines + 1)}</body></opml>', RssOpmlProblem.tooLarge);
      refuses('x' * (rssOpmlMaxBytes + 1), RssOpmlProblem.tooLarge);
    });
  });

  test('exported files escape what they carry and read back with their tags', () {
    const feeds = [
      RssFeed(
        id: 'a',
        feedUrl: 'https://example.com/rss?x=1&y=2',
        name: 'Tom & "Jerry" <news>',
        siteUrl: 'https://example.com/',
      ),
      RssFeed(id: 'b', feedUrl: 'https://b.example/feed', name: 'Plain'),
    ];
    final xml = exportRssOpml(
      feeds,
      tags: const {
        'a': ['News', 'Daily'],
      },
      now: DateTime.utc(2026, 9, 30, 12, 5, 9),
    );
    expect(xml, contains('<dateCreated>Wed, 30 Sep 2026 12:05:09 GMT</dateCreated>'));
    expect(xml, contains('title="Tom &amp; &quot;Jerry&quot; &lt;news>"'));
    final back = parseRssOpml(xml);
    expect(back.feeds.map((feed) => feed.url), ['https://example.com/rss?x=1&y=2', 'https://b.example/feed']);
    expect(back.feeds.map((feed) => feed.title), ['Tom & "Jerry" <news>', 'Plain']);
    expect(back.feeds.map((feed) => feed.tags), [
      ['News', 'Daily'],
      isEmpty,
    ]);
    expect(back.feeds.first.siteUrl, 'https://example.com/');
  });

  group('importing into the followed feeds', () {
    test('adds new feeds once, keeps followed ones as they are and saves them together', () async {
      final prefs = _following([_mine]);
      final table = _Table();
      final store = RssFeedsStore(prefs, table: table);
      final result = await store.importOpml(parseRssOpml(_opml));
      expect((result.imported, result.duplicates, result.skipped), (3, 2, 2));
      expect(result.persisted && result.tableSynced, isTrue);
      expect(store.state.first.name, 'Mine');
      final ids = store.state.map((feed) => feed.id).toList();
      expect(ids.toSet(), hasLength(4));
      expect(ids, containsAll(['https://www.youtube.com/feeds/videos.xml', _channelB]));
      expect(_saved(prefs).map((feed) => feed.id), ids);
      expect(table.rows.map((feed) => feed.id), ids);
      expect(result.tags, {
        'https://www.youtube.com/feeds/videos.xml': ['Video'],
        _channelB: ['Video'],
      });
      final again = await store.importOpml(parseRssOpml(_opml));
      expect((again.imported, again.duplicates), (0, 5));
      await store.destroy();
    });

    test('a refused or failing save changes nothing', () async {
      final prefs = GatedPrefs(
        cache: {
          optionPluginRssFeeds: RssFeed.listToPrefs([_mine]),
        },
      )..rejectWrites = true;
      final table = _Table();
      final store = RssFeedsStore(prefs, table: table);
      final refused = await store.importOpml(parseRssOpml(_opml));
      expect((refused.persisted, refused.imported), (false, 0));
      prefs
        ..rejectWrites = false
        ..throwWrites = true;
      final thrown = await store.importOpml(parseRssOpml(_opml));
      expect((thrown.persisted, thrown.imported), (false, 0));
      expect(store.state.map((feed) => feed.id), [_mine.id]);
      expect(_saved(prefs).map((feed) => feed.id), [_mine.id]);
      expect(table.rows.map((feed) => feed.id), [_mine.id]);
      await store.destroy();
    });

    test('runs after a load already asked for, and reads saved follows when nothing was loaded', () async {
      final racing = RssFeedsStore(_following([_mine]), table: _Table());
      await Future.wait([racing.load(), racing.importOpml(parseRssOpml(_opml))]);
      expect(racing.state.map((feed) => feed.name), contains('Mine'));
      expect(racing.state, hasLength(4));
      final cold = RssFeedsStore(_following([_mine]), table: _Table());
      await cold.importOpml(parseRssOpml(_opml));
      expect(cold.state.first.name, 'Mine');
      final legacy = RssFeedsStore(PrefServiceCache(), table: _Table([_mine]));
      await legacy.importOpml(parseRssOpml(_opml));
      expect(legacy.state.map((feed) => feed.id), contains(_mine.id));
      await Future.wait([racing.destroy(), cold.destroy(), legacy.destroy()]);
    });

    test('writes nothing while the saved follows cannot be read', () async {
      final prefs = PrefServiceCache();
      final table = _Table([_mine])..failRead = true;
      final store = RssFeedsStore(prefs, table: table);
      final result = await store.importOpml(parseRssOpml(_opml));
      expect(result.persisted, isFalse);
      expect(prefs.get(optionPluginRssFeeds), isNull);
      expect(table.syncs, 0);
      await store.destroy();
    });

    test('says when groups cannot see the feeds yet, and catches up on retry', () async {
      final table = _Table()..failSync = true;
      final store = RssFeedsStore(PrefServiceCache(), table: table);
      final result = await store.importOpml(parseRssOpml(_opml));
      expect((result.persisted, result.tableSynced), (true, false));
      expect(store.tableSyncPending, isTrue);
      table.failSync = false;
      expect(await store.retryTableSync(), isTrue);
      expect(table.rows, hasLength(4));
      expect(store.tableSyncPending, isFalse);
      await store.destroy();
    });
  });

  group('following one feed', () {
    test('keeps an address under its id and gives a colliding one its own', () async {
      final store = RssFeedsStore(PrefServiceCache(), table: _Table());
      final first = await store.add(RssFeed(id: rssFeedId(_channelA), feedUrl: _channelA, name: 'A'));
      final second = await store.add(RssFeed(id: rssFeedId(_channelB), feedUrl: _channelB, name: 'B'));
      expect(first!.id, isNot(second!.id));
      final renamed = await store.add(
        const RssFeed(id: 'other', feedUrl: 'HTTPS://www.YouTube.com/feeds/videos.xml?channel_id=A#x', name: 'A2'),
      );
      expect(renamed!.id, first.id);
      expect(store.state, hasLength(2));
      expect(store.followedFeed(_channelA)!.name, 'A2');
      await store.destroy();
    });

    test('reports a follow or unfollow that could not be saved', () async {
      final prefs = GatedPrefs();
      final store = RssFeedsStore(prefs, table: _Table());
      final kept = await store.add(_mine);
      prefs.rejectWrites = true;
      expect(await store.add(RssFeed(id: rssFeedId(_channelA), feedUrl: _channelA, name: 'A')), isNull);
      expect(await store.remove(kept!.id), isFalse);
      expect(store.state.map((feed) => feed.id), [_mine.id]);
      await store.destroy();
    });
  });

  test('imported tags go only to feeds without tags, even before tags were loaded', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginRssTags: rssTagsToPrefs({
          'a': ['Mine'],
        }),
      },
    );
    final tags = RssTagsStore(prefs);
    await tags.adoptTags({
      'a': ['Theirs'],
      'b': ['News'],
    });
    const expected = {
      'a': ['Mine'],
      'b': ['News'],
    };
    expect(tags.state, expected);
    expect(rssTagsFromPrefs(prefs.get(optionPluginRssTags)), expected);
    await tags.destroy();
  });

  group('the group table', () {
    setUpAll(() async {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
      final directory = await Directory.systemTemp.createTemp('xta_rss_opml_test');
      await databaseFactory.setDatabasesPath(directory.path);
      await Repository().migrate();
    });

    test('follows the saved feeds and drops memberships only of feeds no longer followed', () async {
      const table = RssFeedsTable();
      final a = allocateRssFeedIdentity(const RssFeed(id: '', feedUrl: _channelA, name: 'A'), const {});
      final b = allocateRssFeedIdentity(const RssFeed(id: '', feedUrl: _channelB, name: 'B'), rssFeedIdsInUse([a]));
      await table.sync([a, b]);
      final database = await Repository.writable();
      for (final id in [a.id, b.id]) {
        await database.insert(tableSubscriptionGroupMember, {'group_id': 'g', 'profile_id': id});
      }
      await table.sync([b]);
      final members = await database.query(tableSubscriptionGroupMember, where: 'group_id = ?', whereArgs: ['g']);
      expect(members.map((row) => row['profile_id']), [b.id]);
      expect((await table.read()).map((feed) => feed.id), [b.id]);
    });
  });
}
