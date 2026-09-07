import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'reader_review_harness.dart' show reviewImageBytes, ReviewImageOverrides;

Future<void> installBlueskyReadingImages(WidgetTester tester) async {
  final directory = await tester.runAsync(() => Directory.systemTemp.createTemp('xta-bluesky-images-'));
  final bytes = await tester.runAsync(reviewImageBytes);
  final previous = HttpOverrides.current;
  const pathProvider = MethodChannel('plugins.flutter.io/path_provider');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(pathProvider, (call) async {
    if (call.method == 'getTemporaryDirectory') return directory!.path;
    throw MissingPluginException('Unexpected path provider method: ${call.method}');
  });
  HttpOverrides.global = ReviewImageOverrides(bytes!);
  addTearDown(() async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    PaintingBinding.instance.imageCache.clear();
    PaintingBinding.instance.imageCache.clearLiveImages();
    HttpOverrides.global = previous;
    messenger.setMockMethodCallHandler(pathProvider, null);
    await tester.runAsync(() => directory!.delete(recursive: true));
  });
}

Future<void> settleBlueskyReadingImages(WidgetTester tester) async {
  // Complete the tab transition first: its final callback mounts the media grid.
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump();
  for (var attempt = 0; attempt < 80; attempt++) {
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 25));
    });
    await tester.pump(const Duration(milliseconds: 50));
    final images = tester.stateList(find.byType(ExtendedImage)).cast<ExtendedImageState>().toList();
    if (images.isNotEmpty && images.every((state) => state.extendedImageLoadState == LoadState.completed)) {
      await tester.pumpAndSettle();
      return;
    }
  }
  final images = tester.stateList(find.byType(ExtendedImage)).cast<ExtendedImageState>().toList();
  expect(images, isNotEmpty, reason: 'The production image widgets must be mounted.');
  expect(images.map((state) => state.extendedImageLoadState), everyElement(LoadState.completed),
    reason: 'All fixture thumbnails must decode before the screenshot or reveal assertion.');
}
