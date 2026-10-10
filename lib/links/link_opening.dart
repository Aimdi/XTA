import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/links/link_browser_page.dart';
import 'package:xta/links/link_browser_screen.dart';
import 'package:xta/links/link_post_context.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/utils/desktop.dart';
import 'package:xta/utils/urls.dart';

/// Whether the reader chose to read links in the app.
///
/// Settings → browser stores false the moment a real browser is picked, and
/// the app turns the switch on at first launch; an unset key is that default.
bool embeddedBrowserEnabled(BuildContext context) {
  if (isDesktop) return false;
  try {
    return PrefService.of(context, listen: false).get<bool>(optionOpenLinksInEmbeddedBrowser) != false;
  } catch (_) {
    return true;
  }
}

/// A web address an in-app browser can show at all.
bool isBrowsableLink(String url) {
  final uri = Uri.tryParse(url);
  return uri != null && (uri.scheme == 'http' || uri.scheme == 'https') && uri.host.isNotEmpty;
}

/// Opens a link tapped inside a post.
///
/// An XTA screen that reads it natively wins; then, when the reader keeps
/// links in the app, the in-app browser with [post] floating under the page;
/// otherwise the browser they picked in settings.
Future<void> openPostLink(
  BuildContext context,
  String url, {
  String? title,
  LinkPostContext? post,
  LinkNativeHandler openNative = openNativeLink,
  LinkBrowserPageFactory pageFactory = webViewLinkBrowserPage,
}) async {
  if (await openNative(context, url) || !context.mounted) return;
  if (!isBrowsableLink(url) || !embeddedBrowserEnabled(context)) {
    await openUri(context, url);
    return;
  }
  await Navigator.push(
    context,
    MaterialPageRoute(
      builder: (_) =>
          LinkBrowserScreen(url: url, title: title, post: post, openNative: openNative, pageFactory: pageFactory),
    ),
  );
}

/// Pushes [screen], a reader built on the platform web view. The desktop has
/// none, so there the page at [url] opens in the reader's browser instead.
Future<void> pushWebViewScreen(BuildContext context, {required String? url, required Widget Function() screen}) async {
  if (!isDesktop) {
    await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => screen()));
    return;
  }
  if (url != null && url.isNotEmpty) await openUri(context, url);
}
