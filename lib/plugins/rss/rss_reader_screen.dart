import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/rss/rss_group.dart';
import 'package:xta/plugins/rss/rss_html.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/ui/dates.dart';

class RssReaderScreen extends StatefulWidget {
  final RssItem item;

  const RssReaderScreen({super.key, required this.item});

  @override
  State<RssReaderScreen> createState() => _RssReaderScreenState();
}

class _RssReaderScreenState extends State<RssReaderScreen> {
  WebViewController? _controller;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<RssReadStore>().markRead(widget.item.id);
    });
    if (widget.item.hasReadableBody) {
      _controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.disabled);
      WidgetsBinding.instance.addPostFrameCallback((_) => _loadHtml());
    }
  }

  void _loadHtml() {
    final controller = _controller;
    if (controller == null) return;
    final dark = Theme.of(context).brightness == Brightness.dark;
    controller.loadHtmlString(
      rssReaderDocument(
        title: widget.item.title,
        bodyHtml: sanitizeRssBodyHtml(widget.item.bodyHtml ?? ''),
        dark: dark,
      ),
    );
  }

  Future<void> _openBrowser() async {
    final link = widget.item.link;
    if (link == null || link.isEmpty) return;
    await openLink(context, link);
  }

  Future<void> _addToGroup() async {
    final feeds = context.read<RssFeedsStore>().state;
    RssFeed? feed;
    for (final candidate in feeds) {
      if (candidate.id == widget.item.feedId) {
        feed = candidate;
        break;
      }
    }
    feed ??= RssFeed(
      id: widget.item.feedId,
      feedUrl: widget.item.feedId,
      name: widget.item.feedTitle,
    );
    if (!mounted) return;
    await addRssFeedToGroup(context, feed);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final item = widget.item;
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(item.feedTitle),
        actions: [
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
      body: _controller == null
          ? _fallback(context, l10n, theme)
          : WebViewWidget(controller: _controller!),
    );
  }

  Widget _fallback(BuildContext context, L10n l10n, ThemeData theme) {
    final item = widget.item;
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
