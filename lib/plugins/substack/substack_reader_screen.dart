import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/substack/substack_article_cache.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_audio_player.dart';
import 'package:xta/plugins/substack/substack_comments_screen.dart';
import 'package:xta/plugins/substack/substack_links.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/speech/speech_store.dart';
import 'package:xta/speech/tts_settings.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';
import 'package:share_plus/share_plus.dart';
import 'package:webview_flutter/webview_flutter.dart';

class SubstackReaderScreen extends StatefulWidget {
  final SubstackPost post;

  const SubstackReaderScreen({super.key, required this.post});

  @override
  State<SubstackReaderScreen> createState() => _SubstackReaderScreenState();
}

class _SubstackReaderScreenState extends State<SubstackReaderScreen> {
  late final WebViewController _controller;
  late SubstackPost _post;
  final _articleCache = SubstackArticleCache();
  Object? _error;
  var _loading = true;
  var _empty = false;
  var _paywalled = false;

  /// True when what is on screen is the free opening of a paid post rather than
  /// the whole thing.
  var _partial = false;
  String? _speakText;

  /// True while the web view is showing the live publication page (not our HTML).
  var _liveSite = false;

  @override
  void initState() {
    super.initState();
    _post = widget.post;
    // Seed speech from whatever we already know — title and excerpt — so Listen
    // is available before the body finishes loading.
    _speakText = _fallbackSpeakText(_post);
    // Off until something needs it. The article this screen usually shows is a
    // page we build ourselves out of prose, and prose does not need scripting;
    // the live site fallback below turns it back on because a real website
    // does.
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.disabled);
    _stopSpinnerWhenLoaded();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SubstackReadStore>().markRead(_post.id);
      _load();
    });
  }

  String _fallbackSpeakText(
    SubstackPost post, {
    String? bodyHtml,
    String? bodyPlain,
  }) {
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
    final url = request.url;
    if (url.startsWith('about:blank') || url.startsWith('data:')) {
      return NavigationDecision.navigate;
    }
    // Same article (canonical or relative) may reload; let the webview keep it.
    final canonical = _post.canonicalUrl;
    if (canonical != null &&
        url.split('#').first == canonical.split('#').first) {
      return NavigationDecision.navigate;
    }
    final link = substackLinkFor(context, url);
    if (link != null && link.slug != _post.slug) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => SubstackReaderScreen(
            post: substackPostStub(
              link,
              publicationName: _post.publicationName,
            ),
          ),
        ),
      );
      return NavigationDecision.prevent;
    }
    return NavigationDecision.navigate;
  }

  Future<void> _load() async {
    final client = context.read<SubstackClient>();
    try {
      final cached = await _articleCache.get(_post.publication, _post.slug);
      if (cached != null &&
          (cached.bodyHtml?.trim().isNotEmpty ?? false) &&
          mounted) {
        _post = cached;
        _error = null;
        await _showContent(cached);
      }

      final full = await client.fetchPost(_post.publication, _post.slug);
      if (!mounted) return;
      _post = full;
      _error = null;
      await _articleCache.put(full);
      await _showContent(full);
    } catch (e) {
      if (!mounted) return;
      if (_post.bodyHtml?.trim().isNotEmpty == true) {
        // Offline / stale body already on screen from cache.
        return;
      }
      if (_post.canonicalUrl != null) {
        _error = null;
        _paywalled = false;
        _partial = false;
        _empty = false;
        _speakText = _fallbackSpeakText(_post);
        await _loadLiveSite(_post.canonicalUrl!);
      } else {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  /// Every load installs the same delegate: the spinner goes when the page
  /// arrives, and a tapped link goes to the right place — another article opens
  /// in this reader, anything else leaves for the browser — instead of
  /// navigating this screen away from the post it is showing.
  void _stopSpinnerWhenLoaded({bool extractLiveText = false}) {
    _controller.setNavigationDelegate(
      NavigationDelegate(
        onNavigationRequest: _onNavigation,
        onPageFinished: (_) async {
          if (!mounted) return;
          setState(() => _loading = false);
          if (extractLiveText) {
            await _extractLiveSpeakText();
          }
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
      setState(() {
        _speakText = _fallbackSpeakText(_post, bodyPlain: plain);
      });
    } catch (_) {
      // Title/excerpt fallback already set; leave Listen working with that.
    }
  }

  /// `runJavaScriptReturningResult` may wrap strings in JSON quotes.
  String? _jsStringResult(Object? raw) {
    if (raw == null) return null;
    if (raw is! String) return raw.toString();
    final trimmed = raw.trim();
    if (trimmed.length >= 2 &&
        trimmed.startsWith('"') &&
        trimmed.endsWith('"')) {
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
    _liveSite = true;
    if (_speakText == null || _speakText!.trim().isEmpty) {
      _speakText = _fallbackSpeakText(_post);
    }
    _stopSpinnerWhenLoaded(extractLiveText: true);
    await _controller.setJavaScriptMode(JavaScriptMode.unrestricted);
    await _controller.loadRequest(Uri.parse(url));
  }

  Future<void> _showContent(SubstackPost post) async {
    final html = post.bodyHtml;
    final hasBody = html != null && html.trim().isNotEmpty;

    // A paid post usually arrives with its opening paragraphs — the part the
    // publication chose to give away. Refusing to render any of it because the
    // post is marked paid threw away what had already been sent, and left a
    // lock icon where there was something to read.
    if (post.isPaywalled && !hasBody) {
      setState(() {
        _paywalled = true;
        _partial = false;
        _empty = false;
        _loading = false;
        _liveSite = false;
        _speakText = _fallbackSpeakText(post);
      });
      return;
    }

    if (hasBody) {
      _paywalled = false;
      _empty = false;
      _liveSite = false;
      _speakText = _fallbackSpeakText(post, bodyHtml: html);
      _partial = post.isPaywalled;
      _stopSpinnerWhenLoaded();

      // Built before the first await: everything below it reads the theme and
      // the strings, and `context` is not ours to touch across an async gap.
      final scheme = Theme.of(context).colorScheme;
      final isDark = Theme.of(context).brightness == Brightness.dark;
      final page = wrapSubstackHtml(
        title: post.title,
        body: html,
        subtitle: post.excerpt,
        authorName: post.authorName,
        publicationName: post.publicationName,
        background: _cssColor(scheme.surface),
        foreground: _cssColor(scheme.onSurface),
        muted: _cssColor(scheme.onSurfaceVariant),
        link: _cssColor(scheme.primary),
        isDark: isDark,
        // Says where the free part stops, so the end of the preview does not
        // read as the end of the article.
        footer: post.isPaywalled
            ? L10n.of(context).plugin_substack_preview_ends
            : null,
        footerLink: post.isPaywalled ? post.canonicalUrl : null,
        footerLinkLabel: post.isPaywalled
            ? L10n.of(context).plugin_substack_continue_on_site
            : null,
      );

      // Explicit rather than assumed: a retry after the live-site fallback
      // would otherwise render the article with scripting still on.
      await _controller.setJavaScriptMode(JavaScriptMode.disabled);
      await _controller.loadHtmlString(page);
      if (mounted) setState(() {});
      return;
    }

    if (post.canonicalUrl != null) {
      _paywalled = false;
      _partial = false;
      _empty = false;
      _speakText = _fallbackSpeakText(post);
      await _loadLiveSite(post.canonicalUrl!);
      return;
    }

    if (mounted) {
      setState(() {
        _loading = false;
        _empty = true;
        _paywalled = false;
        _partial = false;
        _liveSite = false;
        _speakText = _fallbackSpeakText(post);
      });
    }
  }

  /// True when what is being read aloud is this article, rather than one the
  /// reader started earlier and left playing.
  bool _isReadingThis(SpeechPlayback playback) =>
      playback.speaking && playback.title == _post.title;

  Future<void> _toggleTts(SpeechStore speech) async {
    if (_isReadingThis(speech.state)) {
      await speech.stop();
      return;
    }

    final choice = readTtsChoice(PrefService.of(context, listen: false));
    var text = _speakText?.trim() ?? '';
    if (text.length < 40 && _liveSite) {
      await _extractLiveSpeakText();
      text = _speakText?.trim() ?? '';
    }

    if (text.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(L10n.of(context).plugin_substack_tts_no_text)),
      );
      return;
    }

    final spoke = await speech.speak(
      title: _post.title,
      text: text,
      choice: choice,
    );
    if (!spoke && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(L10n.of(context).plugin_substack_tts_unavailable),
          action: SnackBarAction(
            label: L10n.of(context).plugin_substack_tts_settings,
            onPressed: () => openTtsSettings(context, speech.tts),
          ),
        ),
      );
    }
  }

  void _share() {
    final url = _post.canonicalUrl;
    if (url == null || url.isEmpty) return;
    SharePlus.instance.share(ShareParams(text: '${_post.title}\n$url'));
  }

  @override
  Widget build(BuildContext context) {
    final speech = context.read<SpeechStore>();
    final canSpeak = (_speakText?.trim().isNotEmpty ?? false) && !_empty;

    return Scaffold(
      appBar: AppBar(
        title: Text(_post.title, maxLines: 1, overflow: TextOverflow.ellipsis),
        actions: [
          // Says up front that this is the opening of a paid post, so the
          // reader is not surprised when it stops.
          if (_partial)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 14),
              child: Chip(
                label: Text(L10n.of(context).plugin_substack_preview_badge),
                labelStyle: Theme.of(context).textTheme.labelSmall,
                visualDensity: VisualDensity.compact,
                padding: EdgeInsets.zero,
              ),
            ),
          ScopedBuilder<SubstackLikesStore, List<SubstackPost>>(
            store: context.read<SubstackLikesStore>(),
            onState: (context, liked) {
              final isLiked = liked.any((p) => p.id == _post.id);
              return IconButton(
                tooltip: isLiked
                    ? L10n.of(context).plugin_substack_unlike
                    : L10n.of(context).plugin_substack_like,
                icon: Icon(isLiked ? Icons.favorite : Icons.favorite_outline),
                onPressed: () =>
                    context.read<SubstackLikesStore>().toggle(_post),
              );
            },
          ),
          ScopedBuilder<SubstackSavedStore, List<SubstackPost>>(
            store: context.read<SubstackSavedStore>(),
            onState: (context, saved) {
              final isSaved = saved.any((p) => p.id == _post.id);
              return IconButton(
                tooltip: isSaved
                    ? L10n.of(context).plugin_substack_unsave
                    : L10n.of(context).plugin_substack_save,
                icon: Icon(isSaved ? Icons.bookmark : Icons.bookmark_outline),
                onPressed: () =>
                    context.read<SubstackSavedStore>().toggle(_post),
              );
            },
          ),
          IconButton(
            tooltip: L10n.of(context).plugin_substack_comments,
            icon: const Icon(Icons.mode_comment_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => SubstackCommentsScreen(post: _post),
              ),
            ),
          ),
          IconButton(
            tooltip: L10n.of(context).plugin_substack_publication,
            icon: const Icon(Icons.newspaper_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) =>
                    SubstackArchiveScreen(publication: _post.publication),
              ),
            ),
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
                  icon: Icon(
                    reading
                        ? Icons.stop_circle_outlined
                        : Icons.record_voice_over_outlined,
                  ),
                  onPressed: () => _toggleTts(speech),
                );
              },
            ),
          if (canSpeak)
            IconButton(
              tooltip: L10n.of(context).plugin_substack_tts_settings,
              icon: const Icon(Icons.tune),
              onPressed: () async {
                // A new voice cannot be applied to an utterance already in
                // flight, so what is being read stops rather than finishing in
                // the voice that was just replaced.
                if (await openTtsSettings(context, speech.tts)) {
                  await speech.stop();
                }
              },
            ),
          if (_post.canonicalUrl != null) ...[
            IconButton(
              tooltip: L10n.of(context).share_link,
              icon: const Icon(Icons.share_outlined),
              onPressed: _share,
            ),
            IconButton(
              tooltip: L10n.of(context).open_in_browser,
              icon: const Icon(Icons.open_in_new),
              onPressed: () => openUri(context, _post.canonicalUrl!),
            ),
          ],
        ],
      ),
      body: _error != null
          ? FullPageErrorWidget(
              error: _error,
              stackTrace: null,
              prefix: L10n.of(context).plugin_substack_load_error,
              onRetry: () {
                setState(() {
                  _error = null;
                  _empty = false;
                  _paywalled = false;
                  _loading = true;
                });
                _load();
              },
            )
          : _paywalled
          ? _PaywallPane(
              post: _post,
              onOpenWeb: _post.canonicalUrl == null
                  ? null
                  : () => openUri(context, _post.canonicalUrl!),
            )
          : _empty
          ? Center(child: Text(L10n.of(context).plugin_substack_no_content))
          : Column(
              children: [
                // A podcast post's episode, above its show notes.
                if (_post.isPodcast)
                  SubstackAudioPlayer(url: _post.audioUrl!, title: _post.title),
                Expanded(
                  child: Stack(
                    children: [
                      WebViewWidget(controller: _controller),
                      if (_loading)
                        const Center(child: CircularProgressIndicator()),
                    ],
                  ),
                ),
              ],
            ),
    );
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
        Text(
          L10n.of(context).plugin_substack_paywalled_description,
          textAlign: TextAlign.center,
        ),
        if (post.excerpt != null) ...[
          const SizedBox(height: 24),
          Text(
            post.excerpt!,
            style: theme.textTheme.bodyLarge,
            textAlign: TextAlign.center,
          ),
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
