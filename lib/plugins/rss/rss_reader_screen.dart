import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/rss/rss_group.dart';
import 'package:xta/plugins/rss/rss_html.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/offline/offline_article.dart';
import 'package:xta/offline/offline_article_action.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/reading/article_reader_controls.dart';
import 'package:xta/reading/article_reading_bridge.dart';
import 'package:xta/reading/article_reading_store.dart';

class RssReaderScreen extends StatefulWidget {
  final RssItem item;

  const RssReaderScreen({super.key, required this.item});

  @override
  State<RssReaderScreen> createState() => _RssReaderScreenState();
}

class _RssReaderContentStore extends Store<RssItem> {
  _RssReaderContentStore(RssItem item) : super(item);
}

class _RssReaderScreenState extends State<RssReaderScreen> with WidgetsBindingObserver {
  late final WebViewController _controller;
  late final _RssReaderContentStore _content;
  late final ArticleReadingStore _reading;
  RssItem get _item => _content.state;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _content = _RssReaderContentStore(widget.item);
    final read = context.read<RssReadStore>();
    _reading = ArticleReadingStore(
      prefs: read.prefs,
      articleId: 'rss:${_item.feedId}:${_item.id}',
      onCompleted: () => read.markRead(_item.id),
      alreadyCompleted: read.isRead(_item.id),
    );
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..addJavaScriptChannel('XtaReading', onMessageReceived: (message) {
        if (mounted && ModalRoute.of(context)?.isCurrent == true) {
          _reading.receiveProgress(message.message);
        }
      })
      ..setNavigationDelegate(NavigationDelegate(
        onPageFinished: (_) => _applyReadingAppearance(),
        onNavigationRequest: (request) {
          if (request.url.startsWith('about:blank') || request.url.startsWith('data:')) {
            return NavigationDecision.navigate;
          }
          unawaited(openLink(context, request.url));
          return NavigationDecision.prevent;
        },
      ));
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadHtml());
  }

  Future<void> _loadHtml() async {
    final pinned = await OfflineStore.shared.article(_reading.articleId);
    if (!mounted) return;
    final savedItem = pinned?.rssItem;
    if (savedItem != null) _content.update(savedItem);
    _reading.allowAutomaticCompletion = _item.hasReadableBody;
    if (!_item.hasReadableBody) return;
    final scheme = Theme.of(context).colorScheme;
    final document = rssReaderDocument(
      title: _item.title,
      bodyHtml: sanitizeRssBodyHtml(_item.bodyHtml ?? ''),
      dark: Theme.of(context).brightness == Brightness.dark,
      background: _cssColor(scheme.surface),
      foreground: _cssColor(scheme.onSurface),
      link: _cssColor(scheme.primary),
      fontSizePx: MediaQuery.textScalerOf(context).scale(18) * _reading.state.fontSize / 18,
      lineHeight: _reading.state.lineHeight,
    );
    final page = await OfflineStore.shared.renderArticle(_reading.articleId, document);
    if (!mounted) return;
    await _controller.loadHtmlString(page, baseUrl: _item.link);
  }

  Future<void> _applyReadingAppearance() async {
    if (!mounted || !_item.hasReadableBody) return;
    final textScale = MediaQuery.textScalerOf(context).scale(18) / 18;
    try {
      await _controller.runJavaScript(articleReadingBridge(_reading.state, textScale: textScale));
      _reading.setActive(true);
    } catch (_) {
      // Article text remains readable when the platform bridge is unavailable.
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _reading.setActive(state == AppLifecycleState.resumed && _item.hasReadableBody);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_reading.destroy());
    unawaited(_content.destroy());
    super.dispose();
  }

  Future<void> _openBrowser() async {
    final link = _item.link;
    if (link == null || link.isEmpty) return;
    await openLink(context, link);
  }

  Future<void> _addToGroup() async {
    final feeds = context.read<RssFeedsStore>().state;
    RssFeed? feed;
    for (final candidate in feeds) {
      if (candidate.id == _item.feedId) {
        feed = candidate;
        break;
      }
    }
    feed ??= RssFeed(
      id: _item.feedId,
      feedUrl: _item.feedId,
      name: _item.feedTitle,
    );
    if (!mounted) return;
    await addRssFeedToGroup(context, feed);
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<_RssReaderContentStore, RssItem>(
    store: _content,
    onState: (context, _) => _buildReader(context),
  );

  Widget _buildReader(BuildContext context) {
    final l10n = L10n.of(context);
    final item = _item;
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(item.feedTitle),
        actions: [
          if (item.hasReadableBody) OfflineArticleAction(article: OfflineArticle.rss(item)),
          IconButton(
            tooltip: l10n.plugin_rss_add_to_group,
            icon: const Icon(Icons.group_add_outlined),
            onPressed: _addToGroup,
          ),
          if (item.link != null)
            IconButton(
              tooltip: l10n.plugin_rss_open_browser,
              icon: const Icon(Icons.open_in_new),
              onPressed: _openBrowser,
            ),
        ],
      ),
      body: Column(children: [
        ArticleReaderControls(
          store: _reading,
          supportsAppearance: item.hasReadableBody,
          onAppearanceChanged: _applyReadingAppearance,
          onStartOver: () => _controller.runJavaScript('window.xtaArticle?.startOver();'),
        ),
        Expanded(child: !item.hasReadableBody
          ? _fallback(context, l10n, theme)
          : WebViewWidget(controller: _controller)),
      ]),
    );
  }

  Widget _fallback(BuildContext context, L10n l10n, ThemeData theme) {
    final item = _item;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 48),
      children: [
        Text(
          item.title,
          style: theme.textTheme.headlineSmall!.copyWith(
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          [
            item.author,
            if (item.publishedAt != null) createRelativeDate(item.publishedAt!),
          ].whereType<String>().join(' · '),
          style: theme.textTheme.bodySmall!.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        if (item.excerpt != null) ...[
          const SizedBox(height: 16),
          Text(item.excerpt!, style: theme.textTheme.bodyLarge),
        ],
        const SizedBox(height: 24),
        Text(l10n.plugin_rss_no_article),
        if (item.link != null) ...[
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _openBrowser,
            icon: const Icon(Icons.open_in_new),
            label: Text(l10n.plugin_rss_open_browser),
          ),
        ],
      ],
    );
  }
}

String _cssColor(Color color) => '#${color.toARGB32().toRadixString(16).padLeft(8, '0').substring(2)}';
