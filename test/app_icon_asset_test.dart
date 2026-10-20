import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// Width and height from a PNG's IHDR chunk.
(int, int) pngSize(ByteData png) => (png.getUint32(16), png.getUint32(20));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('the app bundles its own icon at 512 px', () async {
    expect(pngSize(await rootBundle.load('assets/app-icon.png')), (512, 512));
  });

  test('the 2000 px source icon is not bundled', () {
    expect(() => rootBundle.load('assets/icon.png'), throwsA(isA<FlutterError>()));
  });
}
