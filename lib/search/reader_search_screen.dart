import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/archive/archive_document_screen.dart';
import 'package:xta/archive/archive_notes.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/saved/local_note_thread.dart';
import 'package:xta/saved/local_post_model.dart';
import 'package:xta/saved/saved_screen.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/search/reader_search_store.dart';
import 'package:xta/search/search.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/utils/local_json_store.dart';

class ReaderSearchScreen extends StatefulWidget {
  final String initialQuery;
  const ReaderSearchScreen({super.key, this.initialQuery = ''});
  @override
  State<ReaderSearchScreen> createState() => _ReaderSearchScreenState();
}

class _ReaderSearchScreenState extends State<ReaderSearchScreen> {
  final _store = ReaderSearchStore();
  late final _controller = TextEditingController(text: widget.initialQuery);
  @override
  void initState() {
    super.initState();
    _store.query(widget.initialQuery);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _load();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _store.destroy();
    super.dispose();
  }

  int _generation = 0;
  Future<void> _load() async {
    try {
      await _reload();
    } catch (error) {
      if (mounted) _store.fail(error);
    }
  }

  Future<void> _reload() async {
    final generation = ++_generation;
    final saved = context.read<SavedTweetModel>();
    final notes = context.read<LocalPostModel>();
    await Future.wait([saved.refreshSavedTweets(), notes.refreshLocalPosts()]);
    if (!mounted || generation != _generation) return;
    final local = <ReaderSearchDocument>[
      for (final row in saved.state)
        ReaderSearchDocument(
          id: 'saved:${row.id}',
          title: saved.contentOf(row.id)?.haystack.split('\n').firstOrNull ?? row.id,
          text: '${saved.contentOf(row.id)?.haystack ?? ''} ${row.note ?? ''}',
          kind: ReaderSearchKind.saved,
          target: row,
        ),
      for (final row in notes.state)
        ReaderSearchDocument(
          id: 'note:${row.id}',
          title: row.body,
          text: row.body,
          kind: ReaderSearchKind.note,
          target: row,
        ),
      for (final row in context.read<SubscriptionsModel>().state)
        ReaderSearchDocument(
          id: 'account:${row.id}',
          title: row.name,
          text: row.screenName,
          kind: ReaderSearchKind.account,
          target: row,
        ),
      for (final row in context.read<GroupsModel>().state)
        ReaderSearchDocument(
          id: 'group:${row.id}',
          title: row.name,
          text: row.name,
          kind: ReaderSearchKind.group,
          target: row,
        ),
    ];
    await _store.load(local, () async {
      final offline = OfflineStore.shared;
      await offline.load();
      final annotations = await LocalJsonStore.shared.readPrefix('archive-note:');
      final result = <ReaderSearchDocument>[];
      for (final entry in offline.state.entries) {
        if (entry.sensitive) continue;
        final article = entry.hasArticle ? await offline.article(entry.id) : null;
        final note = ArchiveAnnotation.parse(annotations['archive-note:${entry.id}']);
        result.add(
          ReaderSearchDocument(
            id: 'article:${entry.id}',
            title: entry.title,
            text: '${entry.source} ${article == null ? '' : archivePlainText(article.bodyHtml)} ${note.searchable}',
            kind: ReaderSearchKind.article,
            target: entry,
          ),
        );
      }
      return result;
    });
  }

