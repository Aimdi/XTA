import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/offline/offline_article.dart';
import 'package:xta/offline/offline_article_action.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/reading/article_block_reading.dart';
import 'package:xta/reading/article_reader_controls.dart';
import 'package:xta/reading/article_reading_bridge.dart';
import 'package:xta/reading/article_reading_store.dart';
import 'package:xta/plugins/substack/substack_error_view.dart';
import 'package:xta/plugins/substack/substack_reader_store.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/substack/substack_article_cache.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_article_navigation_sheet.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_blocks.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_native_article.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_audio_player.dart';
import 'package:xta/plugins/substack/substack_comments_screen.dart';
import 'package:xta/plugins/substack/substack_links.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/speech/speech_store.dart';
import 'package:xta/speech/tts_engines.dart';
import 'package:xta/speech/tts_settings.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/utils/background_parse.dart';
import 'package:xta/utils/urls.dart';
import 'package:share_plus/share_plus.dart';
import 'package:webview_flutter/webview_flutter.dart';

class SubstackReaderScreen extends StatefulWidget {
  final SubstackPost post;

  const SubstackReaderScreen({super.key, required this.post});

  @override
  State<SubstackReaderScreen> createState() => _SubstackReaderScreenState();
}

class _SubstackReaderScreenState extends State<SubstackReaderScreen> with WidgetsBindingObserver {
  late final WebViewController _controller;
  final _articleCache = SubstackArticleCache();
  late final SubstackReaderStore _content;
  late final ArticleReadingStore _reading;
  late final _place = ArticleBlockReading(_reading, blocks: () => _content.state.blocks?.length ?? 0);
  SubstackPost get _post => _content.state.post;
  Object? get _error => _content.state.error;
  bool get _loading => _content.state.loading;
  bool get _empty => _content.state.empty;
  bool get _paywalled => _content.state.paywalled;
  bool get _partial => _content.state.partial;
  String? get _speakText => _content.state.speakText;
  bool get _liveSite => _content.state.liveSite;
  String get _offlineId => OfflineArticle.substack(_post).id;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _content = SubstackReaderStore(widget.post);
    final read = context.read<SubstackReadStore>();
    _reading = ArticleReadingStore(
      prefs: read.prefs,
      articleId: substackArticleReadingId(widget.post),
      onCompleted: () => read.markPostRead(_post),
      alreadyCompleted: read.isPostRead(widget.post),
      allowAutomaticCompletion: false,
    );
    // Seed speech from whatever we already know — title and excerpt — so Listen
    // is available before the body finishes loading.
    _content.change(speakText: _fallbackSpeakText(_post));
    // Trusted reading progress and speech helpers use the JavaScript channels;
    // publisher scripts and handlers are removed before loading article HTML.
    _controller = WebViewController()..setJavaScriptMode(JavaScriptMode.unrestricted);
    _controller.addJavaScriptChannel(
      'XtaReading',
      onMessageReceived: (message) {
        if (mounted && !_liveSite && ModalRoute.of(context)?.isCurrent == true) {
          _reading.receiveProgress(message.message);
        }
      },
    );
    _controller.addJavaScriptChannel(
      'XtaTts',
      onMessageReceived: (message) {
        if (!mounted || _liveSite || ModalRoute.of(context)?.isCurrent != true) return;
        _speakFromHere(message.message);
      },
    );
    _stopSpinnerWhenLoaded();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _load();
    });
  }

  String _fallbackSpeakText(SubstackPost post, {String? bodyHtml, String? bodyPlain}) {
    return buildSubstackSpeakText(
      title: post.title,
      subtitle: post.excerpt,
      authorName: post.authorName,
      publicationName: post.publicationName,
      bodyHtml: bodyHtml ?? post.bodyHtml,
      bodyPlain: bodyPlain,
    );
  }

  NavigationDecision _onNavigation(NavigationRequest request) {
    if (!request.isMainFrame) return NavigationDecision.navigate;
    final url = request.url;
    if (url.startsWith('about:blank') || url.startsWith('data:')) {
      return NavigationDecision.navigate;
    }
    // Same article (canonical or relative) may reload; let the webview keep it.
    final canonical = _post.canonicalUrl;
    if (_liveSite && canonical != null && url.split('#').first == canonical.split('#').first) {
      return NavigationDecision.navigate;
    }
    final fragment = substackArticleFragment(url, canonical);
    if (!_liveSite && fragment != null) {
      unawaited(_controller.runJavaScript(substackArticleJumpJs(fragment, publisherAnchor: true)));
      return NavigationDecision.prevent;
    }
    final link = substackLinkFor(context, url);
    if (link != null && (link.slug != _post.slug || link.publicationBase.origin != _post.publicationBaseUrl)) {
      unawaited(
        _openReaderRoute(SubstackReaderScreen(post: substackPostStub(link, publicationName: _post.publicationName))),
      );
      return NavigationDecision.prevent;
    }
    if (_liveSite) return NavigationDecision.navigate;
    unawaited(openLink(context, url));
    return NavigationDecision.prevent;
  }

  Future<SubstackPost> _resolvePost(SubstackClient client, SubstackPost post, {required bool force}) async {
    if (!force) {
      final pinned = await OfflineStore.shared.article(
        OfflineArticle.substack(post).id,
        canonicalUrl: post.canonicalUrl ?? '${post.publicationBaseUrl}/p/${post.slug}',
      );
      final offline = pinned?.substackPost;
      if (offline?.bodyHtml?.trim().isNotEmpty == true) return offline!;
      final cached = await _articleCache.get(post.publication, post.slug);
      if (cached != null && cached.bodyHtml?.trim().isNotEmpty == true && substackCachedCopyIsCurrent(cached, post)) {
        return cached;
      }
      if (post.bodyHtml?.trim().isNotEmpty == true) return post;
    }
    return client.fetchPost(post.publication, post.slug);
  }

  Future<void> _load({bool force = false}) async {
    final client = context.read<SubstackClient>();
    final post = _post;
    final canRetain = !_liveSite && _content.state.document != null;
    final generation = _content.generation + 1;
    final loaded = await _content.load(() => _resolvePost(client, post, force: force), refresh: force && canRetain);
    if (!mounted || !_content.accepts(generation)) return;
    try {
      if (loaded != null) {
        unawaited(_articleCache.put(loaded));
        await _showContent(loaded);
      } else if (_error != null && post.canonicalUrl != null) {
        _content.change(error: null, paywalled: false, partial: false, empty: false, loading: true);
        await _loadLiveSite(post.canonicalUrl!);
      }
    } catch (error) {
      if (mounted && _content.accepts(generation)) _content.change(error: error, loading: false);
    }
  }

  /// Every load installs the same delegate: the spinner goes when the page
  /// arrives, and a tapped link goes to the right place — another article opens
  /// in this reader, anything else leaves for the browser — instead of
  /// navigating this screen away from the post it is showing.
  void _stopSpinnerWhenLoaded({bool extractLiveText = false}) {
    final generation = _content.generation;
    _controller.setNavigationDelegate(
      NavigationDelegate(
        onNavigationRequest: _onNavigation,
        onPageFinished: (_) async {
          if (!mounted || !_content.accepts(generation) || _error != null) return;
          _content.change(loading: false);
          if (!extractLiveText) await _applyReadingAppearance();
          if (extractLiveText) {
            await _extractLiveSpeakText();
          }
        },
        onWebResourceError: (error) {
          if (!mounted || !_content.accepts(generation) || error.isForMainFrame != true) return;
          _reading.setActive(false);
          _content.change(error: StateError(error.description), loading: false);
        },
      ),
    );
  }

  /// Pull readable prose out of the live Substack page for TTS.
  Future<void> _extractLiveSpeakText() async {
    try {
      final raw = await _controller.runJavaScriptReturningResult(r'''
(function() {
  var root = document.querySelector('div.available-content')
    || document.querySelector('article')
    || document.querySelector('[data-testid="post-content"]')
    || document.querySelector('.body.markup')
    || document.body;
  if (!root) return '';
  var clone = root.cloneNode(true);
  clone.querySelectorAll('script,style,nav,button,svg,iframe,form,.subscription-widget-wrap,.paywall').forEach(function(n){ n.remove(); });
  return (clone.innerText || '').replace(/\n{3,}/g, '\n\n').trim().slice(0, 120000);
})()
''');
      final plain = _jsStringResult(raw);
      if (plain == null || plain.trim().length < 40) return;
      if (!mounted) return;

      _content.change(speakText: _fallbackSpeakText(_post, bodyPlain: plain));
    } catch (_) {
      // Title/excerpt fallback already set; leave Listen working with that.
    }
  }

  /// `runJavaScriptReturningResult` may wrap strings in JSON quotes.
  String? _jsStringResult(Object? raw) {
    if (raw == null) return null;
    if (raw is! String) return raw.toString();
    final trimmed = raw.trim();
    if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
      try {
        final decoded = jsonDecode(trimmed);
        if (decoded is String) return decoded;
      } catch (_) {
        return trimmed.substring(1, trimmed.length - 1);
      }
    }
    return trimmed;
  }

  /// The publication's own page, when there was no body to render into one of
  /// ours. A real website, so it gets the scripting the article view does
  /// without.
  Future<void> _loadLiveSite(String url) async {
    _reading.allowAutomaticCompletion = false;
    _reading.setActive(false);
    _content.change(liveSite: true, document: null, blocks: null);
    if (_speakText == null || _speakText!.trim().isEmpty) {
      _content.change(speakText: _fallbackSpeakText(_post));
    }
    _stopSpinnerWhenLoaded(extractLiveText: true);
    await _controller.setJavaScriptMode(JavaScriptMode.unrestricted);
    await _controller.loadRequest(Uri.parse(url));
  }

  Future<void> _showContent(SubstackPost post) async {
    if (context.read<SubstackReadStore>().isPostRead(post)) unawaited(_reading.complete());
    switch (substackReaderBodyFor(post)) {
      case SubstackReaderBody.article:
        await _showArticle(post, post.bodyHtml!);
      case SubstackReaderBody.liveSite:
        _content.change(paywalled: false, partial: false, empty: false, speakText: _fallbackSpeakText(post));
        await _loadLiveSite(post.canonicalUrl!);
      case SubstackReaderBody.paywall:
        _showPane(post, paywalled: true);
      case SubstackReaderBody.empty:
        _showPane(post, paywalled: false);
    }
  }

  /// The paid pane or the empty note: nothing goes into the web view.
  void _showPane(SubstackPost post, {required bool paywalled}) {
    _reading.allowAutomaticCompletion = false;
    _reading.setActive(false);
    _content.change(
      blocks: null,
      paywalled: paywalled,
      empty: !paywalled,
      partial: false,
      loading: false,
      liveSite: false,
      speakText: _fallbackSpeakText(post),
    );
  }

  // A paid post usually arrives with its opening paragraphs — the part the
  // publication chose to give away. Refusing to render any of it because the
  // post is marked paid threw away what had already been sent, and left a
  // lock icon where there was something to read.
  Future<void> _showArticle(SubstackPost post, String html) async {
    final generation = _content.generation;
    final wrap = _pageWrapper(post);
    final native = PrefService.of(context, listen: false).get<bool>(optionPluginSubstackNativeReader) == true;
    // One parse gives the page, the navigation index and the speech text; a
    // long body is parsed off the UI isolate.
    final key = '${substackPostKey(post)}\n${post.postDate}';
    final parsed =
        substackParsedArticles.get(key, html) ??
        await parseInBackground<SubstackArticleDocument>(SubstackArticleDocument.parse, html);
    substackParsedArticles.put(key, html, parsed);
    if (!mounted || !_content.accepts(generation)) return;
    _content.change(
      paywalled: false,
      empty: false,
      liveSite: false,
      speakText: _fallbackSpeakText(post, bodyPlain: parsed.plainText),
      partial: post.isPaywalled,
      document: parsed,
    );
    _reading.allowAutomaticCompletion = !post.isPaywalled;
    if (native) return _showNative(parsed.body, generation);
    _content.change(blocks: null);
    _stopSpinnerWhenLoaded();

    // Article HTML is sanitized; JS stays on so a long-press can start
    // Vorlesen from that paragraph.
    await _controller.setJavaScriptMode(JavaScriptMode.unrestricted);
    final document = await OfflineStore.shared.renderArticle(_offlineId, wrap(parsed));
    if (!mounted || !_content.accepts(generation)) return;
    await _controller.loadHtmlString(document, baseUrl: post.canonicalUrl);
  }

  /// The native reader: the parsed body as blocks, no web view.
  Future<void> _showNative(String body, int generation) async {
    final blocks = await parseInBackground<List<SubstackBlock>>(substackBlocksOfBody, body);
    final kept = await OfflineStore.shared.articleImages(_offlineId);
    if (!mounted || !_content.accepts(generation)) return;
    // A refresh keeps the passage being read; a first open goes back to the saved place.
    final point = (_content.state.blocks == null ? null : _place.place) ?? _reading.state.point;
    _content.change(blocks: blocks, keptImages: kept, loading: false);
    _reading.setActive(true);
    await _place.open(point);
  }

  /// A link tapped in the native reader goes where a web view link would.
  void _openNativeLink(String url) {
    final link = substackLinkFor(context, url);
    if (link != null && (link.slug != _post.slug || link.publicationBase.origin != _post.publicationBaseUrl)) {
      unawaited(
        _openReaderRoute(SubstackReaderScreen(post: substackPostStub(link, publicationName: _post.publicationName))),
      );
      return;
    }
    unawaited(openLink(context, url));
  }

  /// The page around a sanitized body. Theme, text scale and strings are read
  /// now: `context` is not ours to touch across the parse's async gap.
  String Function(SubstackArticleDocument parsed) _pageWrapper(SubstackPost post) {
    final scheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isRtl = Directionality.of(context) == TextDirection.rtl;
    final fontSizePx = MediaQuery.textScalerOf(context).scale(18) * _reading.state.fontSize / 18;
    final lineHeight = _reading.state.lineHeight;
    final l10n = L10n.of(context);
    return (parsed) => wrapSubstackHtml(
      title: post.title,
      body: parsed.body,
      bodySanitized: true,
      subtitle: post.excerpt,
      authorName: post.authorName,
      publicationName: post.publicationName,
      readTime: switch (substackReadMinutes(post, plainText: parsed.plainText)) {
        final minutes? => l10n.plugin_substack_read_minutes(minutes),
        null => null,
      },
      background: _cssColor(scheme.surface),
      foreground: _cssColor(scheme.onSurface),
      muted: _cssColor(scheme.onSurfaceVariant),
      link: _cssColor(scheme.primary),
      isDark: isDark,
      isRtl: isRtl,
      fontSizePx: fontSizePx,
      lineHeight: lineHeight,
      // Says where the free part stops, so the end of the preview does not
      // read as the end of the article.
      footer: post.isPaywalled ? l10n.plugin_substack_preview_ends : null,
      footerLink: post.isPaywalled ? post.canonicalUrl : null,
      footerLinkLabel: post.isPaywalled ? l10n.plugin_substack_continue_on_site : null,
      embedLabel: l10n.plugin_substack_load_embed,
      webOnlyLabel: l10n.plugin_substack_web_only_block,
      openLabel: l10n.open_in_browser,
      postUrl: post.canonicalUrl,
    );
  }

  /// True when what is being read aloud is this article, rather than one the
  /// reader started earlier and left playing.
  bool _isReadingThis(SpeechPlayback playback) => playback.speaking && playback.title == _post.title;

  Future<void> _toggleTts(SpeechStore speech) async {
    if (_isReadingThis(speech.state)) {
      await speech.stop();
      return;
    }

    if (await _speakFromSelection()) return;
    if (!mounted) return;
    final choice = readTtsChoice(PrefService.of(context, listen: false));
    var text = _speakText?.trim() ?? '';
    if (text.length < 40 && _liveSite) {
      await _extractLiveSpeakText();
      text = _speakText?.trim() ?? '';
    }

    if (text.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).plugin_substack_tts_no_text)));
      return;
    }

    final spoke = await speech.speak(title: _post.title, text: text, choice: choice);
    if (!spoke && mounted) {
      await _onTtsFailed(speech, choice);
    }
  }

  /// Selected text in the article means "read from here": the page posts the
  /// rest of the article from that paragraph through the speech channel.
  Future<bool> _speakFromSelection() async {
    if (_liveSite || _content.state.document == null) return false;
    try {
      final started = await _controller.runJavaScriptReturningResult(
        'window.xtaSpeakFromSelection ? window.xtaSpeakFromSelection() : false',
      );
      return '$started' == 'true';
    } catch (_) {
      return false;
    }
  }

  Future<void> _speakFromHere(String needle) async {
    if (!mounted) return;
    final speech = context.read<SpeechStore>();
    final from = textFromHere(_speakText ?? '', needle);
    if (from.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).plugin_substack_tts_no_text)));
      return;
    }
    final choice = readTtsChoice(PrefService.of(context, listen: false));
    final spoke = await speech.speak(title: _post.title, text: from, choice: choice);
    if (!spoke && mounted) {
      await _onTtsFailed(speech, choice);
    }
  }

  Future<void> _onTtsFailed(SpeechStore speech, TtsChoice choice) async {
    final l10n = L10n.of(context);
    final action = ttsFailureActionFor(choice.engine);
    final install = action == TtsFailureAction.installSherpa;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(install ? l10n.tts_sherpa_missing : l10n.plugin_substack_tts_unavailable),
        action: SnackBarAction(
          label: install ? l10n.tts_sherpa_install : l10n.tts_use_sherpa,
          onPressed: () => _onTtsFailureAction(speech, install),
        ),
      ),
    );
  }

  Future<void> _onTtsFailureAction(SpeechStore speech, bool install) async {
    if (install) {
      if (!mounted) return;
      await openUri(context, sherpaTtsInstallUrl);
      return;
    }
    await preferSherpaTts(PrefService.of(context, listen: false));
    if (mounted) await _toggleTts(speech);
  }

  void _share() {
    final url = _post.canonicalUrl;
    if (url == null || url.isEmpty) return;
    SharePlus.instance.share(ShareParams(text: '${_post.title}\n$url'));
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<SubstackReaderStore, SubstackReaderState>(
    store: _content,
    onState: (context, _) => _buildReader(context),
  );

  Widget _buildReader(BuildContext context) {
    final speech = context.read<SpeechStore>();
    final canSpeak = (_speakText?.trim().isNotEmpty ?? false) && !_empty;

    return Scaffold(
      appBar: AppBar(
        title: Text(_post.title, maxLines: 1, overflow: TextOverflow.ellipsis),
        actions: [
          if (_post.bodyHtml?.trim().isNotEmpty == true) OfflineArticleAction(article: OfflineArticle.substack(_post)),
          ScopedBuilder<SubstackSavedStore, List<SubstackPost>>(
            store: context.read<SubstackSavedStore>(),
            onState: (context, saved) {
              final isSaved = saved.any((p) => sameSubstackPost(p, _post));
              return IconButton(
                tooltip: isSaved ? L10n.of(context).plugin_substack_unsave : L10n.of(context).plugin_substack_save,
                icon: Icon(isSaved ? Icons.bookmark : Icons.bookmark_outline),
                onPressed: () => context.read<SubstackSavedStore>().toggle(_post),
              );
            },
          ),
          if (canSpeak)
            ScopedBuilder<SpeechStore, SpeechPlayback>(
              store: speech,
              onState: (context, playback) {
                final reading = _isReadingThis(playback);
                return IconButton(
                  tooltip: reading
                      ? L10n.of(context).plugin_substack_tts_stop
                      : L10n.of(context).plugin_substack_tts_listen,
                  icon: Icon(reading ? Icons.stop_circle_outlined : Icons.record_voice_over_outlined),
                  onPressed: () => _toggleTts(speech),
                );
              },
            ),
          PopupMenuButton<VoidCallback>(
            tooltip: MaterialLocalizations.of(context).showMenuTooltip,
            onSelected: (action) => action(),
            itemBuilder: (context) {
              final l10n = L10n.of(context);
              final liked = context.read<SubstackLikesStore>().contains(_post);
              return [
                if (!_liveSite && _content.state.document != null) ...[
                  _menu(Icons.format_list_bulleted, l10n.article_reader_contents, () => _openArticleNavigation()),
                  _menu(Icons.manage_search, l10n.article_reader_find, () => _openArticleNavigation(find: true)),
                ],
                if (!_loading && !_content.state.refreshing)
                  _menu(Icons.refresh, l10n.article_reader_refresh, () => _load(force: true)),
                _menu(
                  Icons.mode_comment_outlined,
                  l10n.plugin_substack_comments,
                  () => _openReaderRoute(SubstackCommentsScreen(post: _post)),
                ),
                _menu(
                  Icons.newspaper_outlined,
                  l10n.plugin_substack_publication,
                  () => _openReaderRoute(SubstackArchiveScreen(publication: _post.publication)),
                ),
                _menu(
                  liked ? Icons.favorite : Icons.favorite_outline,
                  liked ? l10n.plugin_substack_unlike : l10n.plugin_substack_like,
                  () => context.read<SubstackLikesStore>().toggle(_post),
                ),
                if (canSpeak)
                  _menu(Icons.tune, l10n.plugin_substack_tts_settings, () async {
                    if (await openTtsSettings(context, speech.tts)) await speech.stop();
                  }),
                if (_post.canonicalUrl != null) ...[
                  _menu(Icons.share_outlined, l10n.share_link, _share),
                  _menu(Icons.open_in_new, l10n.open_in_browser, () => openUri(context, _post.canonicalUrl!)),
                ],
              ];
            },
          ),
        ],
      ),
      body: Column(
        children: [
          if (_content.state.refreshing) const LinearProgressIndicator(),
          if (_content.state.refreshError != null)
            Padding(
              padding: const EdgeInsetsDirectional.fromSTEB(16, 4, 8, 4),
              child: Row(
                children: [
                  Expanded(child: Text(L10n.of(context).article_reader_refresh_failed)),
                  TextButton(onPressed: () => _load(force: true), child: Text(L10n.of(context).retry)),
                ],
              ),
            ),
          if (_partial)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Text(
                L10n.of(context).plugin_substack_preview_badge,
                style: Theme.of(context).textTheme.labelSmall,
              ),
            ),
          ArticleReaderControls(
            store: _reading,
            supportsAppearance: !_liveSite && !_empty && !_paywalled,
            canComplete: !_loading,
            onAppearanceChanged: _content.state.blocks == null ? _applyReadingAppearance : _place.keepPlace,
            onStartOver: () {
              if (_content.state.blocks != null) return _place.startOver();
              if (!_liveSite) _controller.runJavaScript('window.xtaArticle?.startOver();');
            },
          ),
          Expanded(child: _articleBody(context)),
        ],
      ),
    );
  }

  PopupMenuItem<VoidCallback> _menu(IconData icon, String title, VoidCallback action) => PopupMenuItem(
    value: action,
    child: Row(
      children: [
        Icon(icon, size: 20),
        const SizedBox(width: 12),
        Flexible(child: Text(title)),
      ],
    ),
  );

  Future<void> _openArticleNavigation({bool find = false}) async {
    final document = _content.state.document;
    if (document == null || _liveSite) return;
    _reading.setActive(false);
    final anchor = await showSubstackArticleNavigation(context, document, find: find);
    if (!mounted || _liveSite) return;
    if (anchor != null && identical(document, _content.state.document)) await _jumpTo(document, anchor);
    if (mounted && !_loading) _reading.setActive(true);
  }

  Future<void> _jumpTo(SubstackArticleDocument document, String anchor) async {
    final blocks = _content.state.blocks;
    if (blocks == null) return _controller.runJavaScript(substackArticleJumpJs(anchor));
    final passages = [for (final passage in document.passages) passage.anchor];
    final index = substackBlockOfPassage(blocks, passages, anchor);
    if (index != null) await _place.showBlock(index, duration: xtaMotionDuration(context, kXtaMotionNavigation));
  }

  Future<void> _openReaderRoute(Widget screen) async {
    _reading.setActive(false);
    await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => screen));
    if (mounted && !_loading && !_liveSite) _reading.setActive(true);
  }

  Widget _articleBody(BuildContext context) => _error != null
      ? SubstackErrorView(
          error: _error,
          onRetry: () {
            _content.change(error: null);
            _content.change(empty: false);
            _content.change(paywalled: false);
            _content.change(loading: true);

            _load();
          },
        )
      : _paywalled
      ? _PaywallPane(
          post: _post,
          onOpenWeb: _post.canonicalUrl == null ? null : () => openUri(context, _post.canonicalUrl!),
        )
      : _empty
      ? Center(child: Text(L10n.of(context).plugin_substack_no_content))
      : Column(
          children: [
            // A podcast post's episode, above its show notes.
            if (_post.isPodcast) SubstackAudioPlayer(url: _post.audioUrl!, title: _post.title),
            Expanded(
              child: Stack(
                children: [
                  if (_content.state.blocks case final blocks?)
                    ScopedBuilder<ArticleReadingStore, ArticleReadingState>(
                      store: _reading,
                      onState: (_, reading) => SubstackNativeArticle(
                        post: _post,
                        blocks: blocks,
                        place: _place,
                        readMinutes: substackReadMinutes(_post, plainText: _content.state.document?.plainText),
                        onLink: _openNativeLink,
                        keptImages: _content.state.keptImages,
                        fontSize: MediaQuery.textScalerOf(context).scale(18) * reading.fontSize / 18,
                        lineHeight: reading.lineHeight,
                      ),
                    )
                  else
                    WebViewWidget(controller: _controller),
                  if (_loading) const Center(child: CircularProgressIndicator()),
                ],
              ),
            ),
          ],
        );

  Future<void> _applyReadingAppearance() async {
    if (!mounted || _liveSite || _empty || _paywalled) return;
    final textScale = MediaQuery.textScalerOf(context).scale(18) / 18;
    try {
      await _controller.runJavaScript(articleReadingBridge(_reading.state, textScale: textScale));
      _reading.setActive(true);
    } catch (_) {
      // The article remains readable without platform progress callbacks.
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _reading.setActive(state == AppLifecycleState.resumed && !_loading && !_liveSite);
  }

  @override
  void deactivate() {
    _place.deactivate();
    super.deactivate();
  }

  @override
  void activate() {
    super.activate();
    _place.activate();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _place.close(then: _reading.destroy);
    unawaited(_content.destroy());
    super.dispose();
  }
}

class _PaywallPane extends StatelessWidget {
  final SubstackPost post;
  final VoidCallback? onOpenWeb;

  const _PaywallPane({required this.post, this.onOpenWeb});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Icon(Icons.lock_outline, size: 48, color: theme.colorScheme.outline),
        const SizedBox(height: 16),
        Text(
          L10n.of(context).plugin_substack_paywalled_title,
          style: theme.textTheme.titleLarge,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(L10n.of(context).plugin_substack_paywalled_description, textAlign: TextAlign.center),
        if (post.excerpt != null) ...[
          const SizedBox(height: 24),
          Text(post.excerpt!, style: theme.textTheme.bodyLarge, textAlign: TextAlign.center),
        ],
        if (onOpenWeb != null) ...[
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: onOpenWeb,
            icon: const Icon(Icons.open_in_new),
            label: Text(L10n.of(context).open_in_browser),
          ),
        ],
      ],
    );
  }
}

String _cssColor(Color color) {
  final hex = color.toARGB32().toRadixString(16).padLeft(8, '0');
  return '#${hex.substring(2)}';
}
