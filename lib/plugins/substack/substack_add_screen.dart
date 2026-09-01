import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_reader_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/ui/errors.dart';

class SubstackAddScreen extends StatefulWidget {
  const SubstackAddScreen({super.key});

  @override
  State<SubstackAddScreen> createState() => _SubstackAddScreenState();
}

class _SubstackAddScreenState extends State<SubstackAddScreen> {
  final _controller = TextEditingController();
  SubstackPost? _pendingPost;
  SubstackPublication? _followed;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final addStore = context.read<SubstackAddPublicationStore>();
    final pubs = context.read<SubstackPublicationsStore>();
    final client = context.read<SubstackClient>();
    final input = _controller.text;

    try {
      final postRef = resolveSubstackPostRef(input);
      SubstackPost? pendingPost;
      if (postRef != null) {
        final tempPub = SubstackPublication(
          subdomain: subdomainOf(postRef.base),
          baseUrl: postRef.base.origin,
          name: subdomainOf(postRef.base),
        );
        try {
          pendingPost = await client.fetchPost(tempPub, postRef.slug);
        } catch (_) {
          pendingPost = null;
        }
      }

      final publication = await addStore.lookup(input);
      await pubs.add(publication);
      if (!mounted) return;

      setState(() {
        _followed = publication;
        _pendingPost = pendingPost;
      });
    } catch (_) {
      // ScopedBuilder shows the error state.
    }
  }

  @override
  Widget build(BuildContext context) {
    final addStore = context.read<SubstackAddPublicationStore>();

    return Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).plugin_substack_add)),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(L10n.of(context).plugin_substack_add_hint),
            const SizedBox(height: 12),
            TextField(
              controller: _controller,
              textInputAction: TextInputAction.done,
              decoration: InputDecoration(
                border: const OutlineInputBorder(),
                hintText: L10n.of(context).plugin_substack_add_placeholder,
              ),
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _submit,
              child: Text(L10n.of(context).plugin_substack_follow),
            ),
            const SizedBox(height: 16),
            Expanded(
              child:
                  ScopedBuilder<
                    SubstackAddPublicationStore,
                    SubstackPublication?
                  >(
                    store: addStore,
                    onError: (_, error) => FullPageErrorWidget(
                      error: error,
                      stackTrace: null,
                      prefix: L10n.of(context).plugin_substack_add_error,
                      onRetry: _submit,
                    ),
                    onLoading: (_) =>
                        const Center(child: CircularProgressIndicator()),
                    onState: (_, pub) {
                      final shown = _followed ?? pub;
                      if (shown == null) return const SizedBox.shrink();
                      return ListView(
                        children: [
                          ListTile(
                            leading: shown.logoUrl == null
                                ? const CircleAvatar(
                                    child: Icon(Icons.newspaper_outlined),
                                  )
                                : ClipOval(
                                    child: ExtendedImage.network(
                                      shown.logoUrl!,
                                      width: 40,
                                      height: 40,
                                      fit: BoxFit.cover,
                                      cacheWidth:
                                          (40 *
                                                  MediaQuery.devicePixelRatioOf(
                                                    context,
                                                  ))
                                              .ceil(),
                                    ),
                                  ),
                            title: Text(shown.name),
                            subtitle: Text(
                              shown.description?.trim().isNotEmpty == true
                                  ? shown.description!
                                  : shown.baseUrl,
                            ),
                          ),
                          const SizedBox(height: 8),
                          FilledButton.tonalIcon(
                            onPressed: () =>
                                addSubstackPublicationToGroup(context, shown),
                            icon: const Icon(Icons.group_add_outlined),
                            label: Text(L10n.of(context).add_to_group),
                          ),
                          if (_pendingPost != null) ...[
                            const Divider(),
                            ListTile(
                              leading: const Icon(Icons.article_outlined),
                              title: Text(_pendingPost!.title),
                              subtitle: Text(
                                L10n.of(
                                  context,
                                ).plugin_substack_open_pasted_post,
                              ),
                              trailing: const Icon(Icons.chevron_right),
                              onTap: () async {
                                final post = _pendingPost!;
                                await Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) =>
                                        SubstackReaderScreen(post: post),
                                  ),
                                );
                                if (context.mounted) {
                                  Navigator.pop(context, true);
                                }
                              },
                            ),
                          ],
                          TextButton(
                            onPressed: () => Navigator.pop(context, true),
                            child: Text(L10n.of(context).plugin_substack_done),
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
