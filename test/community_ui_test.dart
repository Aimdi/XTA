import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/community/community_members_screen.dart';
import 'package:xta/community/community_screen.dart';
import 'package:xta/community/community_search_results.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/utils/json.dart';
import 'package:xta/utils/urls.dart';

Json _fixture(String name) => Json(jsonDecode(File('test/fixtures/Community/$name').readAsStringSync()));

final _community = Community.fromQuery(_fixture('community.json'))!;

final _emptyPage = TweetStatus(chains: const [], cursorBottom: null, cursorTop: null);

/// Banners and avatars never load without a network, and their spinners would
/// keep pumpAndSettle waiting, so this pumps a fixed few frames instead.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _pumpApp(WidgetTester tester, Widget home) async {
  final prefs = PrefServiceCache();
  final groups = GroupsModel(prefs);
  final subscriptions = SubscriptionsModel(prefs, groups);
  addTearDown(subscriptions.destroy);
  addTearDown(groups.destroy);
  await tester.pumpWidget(
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider.value(value: groups),
          Provider.value(value: subscriptions),
        ],
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en')],
          home: home,
          routes: {routeCommunity: (context) => Text('opened ${ModalRoute.of(context)!.settings.arguments}')},
        ),
      ),
    ),
  );
}

void main() {
  group('links', () {
    test('a community link and its about page open the community', () async {
      final page = await parseUri(Uri.parse('https://x.com/i/communities/1472249315724771329'));
      expect(page, isA<CommunityUriInfo>().having((info) => info.about, 'about', isFalse));
      expect((page as CommunityUriInfo).id, '1472249315724771329');
      final about = await parseUri(Uri.parse('https://twitter.com/i/communities/1472249315724771329/about/'));
      expect(about, isA<CommunityUriInfo>().having((info) => info.about, 'about', isTrue));
    });

    test('other community pages are not mistaken for one', () async {
      expect(
        await parseUri(Uri.parse('https://x.com/i/communities/1472249315724771329/members')),
        isNot(isA<CommunityUriInfo>()),
      );
      expect(await parseUri(Uri.parse('https://x.com/i/communities/flutter')), isNot(isA<CommunityUriInfo>()));
    });
  });

  group('community page', () {
    testWidgets('shows the header and asks X for its Top posts', (tester) async {
      final asked = <(String, String?)>[];
      await _pumpApp(
        tester,
        CommunityPage(
          id: _community.id,
          readCommunity: (id) async => _community,
          readPosts: (id, cursor) async {
            asked.add((id, cursor));
            return _emptyPage;
          },
        ),
      );
      await _settle(tester);
      expect(find.text('Flutter'), findsWidgets);
      expect(find.text('Software'), findsOneWidget);
      expect(find.textContaining('members'), findsOneWidget);
      expect(asked, [(_community.id, null)]);
      expect(find.text('No results'), findsOneWidget);
    });

    testWidgets('an about link opens on the rules', (tester) async {
      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await _pumpApp(
        tester,
        CommunityPage(
          id: _community.id,
          about: true,
          readCommunity: (id) async => _community,
          readPosts: (id, cursor) async => _emptyPage,
        ),
      );
      await _settle(tester);
      expect(find.text('Rules'), findsOneWidget);
      expect(find.text('Be kind and respectful.'), findsOneWidget);
      expect(find.textContaining('Created'), findsOneWidget);
    });

    testWidgets('a community X does not show can be asked for again', (tester) async {
      var reads = 0;
      await _pumpApp(
        tester,
        CommunityPage(
          id: '1',
          readCommunity: (id) async {
            reads++;
            throw CommunityUnavailableException(id);
          },
          readPosts: (id, cursor) async => _emptyPage,
        ),
      );
      await _settle(tester);
      await tester.tap(find.text('Retry'));
      await _settle(tester);
      expect(reads, 2);
      await tester.tap(find.byTooltip('More info'));
      await _settle(tester);
      expect(find.textContaining('Unable to load the community'), findsOneWidget);
    });
  });

  testWidgets('members are listed from what X sent', (tester) async {
    final pages = [parseCommunityMembers(_fixture('members.json'))];
    await _pumpApp(
      tester,
      CommunityMembersScreen(
        community: _community,
        readMembers: (id, cursor) async =>
            pages.isEmpty ? Follows(cursorBottom: null, cursorTop: null, users: const []) : pages.removeAt(0),
      ),
    );
    await _settle(tester);
    expect(find.textContaining('tadaspetra'), findsOneWidget);
    expect(find.text('Members'), findsOneWidget);
  });

  testWidgets('search lists communities and opens the one tapped', (tester) async {
    final found = parseCommunitySearch(_fixture('search.json'));
    final model = CommunitySearchPagination(
      search: (query, cursor) async => cursor == null ? found : (communities: const <Community>[], cursor: null),
    );
    addTearDown(model.dispose);
    model.updateQuery('flutter');
    await _pumpApp(tester, Scaffold(body: CommunitySearchResults(model: model)));
    await _settle(tester);
    final first = found.communities.first;
    expect(find.text(first.name), findsWidgets);
    await tester.tap(find.byType(ListTile).first);
    await _settle(tester);
    expect(find.text('opened ${CommunityScreenArguments(first.id)}'), findsOneWidget);
  });
}
