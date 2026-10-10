import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:xta/client/x_cookie_login.dart';
import 'package:xta/utils/desktop.dart';
import 'package:xta/utils/desktop_download_directory.dart';
import 'package:xta/utils/download_directory.dart';

void main() {
  group('isDesktop', () {
    tearDown(() => debugDesktopOverride = null);

    test('keeps the suite on the Android paths it mocks', () {
      expect(isDesktop, isFalse);
    });

    test('follows the override a desktop test sets', () {
      debugDesktopOverride = true;
      expect(isDesktop, isTrue);
    });
  });

  group('parseXSessionCookies', () {
    test('reads a browser Cookie header, ignoring the other cookies', () {
      final cookies = parseXSessionCookies('guest_id=v1%3A1; auth_token=abc123; lang=en; ct0=def456')!;
      expect({for (final c in cookies) c.name: c.value}, {'auth_token': 'abc123', 'ct0': 'def456'});
    });

    test('reads the two pairs pasted one per line', () {
      final cookies = parseXSessionCookies(' auth_token = abc \n ct0=def\n')!;
      expect({for (final c in cookies) c.name: c.value}, {'auth_token': 'abc', 'ct0': 'def'});
    });

    test('keeps an "=" inside a value', () {
      final cookies = parseXSessionCookies('auth_token=a=b; ct0=c')!;
      expect(cookies.first.value, 'a=b');
    });

    test('is null unless both cookies have a value', () {
      expect(parseXSessionCookies('auth_token=abc'), isNull);
      expect(parseXSessionCookies('auth_token=abc; ct0='), isNull);
      expect(parseXSessionCookies(''), isNull);
    });
  });

  test('normalizeScreenName drops one leading @ and the spaces', () {
    expect(normalizeScreenName('  @jack '), 'jack');
    expect(normalizeScreenName('jack'), 'jack');
  });

  group('sharedFolderFor', () {
    Directory? none(String _) => null;

    test('maps each MediaStore collection to its XDG directory', () {
      final asked = <String>[];
      Directory? xdg(String name) {
        asked.add(name);
        return Directory('/xdg/$name');
      }

      expect(sharedFolderFor('images', xdg, '/home/me'), '/xdg/PICTURES');
      expect(sharedFolderFor('video', xdg, '/home/me'), '/xdg/VIDEOS');
      expect(sharedFolderFor('downloads', xdg, '/home/me'), '/xdg/DOWNLOAD');
      expect(asked, ['PICTURES', 'VIDEOS', 'DOWNLOAD']);
    });

    test('falls back to the usual folders in home without xdg-user-dir', () {
      expect(sharedFolderFor('images', none, '/home/me'), '/home/me/Pictures');
      expect(sharedFolderFor('video', none, '/home/me'), '/home/me/Videos');
      expect(sharedFolderFor('downloads', none, '/home/me'), '/home/me/Downloads');
    });
  });

  test('displayName shows a desktop folder as its whole path', () {
    expect(DownloadDirectory.displayName('/home/me/Pictures/XTA'), '/home/me/Pictures/XTA');
  });

  group('DesktopDownloadDirectory.saveFile', () {
    late Directory root;

    setUp(() async => root = await Directory.systemTemp.createTemp('xta-desktop-'));
    tearDown(() async => root.delete(recursive: true));

    test('copies into the subfolder, never over an earlier save', () async {
      final source = await File(p.join(root.path, 'staged.part')).writeAsString('pixels');
      final folder = p.join(root.path, 'Pictures');

      final first = await DesktopDownloadDirectory.saveFile(
        folder: folder,
        fileName: 'cat.jpg',
        sourcePath: source.path,
        subfolder: 'artist',
      );
      final second = await DesktopDownloadDirectory.saveFile(
        folder: folder,
        fileName: 'cat.jpg',
        sourcePath: source.path,
        subfolder: 'artist',
      );

      expect(first, p.join(folder, 'artist', 'cat.jpg'));
      expect(second, p.join(folder, 'artist', 'cat (1).jpg'));
      expect(await File(second).readAsString(), 'pixels');
    });
  });
}
