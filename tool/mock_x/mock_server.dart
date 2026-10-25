import 'dart:io';

import 'package:xta/utils/mock_x_urls.dart';

import 'mock_x.dart';

/// Answers the requests sent to [port] as [mock] would, and reports each
/// answer to [onAnswer].
Future<HttpServer> serveMockX(
  MockX mock, {
  int port = 8787,
  required void Function(String method, Uri url, MockAnswer answer) onAnswer,
}) async {
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
  server.listen((request) async {
    await request.drain<void>();
    final url = originalXUrl(request.uri);
    final answer = mock.answer(request.method, url);
    onAnswer(request.method, url, answer);
    request.response
      ..statusCode = answer.status
      ..headers.contentType = answer.answered ? ContentType.json : ContentType.text
      ..write(answer.body);
    await request.response.close();
  });
  return server;
}
