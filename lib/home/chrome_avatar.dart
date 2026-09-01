import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_account_avatar.dart';

/// Fixed on-disk name for the chrome avatar inside the app documents directory.
const chromeAvatarFileName = 'chrome_avatar';

/// Larger X CDN variant when the API hands back the tiny `_normal` crop.
String chromeAvatarSourceUrl(String url) {
  return url.replaceFirst('_normal.', '_400x400.');
}

Future<File> chromeAvatarFile() async {
  final dir = await getApplicationDocumentsDirectory();
  return File('${dir.path}/$chromeAvatarFileName');
}

/// Revision of the local chrome avatar. `0` means none (use the monogram).
int chromeAvatarRevisionFromPrefs(BasePrefService prefs) {
  final value = prefs.get<int>(optionChromeAvatarRevision);
  return value == null || value < 0 ? 0 : value;
}

/// Local picture for the upper-left chrome avatar (drawer button + drawer header).
///
/// Stays on-device only — never uploaded anywhere. State is a revision counter so
/// [Image.file] can bust its cache when the bytes change; `0` means cleared.
class ChromeAvatarStore extends Store<int> {
  final BasePrefService prefs;

  ChromeAvatarStore(this.prefs) : super(chromeAvatarRevisionFromPrefs(prefs));

  bool get hasCustom => state > 0;

  Future<void> reload() async {
    await execute(() async {
      final revision = chromeAvatarRevisionFromPrefs(prefs);
      if (revision == 0) {
        return 0;
      }
      final file = await chromeAvatarFile();
      if (!await file.exists()) {
        await prefs.set(optionChromeAvatarRevision, 0);
        return 0;
      }
      return revision;
    });
  }

  Future<void> setFromBytes(List<int> bytes) async {
    await execute(() async {
      if (bytes.isEmpty) {
        return state;
      }
      final file = await chromeAvatarFile();
      await file.writeAsBytes(bytes, flush: true);
      final next = state + 1;
      await prefs.set(optionChromeAvatarRevision, next);
      return next;
    });
  }

  Future<void> pickFromDevice() async {
    final picked = await FilePicker.pickFile(type: FileType.image);
    if (picked == null) {
      return;
    }
    final bytes = await picked.readAsBytes();
    await setFromBytes(bytes);
  }

  /// Downloads the public profile photo for [screenName] and stores it locally.
  Future<void> setFromProfileScreenName(String screenName) async {
    final profile = await Twitter.getProfileByScreenName(screenName);
    final raw = profile.user.profileImageUrlHttps?.trim() ?? '';
    if (raw.isEmpty) {
      throw StateError('missing profile image');
    }
    final response = await http.get(Uri.parse(chromeAvatarSourceUrl(raw)));
    if (response.statusCode < 200 || response.statusCode >= 300 || response.bodyBytes.isEmpty) {
      throw HttpException(response);
    }
    await setFromBytes(response.bodyBytes);
  }

  Future<void> clear() async {
    await execute(() async {
      final file = await chromeAvatarFile();
      if (await file.exists()) {
        await file.delete();
      }
      await prefs.set(optionChromeAvatarRevision, 0);
      return 0;
    });
  }
}

/// Round chrome avatar: custom local file when set, otherwise the account monogram.
class ChromeAvatarMark extends StatelessWidget {
  final double size;
  final Account? account;

  const ChromeAvatarMark({super.key, required this.size, this.account});

  @override
  Widget build(BuildContext context) {
    final store = context.read<ChromeAvatarStore>();
    return ScopedBuilder<ChromeAvatarStore, int>(
      store: store,
      onState: (_, revision) {
        if (revision > 0) {
          return FutureBuilder<File>(
            future: chromeAvatarFile(),
            builder: (context, snapshot) {
              final file = snapshot.data;
              if (file == null) {
                return AccountAvatar(account: account, size: size);
              }
              return ClipOval(
                child: Image.file(
                  file,
                  key: ValueKey('chrome-avatar-$revision'),
                  width: size,
                  height: size,
                  fit: BoxFit.cover,
                  errorBuilder: (context, error, stackTrace) => AccountAvatar(account: account, size: size),
                ),
              );
            },
          );
        }
        return AccountAvatar(account: account, size: size);
      },
    );
  }
}

/// The app bar's leading slot: custom chrome avatar when set, else the
/// primary account monogram. Tap opens the drawer; long-press sets the picture.
class DrawerAvatarButton extends StatelessWidget {
  const DrawerAvatarButton({super.key});

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Account?>(
      future: primaryAccount(),
      builder: (context, snapshot) => IconButton(
        tooltip: MaterialLocalizations.of(context).openAppDrawerTooltip,
        onPressed: () => Scaffold.of(context).openDrawer(),
        onLongPress: () => showChromeAvatarSheet(context),
        icon: ChromeAvatarMark(account: snapshot.data, size: 30),
      ),
    );
  }
}

Future<void> showChromeAvatarSheet(BuildContext context) async {
  final store = context.read<ChromeAvatarStore>();
  final accounts = await getAccounts();
  if (!context.mounted) {
    return;
  }
  final l10n = L10n.of(context);
  final messenger = ScaffoldMessenger.of(context);

  await showModalBottomSheet<void>(
    context: context,
    builder: (sheetContext) {
      return SafeArea(
        child: ScopedBuilder<ChromeAvatarStore, int>(
          store: store,
          onState: (_, revision) {
            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ListTile(
                  leading: IconButton(
                    icon: const Icon(Icons.arrow_back),
                    onPressed: () => Navigator.of(sheetContext).pop(),
                  ),
                  title: Text(l10n.chrome_avatar_title, style: Theme.of(context).textTheme.titleLarge),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                  child: Text(
                    l10n.chrome_avatar_description,
                    style: TextStyle(color: Theme.of(context).disabledColor),
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.photo_library_outlined),
                  title: Text(l10n.chrome_avatar_choose),
                  onTap: () async {
                    await store.pickFromDevice();
                    if (sheetContext.mounted) {
                      Navigator.of(sheetContext).pop();
                    }
                  },
                ),
                if (accounts.isNotEmpty)
                  ListTile(
                    leading: const Icon(Icons.cloud_download_outlined),
                    title: Text(l10n.chrome_avatar_from_account),
                    subtitle: Text('@${accounts.first.screenName ?? l10n.unknown_username}'),
                    onTap: () async {
                      final name = accounts.first.screenName;
                      if (name == null || name.isEmpty) {
                        return;
                      }
                      try {
                        await store.setFromProfileScreenName(name);
                        if (sheetContext.mounted) {
                          Navigator.of(sheetContext).pop();
                        }
                      } catch (_) {
                        messenger.showSnackBar(SnackBar(content: Text(l10n.chrome_avatar_error)));
                      }
                    },
                  ),
                if (revision > 0)
                  ListTile(
                    leading: const Icon(Icons.hide_image_outlined),
                    title: Text(l10n.chrome_avatar_clear),
                    onTap: () async {
                      await store.clear();
                      if (sheetContext.mounted) {
                        Navigator.of(sheetContext).pop();
                      }
                    },
                  ),
              ],
            );
          },
        ),
      );
    },
  );
}
