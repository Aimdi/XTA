/// Running XTA against the stand-in for X that `tool/mock_x` serves, for QA on
/// an emulator without an X account (docs/mock-x.md). Off in every real build.
library;

import 'package:http/http.dart' as http;
import 'package:xta/client/headers.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/client/x_client_transaction_id/client_transaction.dart';
import 'package:xta/utils/mock_x_urls.dart';

/// The stand-in's address, from `--dart-define=XTA_MOCK_X=http://localhost:8787`.
const mockXServer = String.fromEnvironment('XTA_MOCK_X');

bool get mockXEnabled => mockXServer.isNotEmpty;

/// Sends X's requests to the stand-in at [server], and every other request
/// where it was going.
class MockXClient extends http.BaseClient {
  final Uri server;
  final http.Client _inner;

  MockXClient(this.server, {http.Client? inner}) : _inner = inner ?? http.Client();

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    final target = mockXUrl(server, request.url);
    return _inner.send(target == null ? request : _redirected(request, target));
  }

  @override
  void close() => _inner.close();
}

http.BaseRequest _redirected(http.BaseRequest request, Uri target) {
  final copy = http.AbortableRequest(
    request.method,
    target,
    abortTrigger: request is http.Abortable ? request.abortTrigger : null,
  );
  copy
    ..headers.addAll(request.headers)
    ..followRedirects = request.followRedirects
    ..maxRedirects = request.maxRedirects
    ..persistentConnection = request.persistentConnection;
  if (request is http.Request && request.bodyBytes.isNotEmpty) copy.bodyBytes = request.bodyBytes;
  return copy;
}

/// Points X's requests at [server] and signs them with a fixed key, since the
/// stand-in serves no x.com page to derive one from.
void useMockX(Uri server) {
  xHttpClient = MockXClient(server);
  TwitterHeaders.initializer = () async =>
      ClientTransaction.forTesting(keyBytes: List.filled(48, 0), animationKey: 'mock-x');
}
