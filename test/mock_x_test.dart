import 'dart:convert';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/client/client.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/mock_x.dart';
import 'package:xta/utils/mock_x_urls.dart';

import '../tool/mock_x/app_preferences.dart';
import '../tool/mock_x/mock_server.dart';
import '../tool/mock_x/mock_x.dart';
import '../tool/mock_x/video_cut.dart';

final _mock = MockX(Directory('test/fixtures'));

Uri _graphql(String operation, Map<String, Object?> variables) =>
    Uri.https('x.com', '/i/api/graphql/someId/$operation', {'variables': jsonEncode(variables)});

void main() {
  group('the addresses', () {
    final server = Uri.parse('http://localhost:8787');

    test("an X request goes to the stand-in and comes back as X's", () {
      final url = _graphql('UserByScreenName', {'screen_name': 'quax_tests'});
      final mocked = mockXUrl(server, url)!;
      expect(mocked.toString(), startsWith('http://localhost:8787/x.com/i/api/graphql/someId/UserByScreenName?'));
      expect(originalXUrl(mocked), url);
      expect(mockXUrl(Uri.parse('http://localhost:8787/'), url), mocked);
    });

    test('anything not X goes where it was going', () async {
      final sent = <Uri>[];
      final client = MockXClient(
        server,
        inner: MockClient((request) async {
          sent.add(request.url);
          return http.Response(request.body, 200);
        }),
      );
      await client.get(Uri.https('substack.com', '/api/v1/feed'));
      final echoed = await client.post(Uri.https('api.x.com', '/1.1/guest/activate.json'), body: 'kept');
      expect(sent.map((url) => url.toString()), [
        'https://substack.com/api/v1/feed',
        'http://localhost:8787/api.x.com/1.1/guest/activate.json',
      ]);
      expect(echoed.body, 'kept');
    });
  });

  group('the stand-in', () {
    test('answers what it holds and what X says about the rest', () {
      final known = _mock.answer('GET', _graphql('UserByScreenName', {'screen_name': 'Quax_Tests_2'}));
      expect(known.fixture, 'UserByScreenName/quax_tests_2.json');
      final unknown = _mock.answer('GET', _graphql('UserByScreenName', {'screen_name': 'nobody_by_this_name'}));
      expect(unknown.status, 200);
      expect(jsonDecode(unknown.body), {'data': {}});
    });

    test('ends a list on the page after the recorded one', () {
      final next = _mock.answer('GET', _graphql('UserOriginalsTimeline', {'userId': '1', 'cursor': 'DAAH'}));
      expect(next.body, isNot(contains('"entryId"')));
    });

    test('says what it has instead of guessing', () {
      final other = _mock.answer('GET', _graphql('HomeTimeline', {}));
      expect(other.status, 501);
      expect(other.body, contains('UserByScreenName'));
      final elsewhere = _mock.answer('GET', _graphql('CommunityTweetsTimeline', {'communityId': '2'}));
      expect(elsewhere.status, 501);
      expect(elsewhere.body, contains('1472249315724771329'));
    });

    test('hands out a guest token', () {
      final token = _mock.answer('POST', Uri.https('api.x.com', '/1.1/guest/activate.json'));
      expect(jsonDecode(token.body), {'guest_token': '1'});
    });
  });

  group("the app's requests", () {
    late HttpServer server;
    final answers = <MockAnswer>[];

    setUpAll(() async {
      await L10n.load(const Locale('en'));
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
      await databaseFactory.setDatabasesPath((await Directory.systemTemp.createTemp('xta_mock_x_test')).path);
      await Repository().migrate();
      server = await serveMockX(_mock, port: 0, onAnswer: (method, url, answer) => answers.add(answer));
      useMockX(Uri.parse('http://127.0.0.1:${server.port}'));
    });
    tearDownAll(() async {
      xHttpClient.close();
      xHttpClient = null;
      TwitterHeaders.resetForTesting();
      await server.close(force: true);
    });
    setUp(answers.clear);
    tearDown(() => expect(answers.where((answer) => !answer.answered).map((answer) => answer.body), isEmpty));

    test('open a profile and page through its posts', () async {
      final profile = await Twitter.getProfileByScreenName('quax_tests_2');
      expect(profile.user.name, 'Moluyts');
      Future<TweetStatus> posts(String? cursor) => Twitter.getTweets(
        profile.user.idStr!,
        'profile',
        profile.pinnedTweets,
        cursor: cursor,
        includeReplies: false,
        getTweetsCounter: () => 0,
        incrementTweetsCounter: () {},
      );
      final first = await posts(null);
      expect(first.chains, isNotEmpty);
      expect((await posts(first.cursorBottom)).chains, isEmpty);
    });

    test('say that an account does not exist', () async {
      await expectLater(
        Twitter.getProfileByScreenName('nobody_by_this_name'),
        throwsA(isA<TwitterError>().having((error) => error.message, 'message', 'User not found')),
      );
    });

    test('open a post', () async {
      final post = await Twitter.getTweet('2105589900078641579');
      expect(post.chains.expand((chain) => chain.tweets).map((tweet) => tweet.idStr), contains('2105589900078641579'));
    });

    test('open a community, its posts and members, and find it in search', () async {
      const id = '1472249315724771329';
      expect((await Twitter.getCommunity(id)).name, 'Flutter');
      expect((await Twitter.getCommunityTweets(id)).chains, isNotEmpty);
      expect((await Twitter.getCommunityMembers(id)).users, isNotEmpty);
      expect((await Twitter.searchCommunities('flutter')).communities.map((c) => c.id), contains(id));
    });
  });

  group('on the emulator', () {
    test('the app starts past its intro and without the update check', () {
      final xml = sharedPreferencesXml(emulatorPreferences);
      expect(xml, contains('<boolean name="flutter.pref_intro.seen" value="true" />'));
      expect(xml, contains('<boolean name="flutter.pref_should_check_for_updates" value="false" />'));
    });

    test("each video starts where Maestro's still start-up ends", () {
      expect(cutPoint('freeze_start: 0.4\nfreeze_duration: 3\nfreeze_end: 3.4'), const Duration(milliseconds: 2900));
      expect(cutPoint('freeze_start: 2.5\nfreeze_end: 4'), isNull, reason: 'a still later on is the app waiting');
      expect(cutPoint(''), isNull);
    });
  });
}