  Future<void> _open(ReaderSearchDocument result) async {
    final target = result.target;
    if (target is SavedTweet) {
      final model = context.read<SavedTweetModel>();
      await Navigator.push(
        context,
        MaterialPageRoute<void>(
          builder: (_) => Scaffold(
            appBar: AppBar(title: Text(L10n.of(context).saved)),
            body: TweetContextScope(
              child: ListView(
                children: [SavedClipTile(saved: target, onNoteChanged: (note) => model.setNote(target.id, note))],
              ),
            ),
          ),
        ),
      );
    } else if (target is LocalPost) {
      await openLocalNoteThread(context, rootId: target.inReplyToId ?? target.id);
    } else if (target is SubscriptionGroup) {
      await Navigator.pushNamed(
        context,
        routeGroup,
        arguments: GroupScreenArguments(id: target.id, name: target.name),
      );
    } else if (target is Subscription) {
      final source = subscriptionSources.where((source) => source.owns(target)).firstOrNull;
      final destination = source?.destinationFor(target);
      if (destination != null) {
        await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => destination()));
      } else if (target is UserSubscription) {
        await Navigator.pushNamed(
          context,
          routeProfile,
          arguments: ProfileScreenArguments(target.id, target.screenName, null),
        );
      } else if (target is SearchSubscription) {
        await Navigator.pushNamed(context, routeSearch, arguments: SearchArguments(0, query: target.id));
      }
    } else if (target is OfflineEntry) {
      await openArchiveDocument(context, target);
    }
    if (mounted) await _load();
  }

  String _label(L10n l10n, ReaderSearchKind kind) => switch (kind) {
    ReaderSearchKind.saved => l10n.saved,
    ReaderSearchKind.note => l10n.local_note_author,
    ReaderSearchKind.account => l10n.following,
    ReaderSearchKind.group => l10n.groups,
    ReaderSearchKind.article => l10n.offline_library_title,
  };
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final plugins = searchablePlugins(PrefService.of(context));
    return Scaffold(
      appBar: AppBar(title: Text(l10n.reader_search_all)),
      body: SafeArea(
        child: ScopedBuilder<ReaderSearchStore, ReaderSearchState>(
          store: _store,
          onState: (context, state) {
            final results = state.results;
            return Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: TextField(
                    controller: _controller,
                    autofocus: true,
                    decoration: InputDecoration(prefixIcon: const Icon(Icons.search), hintText: l10n.reader_search_all),
                    onChanged: _store.query,
                  ),
                ),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      ChoiceChip(
                        label: Text(l10n.all),
                        selected: state.kind == null,
                        onSelected: (_) => _store.filter(null),
                      ),
                      for (final kind in ReaderSearchKind.values)
                        Padding(
                          padding: const EdgeInsetsDirectional.only(start: 6),
                          child: ChoiceChip(
                            label: Text(_label(l10n, kind)),
                            selected: state.kind == kind,
                            onSelected: (_) => _store.filter(kind),
                          ),
                        ),
                    ],
                  ),
                ),
                if (state.query.trim().isNotEmpty)
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        ActionChip(
                          avatar: const Icon(Icons.travel_explore),
                          label: Text('X · ${l10n.search}'),
                          onPressed: () => Navigator.pushNamed(
                            context,
                            routeSearch,
                            arguments: SearchArguments(0, query: state.query),
                          ),
                        ),
                        for (final plugin in plugins)
                          Padding(
                            padding: const EdgeInsetsDirectional.only(start: 6),
                            child: ActionChip(
                              avatar: Icon(plugin.icon),
                              label: Text(plugin.title(context)),
                              onPressed: () => plugin.openSearch(context, initialQuery: state.query),
                            ),
                          ),
                      ],
                    ),
                  ),
                if (state.loading) const LinearProgressIndicator(),
                if (state.error != null) TextButton(onPressed: _load, child: Text(l10n.retry)),
                Expanded(
                  child: results.isEmpty
                      ? Center(child: Text(l10n.no_results))
                      : ListView.builder(
                          itemCount: results.length,
                          itemBuilder: (context, index) {
                            final result = results[index];
                            return ListTile(
                              key: ValueKey(result.id),
                              title: Text(result.title, maxLines: 2, overflow: TextOverflow.ellipsis),
                              subtitle: Text(
                                '${_label(l10n, result.kind)} · ${result.text}',
                                maxLines: 3,
                                overflow: TextOverflow.ellipsis,
                              ),
                              onTap: () => _open(result),
                            );
                          },
                        ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
