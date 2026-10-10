import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_user_link.dart';
import 'package:xta/plugins/pixiv/pixivision_parser.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';

/// Opens Pixivision article [id]; [article] is the list's copy, shown while
/// the page loads, and [webUrl] the link it was opened from.
Future<void> openPixivisionArticle(BuildContext context, int id, {PixivSpotlightArticle? article, String? webUrl}) =>
    Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) => PixivisionArticleScreen(articleId: id, article: article, webUrl: webUrl),
      ),
    );

/// One Pixivision article, read from its web page.
class PixivisionArticleStore extends Store<PixivisionArticle?> {
  final PixivDiscoveryApi api;
  final int articleId;

  PixivisionArticleStore(this.api, this.articleId) : super(null);

  Future<void> load() => execute(() => api.pixivisionArticle(articleId));

  /// A pull on a shown article fetches it quietly and keeps it if that fails;
  /// with nothing shown yet it is a first load.
  Future<void> refresh() async {
    if (state == null) return load();
    try {
      update(await api.pixivisionArticle(articleId));
    } catch (_) {
      // The article on screen stays: a failed pull must not blank it.
    }
  }
}

/// A Pixivision article: its picture and title, the intro, then each featured
/// work as a card that opens the work.
class PixivisionArticleScreen extends StatefulWidget {
  final int articleId;
  final PixivSpotlightArticle? article;

  /// The page a link named, which Share and Open in browser keep: the article
  /// loads in the reader's language, and a link may name one it is not in.
  final String? webUrl;

  const PixivisionArticleScreen({super.key, required this.articleId, this.article, this.webUrl});

  @override
  State<PixivisionArticleScreen> createState() => _PixivisionArticleScreenState();
}

class _PixivisionArticleScreenState extends State<PixivisionArticleScreen> {
  late final PixivisionArticleStore _store;
  late final String _language;

  @override
  void initState() {
    super.initState();
    final api = PixivDiscoveryApi.of(context);
    _language = pixivisionLanguage(api.client.locale());
    _store = PixivisionArticleStore(api, widget.articleId)..load();
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  String get _url => switch (widget.article?.articleUrl ?? widget.webUrl) {
    final url? when url.isNotEmpty => url,
    _ => pixivisionArticleUrl(widget.articleId, _language),
  };

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivisionArticleStore, PixivisionArticle?>(
    store: _store,
    onLoading: (context) => _page(context, null, [_filler(const Center(child: CircularProgressIndicator()))]),
    onError: (context, error) => _page(context, null, [
      _filler(
        Padding(
          padding: const EdgeInsets.all(24),
          child: FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: pixivErrorMessage(L10n.of(context), error ?? Exception()),
            onRetry: _store.load,
          ),
        ),
      ),
    ]),
    onState: (context, article) => _page(context, article, [if (article != null) ..._content(context, article)]),
  );

  Widget _filler(Widget child) => SliverFillRemaining(child: child);

