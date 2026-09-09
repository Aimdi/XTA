import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_post_actions.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/utils/json.dart';
import 'package:xta/utils/urls.dart';

/// A small local reading snapshot for sources without a native archive model.
class PluginLinkPost {
  final String source;
  final String url;
  final String author;
  final String text;
  final List<String> images;
  const PluginLinkPost({
    required this.source,
    required this.url,
    required this.author,
    required this.text,
    this.images = const [],
  });

  PluginPostArchive get archive => PluginPostArchive(
    id: '$source:$url',
    userId: author,
    content: {'xtaPlugin': 'link', 'source': source, 'url': url, 'author': author, 'text': text, 'images': images},
  );

  String get haystack => '$source\n$author\n$text\n$url'.toLowerCase();

  static PluginLinkPost? fromArchive(Object? data) {
    final json = Json(data);
    final url = json['url'].string ?? '';
    final uri = Uri.tryParse(url);
    if (json['xtaPlugin'].string != 'link' ||
        uri == null ||
        !uri.hasAuthority ||
        (uri.scheme != 'https' && uri.scheme != 'http'))
      return null;
    return PluginLinkPost(
      source: json['source'].string ?? '',
      url: url,
      author: json['author'].string ?? '',
      text: json['text'].string ?? '',
      images: [
        for (final image in json['images'].list)
          if (image.string case final value?) value,
      ],
    );
  }
}

Future<void> showPluginLinkPostActions(
  BuildContext context, {
  required String source,
  required String url,
  required String author,
  required String text,
  List<String> images = const [],
}) => showPluginPostActions(
  context,
  post: PluginLinkPost(source: source, url: url, author: author, text: text, images: images).archive,
  url: url,
);

class PluginLinkPostCard extends StatelessWidget {
  final PluginLinkPost post;
  const PluginLinkPostCard({super.key, required this.post});
  @override
  Widget build(BuildContext context) {
    final title = pluginById(post.source)?.title(context) ?? post.source;
    return InkWell(
      onTap: () => openUri(context, post.url),
      onLongPress: () => showPluginPostActions(context, post: post.archive, url: post.url),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(title, style: Theme.of(context).textTheme.labelMedium),
            if (post.author.isNotEmpty) Text(post.author, style: Theme.of(context).textTheme.titleSmall),
            if (post.text.isNotEmpty) Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: Text(post.text)),
            if (post.images.isNotEmpty)
              PluginPostMedia(items: [for (final url in post.images) PluginMediaItem(url: url)]),
            Align(
              alignment: AlignmentDirectional.centerEnd,
              child: TextButton.icon(
                onPressed: () => openUri(context, post.url),
                icon: const Icon(Icons.open_in_new),
                label: Text(L10n.of(context).open_in_browser),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
