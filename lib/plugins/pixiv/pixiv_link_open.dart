import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';

/// The one route into a work's detail, so whatever must happen on every
/// opening (history, the muted-work gate) happens wherever it was opened from.
Route<void> pixivIllustRoute(PixivIllust illust) =>
    MaterialPageRoute<void>(builder: (_) => PixivIllustScreen(illust: illust));

Future<void> openPixivIllust(BuildContext context, PixivIllust illust) =>
    Navigator.push(context, pixivIllustRoute(illust));

/// Opens the work at [index] of the list it was tapped in.
Future<void> openPixivIllustFromList(BuildContext context, List<PixivIllust> illusts, int index) =>
    openPixivIllust(context, illusts[index]);

Future<void> openPixivUser(BuildContext context, int userId) =>
    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => PixivUserScreen(userId: userId)));

/// Opens what a Pixiv link or ID names. False when the work could not be
/// fetched, so the caller can fall back to the browser or say so.
Future<bool> openPixivLinkRef(BuildContext context, PixivLinkRef ref) async {
  switch (ref) {
    case PixivUserLinkRef(:final id):
      await openPixivUser(context, id);
      return true;
    case PixivArtworkLinkRef(:final id):
      final PixivIllust illust;
      try {
        illust = await context.read<PixivClient>().illustDetail(id);
      } catch (_) {
        return false;
      }
      if (context.mounted) {
        await openPixivIllust(context, illust);
      }
      return true;
  }
}
