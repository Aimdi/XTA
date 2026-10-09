import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/links/link_browser_page.dart';
import 'package:xta/links/link_browser_store.dart';
import 'package:xta/links/link_post_context.dart';
import 'package:xta/links/link_post_context_bar.dart';
import 'package:xta/links/link_preview_card.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/browsers.dart';
import 'package:xta/utils/urls.dart';

/// Opens a link inside XTA when an XTA screen can read it; true when it did.
typedef LinkNativeHandler =
    Future<bool> Function(BuildContext context, String url);

/// Shares a link; the platform share sheet in the app.
typedef LinkSharer = Future<void> Function(String url);

Future<void> _shareWithPlatform(String url) =>
    SharePlus.instance.share(ShareParams(text: url));

enum LinkBrowserAction { openExternally, copyLink, share, reload }

/// A web page read inside XTA, with the post it came from kept in reach.
///
/// The bar at the top says where the page is; the one at the bottom says
/// where it came from and goes back there. Leaving for a real browser stays
/// one tap away in the menu.
class LinkBrowserScreen extends StatefulWidget {
  final String url;
  final String? title;
  final LinkPostContext? post;
  final LinkNativeHandler? openNative;
  final LinkBrowserPageFactory pageFactory;
  final LinkSharer share;

  const LinkBrowserScreen({
    super.key,
    required this.url,
    this.title,
    this.post,
    this.openNative,
    this.pageFactory = webViewLinkBrowserPage,
    this.share = _shareWithPlatform,
  });

  @override
  State<LinkBrowserScreen> createState() => _LinkBrowserScreenState();
}

