// A temporary probe, run from CI where x.com is reachable:
//   fvm flutter test test/live/x_query_id_probe_test.dart --dart-define=RUN_LIVE=true
// What query ids installs apply, whether the published ones still answer, and
// whether x.com's own scripts name every operation's current id. Never fails.
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:xta/client/client_unauthenticated.dart';
import 'package:xta/client/endpoint_overrides.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/constants.dart';

const _browser = {
  'Accept-Language': 'en-US,en;q=0.9',
  'User-Agent':
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
};

final _jsLiteral = RegExp(r'''(["'`])([^"'`\s$]+\.js)\1''');
final _definition = RegExp(r'queryId:"([A-Za-z0-9_-]{16,32})",operationName:"(\w+)"');
final _definitionReversed = RegExp(r'operationName:"(\w+)",[^{}]{0,80}?queryId:"([A-Za-z0-9_-]{16,32})"');

bool _trusted(Uri uri) => uri.scheme == 'https' && uri.host == 'abs.twimg.com' && uri.path.endsWith('.js');

Iterable<Uri> _references(Uri parent, String source) sync* {
  for (final match in _jsLiteral.allMatches(source)) {
    final reference = Uri.tryParse(match.group(2)!);
    if (reference == null) continue;
    final uri = parent.resolveUri(reference);
    if (_trusted(uri)) yield uri;
  }
}

String _context(String text, int at) {
  final start = (at - 160).clamp(0, text.length);
  final end = (at + 240).clamp(0, text.length);
  return text.substring(start, end).replaceAll('\n', ' ');
}

