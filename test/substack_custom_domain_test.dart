import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_transport.dart';

import 'support/substack_clock.dart';

SubstackClient _client(List<Uri> requests, http.Response Function(http.Request request) respond) {
  final client = SubstackClient(
    transport: SubstackTransport(
      MockClient((request) async {
        requests.add(request.url);
        return respond(request);
      }),
      clock: FakeSubstackClock(),
    ),
  );
  addTearDown(client.httpClient.close);
  return client;
}

void main() {
  final origin = Uri.parse('https://news.example.org');

  test('a domain that answers the Substack post API for the slug is a post; one request only', () async {
    final requests = <Uri>[];
    final client = _client(
      requests,
      (request) => http.Response(
        jsonEncode({'id': 9, 'slug': 'hello', 'title': 'Hello', 'body_html': '<p>Hi</p>'}),
        200,
        request: request,
        headers: {'content-type': 'application/json'},
      ),
    );

    final post = await client.probeCustomDomainPost(origin, 'hello');

    expect(post?.title, 'Hello');
    expect(post?.publicationBaseUrl, 'https://news.example.org');
    expect(requests.map((uri) => uri.toString()), ['https://news.example.org/api/v1/posts/hello']);
  });

  test('a page, an error, or another slug is not a post, and nothing else is asked', () async {
    final requests = <Uri>[];
    final answers = [
      http.Response('<html>blog</html>', 200, headers: {'content-type': 'text/html'}),
      http.Response('', 404),
      http.Response(jsonEncode({'id': 1, 'slug': 'other', 'title': 'Other'}), 200),
    ];
    var next = 0;
    final client = _client(requests, (_) => answers[next++]);

    for (var i = 0; i < answers.length; i++) {
      expect(await client.probeCustomDomainPost(origin, 'hello'), isNull);
    }
    expect(requests.map((uri) => uri.host).toSet(), {'news.example.org'});
    expect(requests.every((uri) => uri.path == '/api/v1/posts/hello'), isTrue);
  });

  testWidgets('a link the domain does not confirm is left to the browser', (tester) async {
    final requests = <Uri>[];
    final client = _client(requests, (request) => http.Response('', 404, request: request));
    var handled = true;
    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(cache: {optionPluginSubstackEnabled: true}),
        child: Provider<SubstackClient>.value(
          value: client,
          child: MaterialApp(
            home: Builder(
              builder: (context) => TextButton(
                onPressed: () async => handled = await openWithPlugins(context, 'https://news.example.org/p/hello'),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.runAsync(() async {
      await tester.tap(find.text('open'));
      for (var i = 0; i < 50 && handled; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 20));
      }
    });

    expect(handled, isFalse);
    expect(requests.single.path, '/api/v1/posts/hello');
  });
}
