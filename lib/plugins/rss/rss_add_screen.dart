import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_group.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/ui/errors.dart';

class RssAddScreen extends StatefulWidget {
  const RssAddScreen({super.key});

  @override
  State<RssAddScreen> createState() => _RssAddScreenState();
}

class _RssAddScreenState extends State<RssAddScreen> {
  final _controller = TextEditingController();
  late final RssAddFeedStore _addStore;
  RssFeed? _followed;

  @override
  void initState() {
    super.initState();
    _addStore = RssAddFeedStore(context.read<RssClient>());
  }

  @override
  void dispose() {
    _controller.dispose();
    _addStore.destroy();
    super.dispose();
  }

  Future<void> _submit() async {
    final feeds = context.read<RssFeedsStore>();
    try {
      final feed = await _addStore.lookup(_controller.text);
      await feeds.add(feed);
      if (!mounted) return;
      setState(() => _followed = feed);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_rss_add)),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.plugin_rss_add_hint),
            const SizedBox(height: 12),
            TextField(
              controller: _controller,
              textInputAction: TextInputAction.done,
              keyboardType: TextInputType.url,
              autocorrect: false,
              decoration: InputDecoration(
                border: const OutlineInputBorder(),
                hintText: l10n.plugin_rss_add_placeholder,
              ),
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _submit,
              child: Text(l10n.plugin_rss_follow),
            ),
            const SizedBox(height: 16),
            Expanded(
              child: ScopedBuilder<RssAddFeedStore, RssFeed?>(
                store: _addStore,
                onError: (_, error) => FullPageErrorWidget(
                  error: error,
                  stackTrace: null,
                  prefix: l10n.plugin_rss_add_error,
                  onRetry: _submit,
                ),
                onLoading: (_) =>
                    const Center(child: CircularProgressIndicator()),
                onState: (_, feed) {
                  final shown = _followed ?? feed;
                  if (shown == null) return const SizedBox.shrink();
                  return ListView(
                    children: [
                      ListTile(
                        leading: shown.iconUrl == null
                            ? const CircleAvatar(child: Icon(Icons.rss_feed))
                            : ClipOval(
                                child: ExtendedImage.network(
                                  shown.iconUrl!,
                                  width: 40,
                                  height: 40,
                                  fit: BoxFit.cover,
                                ),
                              ),
                        title: Text(shown.name),
                        subtitle: Text(
                          Uri.tryParse(shown.siteUrl ?? shown.feedUrl)?.host ??
                              shown.feedUrl,
                        ),
                      ),
                      if (shown.description != null)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                          child: Text(shown.description!),
                        ),
                      TextButton.icon(
                        onPressed: () => addRssFeedToGroup(context, shown),
                        icon: const Icon(Icons.group_add_outlined),
                        label: Text(l10n.plugin_rss_add_to_group),
                      ),
                    ],
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
