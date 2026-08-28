import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/timeline_parser.dart';

Map<String, dynamic> _fixture(String relativePath) {
  final file = File('test/fixtures/$relativePath');
  expect(file.existsSync(), isTrue, reason: 'missing fixture $relativePath');
  return jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
}

void main() {
  group('Retweeters endpoint', () {
    test('ships a query-id-shaped Retweeters operation on x.com', () {
      final uri = XEndpoints.uri(XEndpoints.retweeters, {'variables': '{}'});

      expect(uri.host, 'x.com');
      expect(uri.path, '/i/api/graphql/ROjiuYueotTnWoI8m2YaiQ/Retweeters');
      expect(
        queryIdPattern.hasMatch(XEndpoints.queryId(XEndpoints.retweeters)),
        isTrue,
      );
    });
  });

  group('TimelineParser.retweetersInstructions', () {
    test('reads people and the bottom cursor from the Retweeters timeline', () {
      final body = _fixture('Retweeters/ok.json');
      final users = TimelineParser.parseUsersTimeline(
        TimelineParser.retweetersInstructions(body),
      );

      expect(users.users, hasLength(2));
      expect(users.users![0].idStr, '1');
      expect(users.users![0].screenName, 'alice');
      expect(users.users![0].name, 'Alice');
      expect(
        users.users![0].profileImageUrlHttps,
        'https://example.com/alice.jpg',
      );
      expect(users.users![0].verified, isTrue);
      expect(users.users![1].idStr, '2');
      expect(users.users![1].screenName, 'bob');
      expect(users.users![1].verified, isFalse);
      expect(users.nextCursorStr, 'cursor-next');
    });

    test('missing or reshaped JSON is empty, not an exception', () {
      expect(TimelineParser.retweetersInstructions(null), isNull);
      expect(
        TimelineParser.retweetersInstructions(<String, dynamic>{}),
        isNull,
      );
      expect(TimelineParser.retweetersInstructions('nope'), isNull);
      expect(
        () => TimelineParser.parseUsersTimeline(
          TimelineParser.retweetersInstructions(null),
        ),
        returnsNormally,
      );

      final users = TimelineParser.parseUsersTimeline(
        TimelineParser.retweetersInstructions({'data': null}),
      );
      expect(users.users, isEmpty);
      expect(users.nextCursorStr, isNull);
    });

    test(
      'an unreadable user entry is skipped so the rest of the page survives',
      () {
        final body = {
          'data': {
            'retweeters_timeline': {
              'timeline': {
                'instructions': [
                  {
                    'type': 'TimelineAddEntries',
                    'entries': [
                      {
                        'entryId': 'user-bad',
                        'content': {'itemContent': null},
                      },
                      'not-a-map',
                      {
                        'entryId': 'user-3',
                        'content': {
                          'itemContent': {
                            'user_results': {
                              'result': {
                                'rest_id': '3',
                                'core': {'screen_name': 'cara', 'name': 'Cara'},
                              },
                            },
                          },
                        },
                      },
                    ],
                  },
                ],
              },
            },
          },
        };

        final users = TimelineParser.parseUsersTimeline(
          TimelineParser.retweetersInstructions(body),
        );
        expect(users.users, hasLength(1));
        expect(users.users!.single.screenName, 'cara');
      },
    );
  });
}
