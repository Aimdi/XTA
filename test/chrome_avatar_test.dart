import 'package:flutter_test/flutter_test.dart';
import 'package:xta/home/chrome_avatar.dart';

void main() {
  test('chromeAvatarSourceUrl upgrades the tiny _normal crop', () {
    expect(
      chromeAvatarSourceUrl('https://pbs.twimg.com/profile_images/1/abc_normal.jpg'),
      'https://pbs.twimg.com/profile_images/1/abc_400x400.jpg',
    );
    expect(
      chromeAvatarSourceUrl('https://example.com/plain.png'),
      'https://example.com/plain.png',
    );
  });
}
