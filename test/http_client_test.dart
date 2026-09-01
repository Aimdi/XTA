import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/http_client.dart';

/// The X path used to call the top-level `http.get`, which builds a client per
/// call and closes it again — a fresh TCP and TLS handshake for every request,
/// with no keep-alive between them.
void main() {
  tearDown(() => xHttpClient = null);

  test('the same instance is handed out, so connections are pooled', () {
    expect(identical(xHttpClient, xHttpClient), isTrue);
  });

  test('clearing it hands out a fresh one rather than null', () {
    final first = xHttpClient;
    xHttpClient = null;

    expect(identical(xHttpClient, first), isFalse);
    expect(xHttpClient, isNotNull);
  });
}
