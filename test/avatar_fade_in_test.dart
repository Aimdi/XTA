import 'dart:convert';
import 'dart:io';

import 'package:extended_image/extended_image.dart' show ExtendedRawImage;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/user.dart';

/// A one-pixel PNG.
final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
);

double _opacity(WidgetTester tester) =>
    tester.widget<Opacity>(find.descendant(of: find.byType(XtaFadeIn), matching: find.byType(Opacity))).opacity;

void main() {
  testWidgets('a fetched avatar fades in; one already in memory is drawn at once', (tester) async {
    HttpOverrides.global = null;
    // The avatar caches itself on disk, in the platform's temporary folder.
    final temp = Directory.systemTemp.createTempSync('avatar-');
    addTearDown(() => temp.deleteSync(recursive: true));
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/path_provider'),
      (_) async => temp.path,
    );
    // Bound and answered on the real clock: the test's fake one never runs them.
    final server = (await tester.runAsync(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) {
        request.response.headers.contentType = ContentType('image', 'png');
        request.response.add(_png);
        request.response.close();
      });
      return server;
    }))!;
    addTearDown(() => tester.runAsync(server.close));
    final avatar = MaterialApp(home: UserAvatar(uri: 'http://127.0.0.1:${server.port}/avatar_normal.png'));

    await tester.pumpWidget(avatar);
    for (var wait = 0; wait < 100 && find.byType(XtaFadeIn).evaluate().isEmpty; wait++) {
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
      await tester.pump();
    }
    expect(find.byType(XtaFadeIn), findsOneWidget);
    expect(_opacity(tester), lessThan(1));
    await tester.pumpAndSettle();
    expect(_opacity(tester), 1);

    // Scrolled away and back: drawn from memory, at once.
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(avatar);
    expect(tester.widget<ExtendedRawImage>(find.byType(ExtendedRawImage)).image, isNotNull);
    expect(find.byType(XtaFadeIn), findsNothing);

    // The fetch's keep-alive connection times out on the test's clock.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 30));
  });
}
