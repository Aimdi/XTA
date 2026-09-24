import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/utils/browsers.dart';
import 'package:xta/utils/urls.dart';
import 'package:share_plus/share_plus.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// A long-form X article, read inside XTA.
///
/// Tapping one used to hand the reader to a browser — a custom tab at best,
/// which is still leaving the app: their tabs, their history, their session.
/// The article is the post's content, so it opens where the post did, with the
/// way out still offered rather than taken for them.
bool canOpenInArticleScreen(String url) {
  final uri = Uri.tryParse(url);
  return uri != null &&
      (uri.scheme == 'http' || uri.scheme == 'https') &&
      uri.host.isNotEmpty;
}

class ArticleScreen extends StatefulWidget {
  final String url;
  final String? title;

  const ArticleScreen({super.key, required this.url, this.title});

  @override
  State<ArticleScreen> createState() => _ArticleScreenState();
}

class _ArticleScreenState extends State<ArticleScreen> {
  late final WebViewController _controller;
  var _loading = true;
  var _requested = false;
  late String _currentUrl;

  @override
  void initState() {
    super.initState();
    _currentUrl = widget.url;
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (url) {
            if (!mounted) return;
            setState(() {
              _currentUrl = url;
              _loading = true;
            });
          },
          onPageFinished: (url) {
            if (!mounted) return;
            setState(() {
              _currentUrl = url;
              _loading = false;
            });
          },
          onWebResourceError: (_) {
            if (mounted) setState(() => _loading = false);
          },
          onNavigationRequest: (request) async {
            final uri = Uri.tryParse(request.url);
            if (uri == null) return NavigationDecision.prevent;
            if (uri.scheme == 'http' || uri.scheme == 'https') {
              if (await openNativeLink(context, request.url)) {
                return NavigationDecision.prevent;
              }
              return NavigationDecision.navigate;
            }
            return switch (uri.scheme) {
              'about' || 'data' || 'blob' => NavigationDecision.navigate,
              _ => NavigationDecision.prevent,
            };
          },
        ),
      );
    // Prefs are not available until [didChangeDependencies]. The request is
    // issued there so the clean-links switch is honoured on first load.
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_requested) return;
    _requested = true;
    final url = prepareUrl(PrefService.of(context, listen: false), widget.url);
    _currentUrl = url;
    _controller.loadRequest(Uri.parse(url));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return XtaSystemBars(
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            widget.title?.trim().isNotEmpty == true
                ? widget.title!.trim()
                : l10n.article_on_x,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          actions: [
            IconButton(
              tooltip: l10n.share_link,
              icon: const Icon(Icons.share_outlined),
              onPressed: () {
                SharePlus.instance.share(ShareParams(text: _currentUrl));
              },
            ),
            // Still offered, because an article that will not render in here
            // has to be readable somewhere. Goes to the browser the reader
            // chose, and out of the app rather than into an embedded view —
            // asking for a browser is asking to leave.
            IconButton(
              tooltip: l10n.open_in_browser,
              icon: const Icon(Icons.open_in_new),
              onPressed: () {
                final prefs = PrefService.of(context, listen: false);
                openExternally(
                  _currentUrl,
                  package:
                      prefs.get<String>(optionExternalBrowser) ??
                      systemDefaultBrowser,
                );
              },
            ),
          ],
        ),
        body: Stack(
          children: [
            WebViewWidget(controller: _controller),
            if (_loading) const LinearProgressIndicator(minHeight: 2),
          ],
        ),
      ),
    );
  }
}
