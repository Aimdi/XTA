import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/links/link_browser_screen.dart';
import 'package:xta/links/link_opening.dart';

/// A long-form X article, read inside XTA.
///
/// Tapping one used to hand the reader to a browser — a custom tab at best,
/// which is still leaving the app: their tabs, their history, their session.
/// The article is the post's content, so it opens where the post did, with the
/// way out still offered rather than taken for them.
bool canOpenInArticleScreen(String url) => isBrowsableLink(url);

typedef ArticleNativeLinkHandler = LinkNativeHandler;

/// The in-app browser, named for the X article it was first written for.
class ArticleScreen extends StatelessWidget {
  final String url;
  final String? title;
  final ArticleNativeLinkHandler? openNative;

  const ArticleScreen({
    super.key,
    required this.url,
    this.title,
    this.openNative,
  });

  @override
  Widget build(BuildContext context) {
    final named = title?.trim() ?? '';
    return LinkBrowserScreen(
      url: url,
      title: named.isNotEmpty ? named : L10n.of(context).article_on_x,
      openNative: openNative,
    );
  }
}