class _LinkBrowserScreenState extends State<LinkBrowserScreen> {
  late final LinkBrowserStore _store = LinkBrowserStore(widget.url);
  late final LinkBrowserPage _page = widget.pageFactory(_store, _guard);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final url = prepareUrl(
        PrefService.of(context, listen: false),
        widget.url,
      );
      _store.started(url);
      unawaited(_page.load(url));
    });
  }

  @override
  void dispose() {
    unawaited(_store.destroy());
    super.dispose();
  }

  Future<bool> _guard(String url) async {
    final openNative = widget.openNative;
    if (openNative == null || !mounted) return false;
    return openNative(context, url);
  }

  Future<void> _onBack(bool didPop, Object? _) async {
    if (didPop || await _page.back() || !mounted) return;
    Navigator.of(context).pop();
  }

  void _backToPost() {
    Navigator.of(context).pop();
    widget.post?.openPost?.call();
  }

  @override
  Widget build(BuildContext context) {
    final background =
        XLookTokens.maybeOf(context)?.background ??
        Theme.of(context).scaffoldBackgroundColor;
    return XtaSystemBars(
      child: PopScope(
        canPop: false,
        onPopInvokedWithResult: _onBack,
        child: Scaffold(
          backgroundColor: background,
          body: SafeArea(
            bottom: false,
            child: Column(
              children: [
                ScopedBuilder<LinkBrowserStore, LinkBrowserState>(
                  store: _store,
                  onState: (context, state) => Column(
                    children: [
                      _LinkBrowserTopBar(
                        state: state,
                        source: _subtitle(context),
                        onClose: () => Navigator.of(context).pop(),
                        onAction: _act,
                      ),
                      _progress(state),
                    ],
                  ),
                ),
                Expanded(child: _body(context)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// The network the link came from, as Threads names itself under the
  /// domain; without a post, the title the link was opened with.
  String? _subtitle(BuildContext context) {
    final post = widget.post;
    if (post != null) return pluginById(post.sourceId)?.title(context);
    final title = widget.title?.trim();
    return title == null || title.isEmpty ? null : title;
  }

  Widget _progress(LinkBrowserState state) => SizedBox(
    height: 2,
    child: state.loading
        ? LinearProgressIndicator(
            value: state.progress > 0 ? state.progress : null,
            minHeight: 2,
          )
        : Divider(height: 2, thickness: kTweetDividerThickness),
  );

  Widget _body(BuildContext context) {
    final post = widget.post;
    final page = _page.build(context);
    if (post == null) return page;
    final inset = MediaQuery.paddingOf(context).bottom;
    return Stack(
      children: [
        Positioned.fill(child: page),
        Positioned(
          left: kTweetSpace3,
          right: kTweetSpace3,
          bottom: kTweetSpace3 + inset,
          child: LinkPostContextBar(
            post: post,
            onBackToPost: _backToPost,
            onSharePost: post.postUrl == null
                ? null
                : () => widget.share(post.postUrl!),
          ),
        ),
      ],
    );
  }

  Future<void> _act(LinkBrowserAction action) async {
    final prefs = PrefService.of(context, listen: false);
    final url = prepareUrl(prefs, _store.state.url);
    switch (action) {
      case LinkBrowserAction.openExternally:
        await openExternally(
          url,
          package:
              prefs.get<String>(optionExternalBrowser) ?? systemDefaultBrowser,
        );
      case LinkBrowserAction.copyLink:
        final messenger = ScaffoldMessenger.maybeOf(context);
        final copied = L10n.of(context).link_browser_link_copied;
        await Clipboard.setData(ClipboardData(text: url));
        messenger?.showSnackBar(SnackBar(content: Text(copied)));
      case LinkBrowserAction.share:
        await widget.share(url);
      case LinkBrowserAction.reload:
        await _page.reload();
    }
  }
}

/// ✕, the padlock and the domain over where the link came from, and ⋯.
class _LinkBrowserTopBar extends StatelessWidget {
  final LinkBrowserState state;
  final String? source;
  final VoidCallback onClose;
  final ValueChanged<LinkBrowserAction> onAction;

  const _LinkBrowserTopBar({
    required this.state,
    required this.source,
    required this.onClose,
    required this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 56),
      child: Row(
        children: [
          IconButton(
            tooltip: l10n.close,
            onPressed: onClose,
            icon: Icon(Icons.close, color: tweetPrimaryColor(context)),
          ),
          Expanded(child: _location(context, l10n)),
          _LinkBrowserMenu(onAction: onAction),
        ],
      ),
    );
  }

  Widget _location(BuildContext context, L10n l10n) {
    final secondary = tweetMetadataStyle(context);
    final subtitle = source;
    return Semantics(
      header: true,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                state.secure ? Icons.lock_outline : Icons.lock_open_outlined,
                size: 14,
                color: tweetSecondaryColor(context),
                semanticLabel: state.secure
                    ? l10n.link_browser_secure
                    : l10n.link_browser_not_secure,
              ),
              const SizedBox(width: kTweetSpace1),
              Flexible(
                child: Text(
                  linkDomain(state.url),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: tweetDisplayNameStyle(context),
                ),
              ),
            ],
          ),
          if (subtitle != null && subtitle.isNotEmpty)
            Text(
              subtitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: secondary,
            ),
        ],
      ),
    );
  }
}

class _LinkBrowserMenu extends StatelessWidget {
  final ValueChanged<LinkBrowserAction> onAction;

  const _LinkBrowserMenu({required this.onAction});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final color = tweetPrimaryColor(context);
    return PopupMenuButton<LinkBrowserAction>(
      tooltip: l10n.link_browser_more_options,
      onSelected: onAction,
      icon: DecoratedBox(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(color: tweetDividerColor(context)),
        ),
        child: Padding(
          padding: const EdgeInsets.all(3),
          child: Icon(Icons.more_horiz, size: 20, color: color),
        ),
      ),
      itemBuilder: (context) => [
        _item(
          LinkBrowserAction.openExternally,
          Icons.open_in_new,
          l10n.open_in_browser,
        ),
        _item(
          LinkBrowserAction.copyLink,
          Icons.link,
          l10n.link_browser_copy_link,
        ),
        _item(LinkBrowserAction.share, Icons.share_outlined, l10n.share_link),
        _item(
          LinkBrowserAction.reload,
          Icons.refresh,
          l10n.link_browser_reload,
        ),
      ],
    );
  }

  PopupMenuItem<LinkBrowserAction> _item(
    LinkBrowserAction action,
    IconData icon,
    String label,
  ) => PopupMenuItem(
    value: action,
    child: Row(
      children: [
        Icon(icon, size: 20),
        const SizedBox(width: kTweetSpace3),
        Expanded(child: Text(label)),
      ],
    ),
  );
}
