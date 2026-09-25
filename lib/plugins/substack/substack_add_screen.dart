import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/plugins/substack/substack_reader_screen.dart';
import 'package:xta/plugins/substack/substack_search_store.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/subscriptions/users_model.dart';

/// Preview a URL before making an explicit local subscription.
class SubstackAddScreen extends StatefulWidget {
  const SubstackAddScreen({super.key});
  @override
  State<SubstackAddScreen> createState() => _SubstackAddScreenState();
}

class _SubstackAddScreenState extends State<SubstackAddScreen> {
  final _controller = TextEditingController();
  late final SubstackPreviewStore _store;
  @override
  void initState() {
    super.initState();
    _store = SubstackPreviewStore(context.read<SubstackClient>());
  }

  @override
  void dispose() {
    _controller.dispose();
    _store.destroy();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    await _store.lookup(_controller.text);
  }

  Future<void> _follow() async {
    final pubs = context.read<SubstackPublicationsStore>();
    final subscriptions = context.read<SubscriptionsModel>();
    await _store.follow((publication) async {
      await pubs.add(publication);
      if (!pubs.state.any((pub) => pub.id == publication.id)) throw StateError('Follow was not saved');
      await subscriptions.reloadSubscriptions();
    });
  }

  Future<void> _group() async {
    final publication = _store.state.publication;
    if (publication == null) return;
    if (!_store.state.followed) await _follow();
    if (!mounted || !_store.state.followed) return;
    try {
      await addSubstackPublicationToGroup(context, publication);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).plugin_substack_add_error)));
      }
    }
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<SubstackPreviewStore, SubstackPreviewState>(
    store: _store,
    onState: (context, state) {
      final l10n = L10n.of(context);
      final publication = state.publication;
      final post = state.post;
      return PopScope<bool>(
        canPop: false,
        onPopInvokedWithResult: (didPop, result) {
          if (!didPop) Navigator.pop(context, _store.followedAny);
        },
        child: Scaffold(
          appBar: AppBar(
            title: Text(l10n.plugin_substack_add),
            leading: BackButton(onPressed: () => Navigator.pop(context, _store.followedAny)),
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(l10n.plugin_substack_add_hint),
                const SizedBox(height: 12),
                TextField(
                  controller: _controller,
                  textInputAction: TextInputAction.search,
                  enabled: !state.following,
                  decoration: InputDecoration(
                    border: const OutlineInputBorder(),
                    hintText: l10n.plugin_substack_add_placeholder,
                  ),
                  onSubmitted: (_) => _submit(),
                ),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: state.following ? null : _submit,
                  child: Text(l10n.substack_preview_publication),
                ),
                if (state.loading)
                  const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: LinearProgressIndicator()),
                if (state.error != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Column(
                      children: [
                        Text(l10n.plugin_substack_add_error, textAlign: TextAlign.center),
                        TextButton(onPressed: publication == null ? _submit : _follow, child: Text(l10n.retry)),
                      ],
                    ),
                  ),
                if (publication != null) ...[
                  const SizedBox(height: 20),
                  Text(publication.displayName, style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 4),
                  Text(publication.baseUrl),
                  if (publication.description?.isNotEmpty == true)
                    Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: Text(publication.description!)),
                  Wrap(
                    spacing: 8,
                    children: [
                      FilledButton.tonalIcon(
                        onPressed: state.loading || state.following || state.followed ? null : _follow,
                        icon: Icon(state.followed ? Icons.check_circle_outline : Icons.add_circle_outline),
                        label: Text(
                          state.followed
                              ? l10n.plugin_substack_followed(publication.displayName)
                              : l10n.plugin_substack_follow,
                        ),
                      ),
                      TextButton.icon(
                        onPressed: state.following || state.loading ? null : _group,
                        icon: const Icon(Icons.group_add_outlined),
                        label: Text(l10n.add_to_group),
                      ),
                      TextButton.icon(
                        onPressed: () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => SubstackArchiveScreen(publication: publication)),
                        ),
                        icon: const Icon(Icons.newspaper_outlined),
                        label: Text(l10n.plugin_substack_publication),
                      ),
                    ],
                  ),
                ],
                if (state.postError != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Column(
                      children: [
                        Text(l10n.substack_preview_post_failed),
                        TextButton(onPressed: _store.retryPost, child: Text(l10n.retry)),
                      ],
                    ),
                  ),
                if (post != null)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.article_outlined),
                    title: Text(post.title),
                    subtitle: Text(l10n.plugin_substack_open_pasted_post),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () =>
                        Navigator.push(context, MaterialPageRoute(builder: (_) => SubstackReaderScreen(post: post))),
                  ),
                if (state.followed)
                  TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.plugin_substack_done)),
              ],
            ),
          ),
        ),
      );
    },
  );
}
