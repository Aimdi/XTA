import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_portal/flutter_portal.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/android_secure_window.dart';
import 'package:xta/utils/desktop.dart';

void main() {
  const channel = MethodChannel('screencapture_method');
  final calls = <String>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return false;
    });
  });
  tearDown(() {
    debugDesktopOverride = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  Future<void> mountAndDispose(WidgetTester tester) async {
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: Portal(child: AndroidSecureWindow(isSecure: false, builder: (_) => const SizedBox())),
      ),
    );
    await tester.pumpWidget(const SizedBox());
  }

  testWidgets('the desktop never calls the Android screenshot channel', (tester) async {
    debugDesktopOverride = true;
    await mountAndDispose(tester);
    expect(calls, isEmpty);
  });

  testWidgets('Android still releases FLAG_SECURE through the channel', (tester) async {
    await mountAndDispose(tester);
    expect(calls, contains('prevent_screenshot'));
  });
}
