import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';

/// Bookmarks [illust] on Pixiv, or removes the bookmark; a failure is reported.
Future<void> togglePixivBookmark(BuildContext context, PixivIllust illust) async {
  final messenger = ScaffoldMessenger.of(context);
  final l10n = L10n.of(context);
  try {
    await context.read<PixivBookmarkStore>().toggle(context.read<PixivClient>(), illust);
  } catch (error) {
    messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));
  }
}