void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'X: installed overrides and the ids in x.com scripts',
    () async {
      final client = http.Client();
      final published = await client.get(Uri.parse(defaultEndpointRegistryUrl));
      final installed = parseEndpointRegistry(published.body);
      print('PROBE published ${published.statusCode} from $defaultEndpointRegistryUrl: $installed');

      final roots = <Uri>{};
      for (final path in ['/i/flow/login', '/', '/search?q=AI&f=live', '/explore', '/home', '/tos']) {
        final page = Uri.parse('https://x.com$path');
        try {
          final response = await client.get(page, headers: _browser);
          final doc = html_parser.parse(response.body);
          final found = <Uri>{
            for (final element in [
              ...doc.querySelectorAll('script[src]'),
              ...doc.querySelectorAll('link[rel="modulepreload"][href]'),
              ...doc.querySelectorAll('link[rel="preload"][href]'),
            ])
              ?Uri.tryParse(element.attributes['src'] ?? element.attributes['href'] ?? ''),
            for (final script in doc.querySelectorAll('script:not([src])')) ..._references(page, script.text),
          }.where(_trusted).toSet();
          print('PROBE page $path -> ${response.statusCode}, ${response.body.length} bytes, ${found.length} scripts');
          roots.addAll(found);
        } catch (error) {
          print('PROBE page $path -> ERROR $error');
        }
      }

      final ids = <String, Set<String>>{};
      final filesWith = <String, int>{};
      final hits = <String, int>{};
      final contexts = <String>[];
      final seen = <Uri>{...roots};
      print('PROBE roots: ${roots.map((uri) => uri.path).join(' ')}');
      var queue = roots.toList();
      var bytes = 0;
      var read = 0;
      var depth = 0;
      final probes = {
        'queryId:"': RegExp(r'queryId:"'),
        "queryId:'": RegExp(r"queryId:'"),
        'queryId (any)': RegExp(r'queryId'),
        'operationName': RegExp(r'operationName'),
        '/graphql/': RegExp(r'/graphql/'),
        'graphql (any case)': RegExp(r'graphql', caseSensitive: false),
        'TweetDetail (any)': RegExp(r'TweetDetail'),
        'SearchTimeline (any)': RegExp(r'SearchTimeline'),
        'HomeTimeline (any)': RegExp(r'HomeTimeline'),
        'UserByScreenName (any)': RegExp(r'UserByScreenName'),
      };
      while (queue.isNotEmpty && read < 6000 && depth < 12) {
        final next = <Uri>{};
        for (var i = 0; i < queue.length && read < 6000; i += 24) {
          final batch = queue.sublist(i, (i + 24).clamp(0, queue.length));
          final bodies = await Future.wait(
            batch.map((uri) async {
              try {
                final response = await client.get(uri, headers: _browser);
                return response.statusCode == 200 ? (uri: uri, text: response.body) : null;
              } catch (_) {
                return null;
              }
            }),
          );
          for (final body in bodies.nonNulls) {
            read++;
            final text = body.text;
            bytes += text.length;
            for (final match in _definition.allMatches(text)) {
              ids.putIfAbsent(match.group(2)!, () => {}).add(match.group(1)!);
            }
            for (final match in _definitionReversed.allMatches(text)) {
              ids.putIfAbsent(match.group(1)!, () => {}).add(match.group(2)!);
            }
            for (final MapEntry(key: label, value: pattern) in probes.entries) {
              final match = pattern.firstMatch(text);
              if (match == null) continue;
              hits[label] = (hits[label] ?? 0) + 1;
              if (contexts.where((line) => line.startsWith('[$label]')).length < 2) {
                contexts.add('[$label] ${body.uri.pathSegments.last}: ${_context(text, match.start)}');
              }
            }
            for (final operation in XEndpoints.all.map((endpoint) => endpoint.operation).toSet()) {
              if (text.contains(operation)) filesWith[operation] = (filesWith[operation] ?? 0) + 1;
            }
            next.addAll(_references(body.uri, text).where(seen.add));
          }
        }
        queue = next.toList();
        depth++;
      }
      for (final context in contexts) {
        print('PROBE context $context');
      }
      print('PROBE hits per pattern (files): $hits');
      print(
        'PROBE scripts read: $read of ${seen.length} found, ${bytes ~/ 1024} KiB, depth $depth, operations defined: ${ids.length}',
      );

      final log = Logger('probe');
      final token = await getToken(log);
      Future<int> status(XEndpoint endpoint, String id) async {
        final params = {
          'variables': jsonEncode(const {'userId': '783214', 'screenName': 'X', 'count': 1}),
          'features': jsonEncode(const <String, bool>{}),
        };
        final uri = Uri.https(endpoint.host, endpoint.pathFor(id), params);
        final headers = await TwitterHeaders.getHeaders(uri, null);
        final response = await client.get(
          uri,
          headers: {...headers, 'authorization': guestBearerToken, 'x-guest-token': token},
        );
        await Future<void>.delayed(const Duration(seconds: 2));
        return response.statusCode;
      }

      for (final endpoint in XEndpoints.all) {
        final publishedId = installed[endpoint.name];
        final guest = [XEndpoints.userByScreenName, XEndpoints.userTweetsAndReplies, XEndpoints.userByRestId];
        final checks = guest.contains(endpoint.name)
            ? ' guest: shipped ${await status(endpoint, endpoint.queryId)}'
                  '${publishedId == null || publishedId == endpoint.queryId ? '' : ', published ${await status(endpoint, publishedId)}'}'
            : '';
        print(
          'PROBE op ${endpoint.name} (${endpoint.operation}) shipped ${endpoint.queryId}'
          '${publishedId == null ? '' : ' published $publishedId'}'
          ' | build ${ids[endpoint.operation] ?? '-'} in ${filesWith[endpoint.operation] ?? 0} files$checks',
        );
      }
      final others = ids.keys.where((name) => !XEndpoints.all.any((endpoint) => endpoint.operation == name));
      print('PROBE other operations in the build (${others.length}): ${others.take(60).join(' ')}');
    },
    timeout: const Timeout(Duration(minutes: 12)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit live x.com',
  );
}