  Widget _page(BuildContext context, PixivisionArticle? article, List<Widget> body) {
    final title = article?.title.isNotEmpty == true
        ? article!.title
        : widget.article?.displayTitle ?? L10n.of(context).plugin_pixiv_pixivision_articles;
    final cover = widget.article?.thumbnailUrl.isNotEmpty == true ? widget.article!.thumbnailUrl : article?.coverUrl;
    return Scaffold(
      body: RefreshIndicator(
        onRefresh: _store.refresh,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [_appBar(context, title, cover), ...body],
        ),
      ),
    );
  }

  /// Over the picture the bar is black with white text in every theme, so the
  /// title stays readable on the image and once collapsed.
  Widget _appBar(BuildContext context, String title, String? cover) {
    final l10n = L10n.of(context);
    final pictured = cover != null && cover.isNotEmpty;
    return SliverAppBar(
      pinned: true,
      expandedHeight: pictured ? 220 : null,
      backgroundColor: pictured ? Colors.black : null,
      foregroundColor: pictured ? Colors.white : null,
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      flexibleSpace: pictured ? FlexibleSpaceBar(background: _cover(cover)) : null,
      actions: [
        IconButton(
          tooltip: l10n.share_link,
          icon: const Icon(Icons.share_outlined),
          onPressed: () => SharePlus.instance.share(ShareParams(text: _url)),
        ),
        IconButton(
          tooltip: l10n.open_in_browser,
          icon: const Icon(Icons.open_in_browser),
          onPressed: () => openUri(context, _url),
        ),
      ],
    );
  }

  Widget _cover(String url) => Stack(
    fit: StackFit.expand,
    children: [
      PixivNetworkImage(url: url, fit: BoxFit.cover),
      const DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.center,
            colors: [Colors.black87, Colors.transparent],
          ),
        ),
      ),
    ],
  );

  List<Widget> _content(BuildContext context, PixivisionArticle article) {
    final theme = Theme.of(context);
    return [
      SliverPadding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
        sliver: SliverList.list(
          children: [
            if (article.title.isNotEmpty)
              Text(article.title, style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w800)),
            if (article.intro.isNotEmpty) ...[const SizedBox(height: 12), PixivisionIntroCard(intro: article.intro)],
            if (article.works.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text(L10n.of(context).plugin_pixiv_pixivision_featured, style: theme.textTheme.titleMedium),
            ],
          ],
        ),
      ),
      ScopedBuilder<PixivMuteStore, PixivMuteState>(
        store: context.read<PixivMuteStore>(),
        onState: (context, mute) => _works(context, pixivisionVisibleWorks(article.works, mute)),
      ),
    ];
  }

  Widget _works(BuildContext context, List<PixivisionWork> works) => SliverPadding(
    padding: pluginFeedPadding(context, extra: const EdgeInsets.symmetric(horizontal: 16)),
    sliver: SliverList.separated(
      itemCount: works.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) => PixivisionWorkCard(work: works[index]),
    ),
  );
}

/// The featured works the reader's mutes let through. An article names no
/// tags, so a muted tag still waits behind its notice when the work is opened.
List<PixivisionWork> pixivisionVisibleWorks(List<PixivisionWork> works, PixivMuteState mute) => [
  for (final work in mute.withoutMutedAuthors(works, (work) => work.userId))
    if (!mute.illustIds.contains(work.artworkId)) work,
];

class PixivisionIntroCard extends StatelessWidget {
  final String intro;

  const PixivisionIntroCard({super.key, required this.intro});

  @override
  Widget build(BuildContext context) => Card.filled(
    margin: EdgeInsets.zero,
    child: Padding(
      padding: const EdgeInsets.all(14),
      child: SelectableText(intro, style: Theme.of(context).textTheme.bodyMedium),
    ),
  );
}

/// A featured work: its picture, title and artist. The card opens the work,
/// the artist row their profile.
class PixivisionWorkCard extends StatelessWidget {
  final PixivisionWork work;

  const PixivisionWorkCard({super.key, required this.work});

  Future<void> _openWork(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final failed = L10n.of(context).plugin_pixiv_open_link_failed;
    if (!await openPixivLinkRef(context, PixivLinkRef.artwork(work.artworkId))) {
      messenger.showSnackBar(SnackBar(content: Text(failed)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final image = work.imageUrl;
    return Card.outlined(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: ValueKey('pixivision-work-${work.artworkId}'),
        onTap: () => _openWork(context),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AspectRatio(
              aspectRatio: 4 / 3,
              child: ColoredBox(
                color: theme.colorScheme.surfaceContainerHighest,
                child: image == null ? null : PixivNetworkImage(url: image, fit: BoxFit.cover),
              ),
            ),
            if (work.title.isNotEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
                child: Text(work.title, style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700)),
              ),
            _artist(context),
          ],
        ),
      ),
    );
  }

  Widget _artist(BuildContext context) => PixivUserLink(
    userId: work.userId,
    name: work.userName,
    avatarUrl: work.avatarUrl,
    avatarSize: 28,
    style: Theme.of(context).textTheme.bodyMedium,
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
  );
}
