import 'dart:async';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_pager.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_gate.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/utils/shared_links.dart';
import 'package:xta/utils/urls.dart';

/// A work's detail as every way into it builds it, alone on its route or as one page of
/// a pager, so whatever must happen on every opening happens wherever it was opened from:
/// a muted work waits behind its notice, and a shown one joins the viewing history once
/// it has loaded.
Widget pixivIllustPage(PixivIllust illust) => Builder(
  builder: (context) => PixivMuteGate(
    illust: illust,
    child: PixivIllustScreen(illust: illust, onLoaded: (loaded) => recordPixivVisit(context, loaded)),
  ),
);

/// The one route into a single work's detail.
Route<void> pixivIllustRoute(PixivIllust illust) => MaterialPageRoute<void>(builder: (_) => pixivIllustPage(illust));

Future<void> openPixivIllust(BuildContext context, PixivIllust illust) =>
    Navigator.push(context, pixivIllustRoute(illust));

/// Opens the work at [index] of the list it was tapped in. With Swipe between works on,
/// it opens among its neighbours, and [source] pages the list on past its end.
Future<void> openPixivIllustFromList(
  BuildContext context,
  List<PixivIllust> illusts,
  int index, {
  PixivIllustListStore? source,
}) {
  if (!pixivSwipesBetweenWorks(pixivPrefsOf(context)) || (illusts.length < 2 && source == null)) {
    return openPixivIllust(context, illusts[index]);
  }
  final mute = context.read<PixivMuteStore?>();
  return Navigator.push(
    context,
    MaterialPageRoute<void>(
      builder: (_) => PixivIllustPager(
        illusts: illusts,
        initialIndex: index,
        source: source,
        // The list's own filter first: a profile shown anyway keeps its muted creator's works.
        visible: source?.filter ?? mute?.filter,
        page: pixivIllustPage,
      ),
    ),
  );
}

Future<void> openPixivUser(BuildContext context, int userId) =>
    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => PixivUserScreen(userId: userId)));

/// Opens what a Pixiv link or ID names. False when the work could not be
/// fetched, so the caller can fall back to the browser or say so.
///
/// Series, novels and pixivision articles open in the browser until XTA has
/// screens for them.
Future<bool> openPixivLinkRef(BuildContext context, PixivLinkRef ref) async {
  switch (ref) {
    case PixivUserLinkRef(:final id):
      await openPixivUser(context, id);
      return true;
    case PixivArtworkLinkRef(:final id):
      return _openArtwork(context, id);
    case PixivTagLinkRef(:final tag):
      await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => PixivSearchScreen(initialQuery: tag)));
      return true;
    case PixivShortLinkRef():
      return _openShortLink(context, ref);
    case PixivWebPageLinkRef(:final webUrl):
      await openUri(context, webUrl);
      return true;
  }
}

Future<bool> _openArtwork(BuildContext context, int id) async {
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

/// A pixiv.me link the request could not resolve still opens, in the browser.
Future<bool> _openShortLink(BuildContext context, PixivShortLinkRef ref) async {
  final target = await resolvePixivShortLink(ref);
  if (!context.mounted) {
    return true;
  }
  if (target == null) {
    await openUri(context, ref.uri.toString());
    return true;
  }
  return openPixivLinkRef(context, target);
}

/// Where a pixiv.me short link points, read from one request that does not
/// follow the redirect. Only a page on pixiv.net is trusted; anything else,
/// or a failed request, is null.
Future<PixivLinkRef?> resolvePixivShortLink(PixivShortLinkRef ref, {http.Client? client}) async {
  final transport = client ?? http.Client();
  try {
    final target = await redirectLocation(transport, ref.uri);
    if (target == null || !_isPixivNetPage(target)) {
      return null;
    }
    final resolved = parsePixivLink(target.toString());
    return resolved is PixivShortLinkRef ? null : resolved;
  } on Exception {
    return null;
  } finally {
    if (client == null) transport.close();
  }
}

bool _isPixivNetPage(Uri uri) {
  final host = uri.host.toLowerCase();
  return (uri.scheme == 'https' || uri.scheme == 'http') && (host == 'pixiv.net' || host.endsWith('.pixiv.net'));
}
