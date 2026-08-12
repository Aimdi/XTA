import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/video_quality.dart';
import 'package:xta/utils/urls.dart';
import 'package:provider/provider.dart';

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
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(
          '@${post.author.uniqueId}',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          IconButton(
            tooltip: _embed
                ? l10n.plugin_tiktok_try_native
                : l10n.plugin_tiktok_use_embed,
            icon: Icon(_embed ? Icons.videocam_outlined : Icons.public),
            onPressed: () => setState(() => _embed = !_embed),
          ),
          IconButton(
            tooltip: l10n.plugin_tiktok_open_on_site,
            icon: const Icon(Icons.open_in_new),
            onPressed: () => openUri(context, post.webUri().toString()),
          ),
        ],
      ),
      body: Center(
        child: AspectRatio(
          aspectRatio: post.aspectRatio.clamp(9 / 16, 16 / 9),
          child: _embed || post.playUrl == null
              ? TikTokEmbedView(videoId: post.id)
              : TweetVideo(
                  username: post.author.uniqueId,
                  loop: true,
                  alwaysPlay: true,
                  tweetId: 'tiktok-player-${post.id}',
                  metadata: TweetVideoMetadata(
                    post.aspectRatio,
                    post.coverUrl,
                    () async {
                      final headers = context
                          .read<TikTokClient>()
                          .playbackHeaders;
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
                ),
        ),
      ),
    );
  }
}

class TikTokEmbedView extends StatefulWidget {
  final String videoId;

  const TikTokEmbedView({super.key, required this.videoId});

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
      )
      ..loadRequest(
        Uri.parse('https://www.tiktok.com/embed/v3/${widget.videoId}'),
      );
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
