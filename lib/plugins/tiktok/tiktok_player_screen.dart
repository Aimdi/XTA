import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/plugins/tiktok/tiktok_post_card.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/video_quality.dart';
import 'package:xta/utils/urls.dart';

/// Full-screen TikTok watch: native CDN when headers work, else the official embed.
class TikTokPlayerScreen extends StatefulWidget {
  final TikTokPost post;

  const TikTokPlayerScreen({super.key, required this.post});

  @override
  State<TikTokPlayerScreen> createState() => _TikTokPlayerScreenState();
}

class _TikTokPlayerScreenState extends State<TikTokPlayerScreen> {
  var _embed = false;

  @override
  void initState() {
    super.initState();
    _embed =
        PrefService.of(
          context,
          listen: false,
        ).get<bool>(optionPluginTiktokPreferEmbed) ==
        true;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final post = widget.post;

    return Scaffold(
      backgroundColor: Colors.black,
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        elevation: 0,
        title: Text(
          '@${post.author.uniqueId}',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert),
            onSelected: (value) {
              if (value == 'toggle') {
                setState(() => _embed = !_embed);
              } else if (value == 'open') {
                openUri(context, post.webUri().toString());
              }
            },
            itemBuilder: (context) => [
              PopupMenuItem(
                value: 'toggle',
                child: Text(
                  _embed
                      ? l10n.plugin_tiktok_try_native
                      : l10n.plugin_tiktok_use_embed,
                ),
              ),
              PopupMenuItem(
                value: 'open',
                child: Text(l10n.plugin_tiktok_open_on_site),
              ),
            ],
          ),
        ],
      ),
      body: Stack(
        fit: StackFit.expand,
        children: [
          ColoredBox(
            color: Colors.black,
            child: Center(child: _player(post)),
          ),
          const IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.center,
                  colors: [Color(0x99000000), Color(0x00000000)],
                ),
              ),
            ),
          ),
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: _CaptionOverlay(post: post),
          ),
        ],
      ),
    );
  }

  Widget _player(TikTokPost post) {
    final child = _embed || post.playUrl == null
        ? TikTokEmbedView(
            videoId: post.id,
            cookies: context.read<TikTokClient>().cookies,
          )
        : TweetVideo(
            username: post.author.uniqueId,
            loop: true,
            alwaysPlay: true,
            tweetId: 'tiktok-player-${post.id}',
            metadata: TweetVideoMetadata(
              post.aspectRatio,
              post.coverUrl,
              () async {
                final headers = context.read<TikTokClient>().playbackHeaders;
                final qualities = [
                  for (final source in post.sources)
                    if (source.label != 'download')
                      TweetVideoQuality(source.url, source.label ?? '—'),
                ];
                return TweetVideoUrls(
                  post.playUrl!,
                  null,
                  qualities: qualities,
                  httpHeaders: headers,
                );
              },
            ),
            onPlaybackError: () {
              if (mounted) setState(() => _embed = true);
            },
          );
    if (_embed || post.playUrl == null) {
      return AspectRatio(
        aspectRatio: post.aspectRatio.clamp(9 / 16, 16 / 9),
        child: child,
      );
    }
    return child;
  }
}

class _CaptionOverlay extends StatelessWidget {
  final TikTokPost post;

  const _CaptionOverlay({required this.post});

  @override
  Widget build(BuildContext context) {
    final desc = post.desc.trim();
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0x00000000), Color(0xCC000000)],
        ),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 32, 16, 16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              TikTokAvatar(
                url: post.author.avatarUrl,
                seed: post.author.uniqueId,
                name: post.author.displayName,
                size: 40,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      post.author.displayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 16,
                      ),
                    ),
                    Text(
                      '@${post.author.uniqueId}',
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 13,
                      ),
                    ),
                    if (desc.isNotEmpty) ...[
                      const SizedBox(height: 6),
                      Text(
                        desc,
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 14,
                          height: 1.3,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class TikTokEmbedView extends StatefulWidget {
  final String videoId;
  final Map<String, String> cookies;

  const TikTokEmbedView({
    super.key,
    required this.videoId,
    required this.cookies,
  });

  @override
  State<TikTokEmbedView> createState() => _TikTokEmbedViewState();
}

class _TikTokEmbedViewState extends State<TikTokEmbedView> {
  late final WebViewController _controller;
  var _loading = true;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.black)
      ..setUserAgent(tiktokUserAgent)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (_) {
            if (mounted) setState(() => _loading = false);
          },
          onWebResourceError: (_) {
            if (mounted) setState(() => _loading = false);
          },
        ),
      );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _load();
    });
  }

  Future<void> _load() async {
    final platform = _controller.platform;
    if (platform is AndroidWebViewController) {
      await platform.setMediaPlaybackRequiresUserGesture(false);
      if (!mounted) return;
    }

    final manager = WebViewCookieManager();
    for (final entry in widget.cookies.entries) {
      await manager.setCookie(
        WebViewCookie(
          name: entry.key,
          value: entry.value,
          domain: '.tiktok.com',
          path: '/',
        ),
      );
      if (!mounted) return;
    }

    await _controller.loadRequest(
      Uri.parse('https://www.tiktok.com/embed/v3/${widget.videoId}'),
    );
    if (!mounted) return;
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        WebViewWidget(controller: _controller),
        if (_loading) const LinearProgressIndicator(minHeight: 2),
      ],
    );
  }
}
