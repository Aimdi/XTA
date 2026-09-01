import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/endpoint_overrides.dart';
import 'package:xta/client/endpoints.dart';

void main() {
  tearDown(XEndpoints.clearOverrides);

  group('XEndpoints', () {
    test('every shipped endpoint is keyed by its own name', () {
      XEndpoints.shipped.forEach((key, endpoint) => expect(endpoint.name, key));
    });

    test('every shipped query id is query-id shaped', () {
      for (final endpoint in XEndpoints.all) {
        expect(queryIdPattern.hasMatch(endpoint.queryId), isTrue, reason: '${endpoint.name} has a malformed query id');
      }
    });

    test('builds the URI a call site used to hardcode', () {
      final uri = XEndpoints.uri(XEndpoints.userTweets, {'variables': '{}'});

      expect(uri.host, 'x.com');
      expect(uri.path, '/i/api/graphql/36rb3Xj3iJ64Q-9wKDjCcQ/UserTweets');
      expect(uri.queryParameters['variables'], '{}');
    });

    test('AudioSpaceById is a shipped GraphQL operation', () {
      final uri = XEndpoints.uri(XEndpoints.audioSpaceById, {'variables': '{}'});

      expect(uri.host, 'x.com');
      expect(uri.path, endsWith('/AudioSpaceById'));
      expect(queryIdPattern.hasMatch(XEndpoints.queryId(XEndpoints.audioSpaceById)), isTrue);
    });

    test('the two SearchTimeline call sites keep distinct hosts and registry keys', () {
      final tweets = XEndpoints.uri(XEndpoints.searchTimeline, {});
      final users = XEndpoints.uri(XEndpoints.searchTimelineUsers, {});

      expect(tweets.host, 'x.com');
      expect(users.host, 'twitter.com');
      expect(tweets.path, endsWith('/SearchTimeline'));
      expect(users.path, endsWith('/SearchTimeline'));
      // Registry keys stay distinct so an override can retarget people search
      // without touching tweet search, even when both ship the same query id.
      expect(XEndpoints.searchTimeline, isNot(XEndpoints.searchTimelineUsers));
    });

    test('an override replaces the query id but keeps operation and host', () {
      XEndpoints.applyOverrides({XEndpoints.userTweets: 'AAAAAAAAAAAAAAAAAAAAAA'});

      final uri = XEndpoints.uri(XEndpoints.userTweets, {});
      expect(uri.path, '/i/api/graphql/AAAAAAAAAAAAAAAAAAAAAA/UserTweets');
      expect(uri.host, 'x.com');
      expect(XEndpoints.isOverridden(XEndpoints.userTweets), isTrue);
    });

    test('unknown endpoints and malformed ids are rejected', () {
      final accepted = XEndpoints.applyOverrides({
        'NotAnEndpoint': 'AAAAAAAAAAAAAAAAAAAAAA',
        XEndpoints.userTweets: '../../../evil',
        XEndpoints.tweetDetail: 'short',
        XEndpoints.userMedia: 'BBBBBBBBBBBBBBBBBBBBBB',
      });

      expect(accepted, 1);
      expect(XEndpoints.queryId(XEndpoints.userTweets), '36rb3Xj3iJ64Q-9wKDjCcQ');
      expect(XEndpoints.queryId(XEndpoints.tweetDetail), 'oCon7R-cgWRFy6EfZjaKfg');
      expect(XEndpoints.queryId(XEndpoints.userMedia), 'BBBBBBBBBBBBBBBBBBBBBB');
    });

    test('a path separator in an override can never escape the operation', () {
      XEndpoints.applyOverrides({XEndpoints.userTweets: 'a/b/UserTweets?x=1'});

      expect(XEndpoints.uri(XEndpoints.userTweets, {}).path, '/i/api/graphql/36rb3Xj3iJ64Q-9wKDjCcQ/UserTweets');
    });

    test('an unknown endpoint name is a programming error, not a silent 404', () {
      expect(() => XEndpoints.uri('Nope', {}), throwsArgumentError);
    });
  });

  group('parseEndpointRegistry', () {
    test('reads the published shape', () {
      final ids = parseEndpointRegistry('{"version":1,"endpoints":{"UserTweets":"abc"}}');

      expect(ids, {'UserTweets': 'abc'});
    });

    test('tolerates documents that are not the shape we expect', () {
      expect(parseEndpointRegistry('{}'), isEmpty);
      expect(parseEndpointRegistry('[]'), isEmpty);
      expect(parseEndpointRegistry('{"endpoints":"nope"}'), isEmpty);
      expect(parseEndpointRegistry('{"endpoints":{"UserTweets":42}}'), isEmpty);
    });
  });

  test('the published endpoints.json parses and names only known endpoints', () {
    final file = File('endpoints.json');
    expect(file.existsSync(), isTrue, reason: 'the registry the app fetches must be committed');

    final body = file.readAsStringSync();
    expect(() => jsonDecode(body), returnsNormally);

    parseEndpointRegistry(body).forEach((name, id) {
      expect(XEndpoints.shipped.containsKey(name), isTrue, reason: '$name is not an endpoint this build knows');
      expect(queryIdPattern.hasMatch(id), isTrue, reason: '$name has a malformed query id');
    });
  });
}
