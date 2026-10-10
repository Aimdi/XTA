import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_parse.dart';
import 'package:xta/plugins/ehviewer/eh_query.dart';
import 'package:xta/plugins/ehviewer/eh_search_screen.dart';
import 'package:xta/plugins/ehviewer/eh_search_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';

const _list =
    '<table><tr><td class="gl1c glcat"><div class="cn ct2">Manga</div></td>'
    '<td><a href="https://e-hentai.org/g/1/abc/"><div class="glink">Summer</div></a></td></tr></table>';

const _suggestions = {
  'tags': {
    '1': {'id': 1, 'ns': 'female', 'tn': 'big breasts'},
    '2': {'id': 2, 'ns': 'female', 'tn': 'bb', 'mns': 'female', 'mtn': 'big breasts'},
    '3': {'id': 3, 'ns': 'parody', 'tn': 'blue archive'},
  },
};

class _Site {
  final searches = <Uri>[];
  final suggested = <String>[];

  /// Terms the fake site has tags for; null knows every term.
  Set<String>? known;

  late final client = MockClient((request) async {
    if (request.url.host == 'api.e-hentai.org') {
      final text = jsonDecode(request.body)['text'] as String;
      suggested.add(text);
      final hit = known == null || known!.contains(text);
      return http.Response(jsonEncode(hit ? _suggestions : {'tags': []}), 200);
    }
    searches.add(request.url);
    return http.Response(_list, 200);
  });
}

EhClient _client(_Site site, PrefServiceCache prefs) => EhClient(prefs, httpClient: site.client);

void main() {
  group('query editing', () {
    test('the term being typed starts after the last finished term', () {
      expect(ehLastTermStart('female:"big breasts\$" -mal'), 22);
      expect(ehLastTermStart('artist:bob\$ big br'), 12);
      expect(ehLastTermStart('summer big br'), 0);
      expect(ehLastTermStart('female:"big br'), 0);
    });

    test('suggestions are asked for the whole term, then its last words', () {
      expect(ehSuggestionTerms('summer big br'), [
        (start: 0, prefix: 'summer big br'),
        (start: 7, prefix: 'big br'),
        (start: 11, prefix: 'br'),
      ]);
      expect(ehSuggestionTerms('-bi'), [(start: 0, prefix: 'bi')]);
      expect(ehSuggestionTerms('female:"big br'), [(start: 0, prefix: 'female:big br'), (start: 12, prefix: 'br')]);
      expect(ehSuggestionTerms('x'), isEmpty);
      expect(ehSuggestionTerms('female:"big breasts\$"'), isEmpty);
    });

    test('a suggestion replaces its term and keeps a typed operator', () {
      const tag = EhTag(EhNamespace.female, 'big breasts');
      expect(ehInsertSuggestion('summer -big b', (start: 7, prefix: 'big b'), tag), 'summer -female:"big breasts\$" ');
      expect(ehInsertSuggestion('bi', (start: 0, prefix: 'bi'), tag), 'female:"big breasts\$" ');
    });
  });

  test('suggestions read the tag map, prefer master tags and drop repeats', () {
    final tags = parseEhTagSuggestions(_suggestions);
    expect(tags.map((t) => t.raw), ['female:big breasts', 'parody:blue archive']);
    expect(parseEhTagSuggestions({'tags': []}), isEmpty);
    expect(parseEhTagSuggestions('nonsense'), isEmpty);
  });

  group('store', () {
    late _Site site;
    late PrefServiceCache prefs;
    late EhSearchStore store;

    setUp(() {
      site = _Site();
      prefs = PrefServiceCache();
      store = EhSearchStore(
        _client(site, prefs),
        PluginSearchHistoryStore(prefs, optionPluginEhSearchHistory),
        debounce: Duration.zero,
      );
    });

    test('the longest term the site knows wins, and only that span is replaced', () async {
      site.known = {'big b'};
      store.type('summer big b');
      await Future<void>.delayed(const Duration(milliseconds: 20));
      expect(site.suggested, ['summer big b', 'big b']);
      expect(store.state.suggestions, hasLength(2));
      expect(store.pick(store.state.suggestions.first), 'summer female:"big breasts\$" ');
      expect(store.state.suggestions, isEmpty);
    });

    test('searches are remembered; a changed filter runs the search again', () async {
      await store.search('summer');
      expect(readPluginSearchHistory(prefs, optionPluginEhSearchHistory), ['summer']);
      expect(store.results!.state.single.title, 'Summer');
      store.setMinRating(4);
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(site.searches.last.queryParameters['f_srdd'], '4');
      store.setMinRating(4);
      expect(store.state.minRating, 0);
    });

    test('the last category cannot be switched off', () {
      for (final category in EhCategory.values.skip(1)) {
        store.toggleCategory(category);
      }
      expect(store.state.categories, {EhCategory.values.first});
      store.toggleCategory(EhCategory.values.first);
      expect(store.state.categories, {EhCategory.values.first});
    });

    tearDown(() => store.destroy());
  });

  testWidgets('a suggestion fills the field and history entries can be removed', (tester) async {
    final site = _Site();
    final prefs = PrefServiceCache(
      cache: {
        optionPluginEhSearchHistory: jsonEncode(['older']),
      },
    );
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: Provider<EhClient>.value(
          value: _client(site, prefs),
          child: const MaterialApp(
            localizationsDelegates: [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
            ],
            home: EhSearchScreen(),
          ),
        ),
      ),
    );
    expect(find.byKey(const ValueKey('eh-history-older')), findsOneWidget);
    await tester.tap(
      find.descendant(of: find.byKey(const ValueKey('eh-history-older')), matching: find.byTooltip('Delete')),
    );
    await tester.pump();
    expect(find.byKey(const ValueKey('eh-history-older')), findsNothing);

    await tester.enterText(find.byKey(const ValueKey('eh-search-field')), 'big b');
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    expect(find.text('Female'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('eh-suggestion-female:big breasts')));
    await tester.pump();
    expect(find.text('female:"big breasts\$" '), findsOneWidget);
  });
}
