import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_opml.dart';
import 'package:xta/plugins/substack/substack_opml_section.dart';
import 'package:xta/plugins/substack/substack_store.dart';

SubstackPublication _pub(String host, String name) =>
    SubstackPublication(subdomain: host.split('.').first, baseUrl: 'https://$host', name: name);

/// Follows kept in memory, placed by the plugin's own rule.
class _Publications extends SubstackPublicationsStore {
  _Publications(List<SubstackPublication> pubs) : super(PrefServiceCache(cache: {})) {
    update(pubs);
  }

  @override
  Future<void> load() async {}

  @override
  Future<void> add(SubstackPublication publication) async {
    final placed = placeSubstackFollow(state, publication);
    update([...state.where((pub) => pub.id != placed.id), placed]);
  }
}

class _Client extends SubstackClient {
  final resolved = <String>[];
  final Map<String, Object> answers;

  _Client(this.answers);

  @override
  Future<SubstackPublication> resolvePublication(String input) async {
    resolved.add(input);
    final answer = answers[Uri.parse(input).host];
    if (answer is SubstackPublication) return answer;
    throw answer ?? SubstackNotPublicationException();
  }
}

class _Files extends SubstackOpmlFiles {
  final String? picked;
  final saved = <String, String>{};

  _Files({this.picked});

  @override
  Future<String?> save(String fileName, String text) async {
    saved[fileName] = text;
    return '/downloads/$fileName';
  }

  @override
  Future<String?> pick() async => picked;
}

void main() {
  test('an export reads back as the same sites, names escaped', () {
    final opml = substackOpml([
      _pub('fieldnotes.substack.com', 'Field "Notes" & Co'),
      _pub('news.example.org', 'Example <News>'),
    ], title: 'Substack');

    expect(opml, contains('text="Field &quot;Notes&quot; &amp; Co"'));
    expect(opml, contains('xmlUrl="https://news.example.org/feed"'));
    expect(substackOpmlSites(opml).map((uri) => '$uri'), [
      'https://fieldnotes.substack.com',
      'https://news.example.org',
    ]);
  });

  test('another reader\'s OPML: nested outlines, feed-only entries, duplicates and junk', () {
    const opml = '''<?xml version="1.0"?><opml version="1.0"><body>
<outline text="Newsletters">
  <outline type="rss" xmlUrl="https://www.astralcodexten.com/feed"/>
  <outline type="rss" xmlUrl="https://astralcodexten.com/feed" htmlUrl="https://astralcodexten.com"/>
  <outline type="rss" XMLURL="https://one.substack.com/feed"/>
  <outline text="no address"/>
  <outline xmlUrl="javascript:alert(1)"/>
</outline></body></opml>''';

    expect(substackOpmlSites(opml).map((uri) => '$uri'), [
      'https://www.astralcodexten.com',
      'https://one.substack.com',
    ]);
  });

  test('an import adds the new, leaves the followed and counts the rest; a rate limit stops it', () async {
    final followed = _pub('fieldnotes.substack.com', 'Field Notes');
    final pubs = _Publications([followed]);
    final client = _Client({
      'fieldnotes.substack.com': followed,
      'new.substack.com': _pub('new.substack.com', 'New'),
      'nope.example': SubstackNotPublicationException(),
      'slow.substack.com': SubstackRateLimitedException(Uri.parse('https://slow.substack.com'), Duration.zero),
    });
    final store = SubstackOpmlImportStore(client, pubs);
    addTearDown(store.destroy);

    final result = await store.run([
      for (final host in [
        'fieldnotes.substack.com',
        'new.substack.com',
        'nope.example',
        'slow.substack.com',
        'later.substack.com',
      ])
        Uri.parse('https://$host'),
    ]);

    expect((result.added, result.alreadyFollowed, result.failed, result.total), (1, 1, 3, 5));
    expect(client.resolved, hasLength(4), reason: 'nothing is asked after the rate limit');
    expect(pubs.state.map((pub) => pub.name), containsAll(['Field Notes', 'New']));
  });

  testWidgets('the settings rows export the follows and import a file', (tester) async {
    final files = _Files(picked: '<opml><body><outline xmlUrl="https://new.substack.com/feed"/></body></opml>');
    final pubs = _Publications([_pub('fieldnotes.substack.com', 'Field Notes')]);
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          Provider<SubstackOpmlFiles>.value(value: files),
          Provider<SubstackClient>.value(value: _Client({'new.substack.com': _pub('new.substack.com', 'New')})),
          Provider<SubstackPublicationsStore>.value(value: pubs),
        ],
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: const Scaffold(body: SubstackOpmlSection()),
        ),
      ),
    );

    await tester.tap(find.text(L10n.current.plugin_substack_opml_export));
    await tester.pumpAndSettle();
    expect(files.saved['substack-follows.opml'], contains('https://fieldnotes.substack.com/feed'));

    await tester.tap(find.text(L10n.current.plugin_substack_opml_import));
    await tester.pumpAndSettle();
    expect(pubs.state.map((pub) => pub.name), contains('New'));
    expect(find.text(L10n.current.plugin_substack_opml_result(1, 0, 0)), findsOneWidget);
  });
}
