import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/subscriptions/group_add_follow.dart';
import 'package:xta/subscriptions/group_add_member_store.dart';
import 'package:xta/subscriptions/group_add_sources.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/user.dart';

/// Adds something new to a group without leaving the group.
///
/// The member list only ever offered what was already subscribed to, so putting
/// an account into a group meant going and following it first, somewhere else,
/// then coming back — and for every network except X and Reddit there was no
/// way in at all. One box now takes an X search, a subreddit, a Threads or
/// Bluesky handle, a Fediverse address or a newsletter, follows whatever is
/// chosen, and hands the ids back to be ticked.
Future<Set<String>> openGroupAddMemberSheet(BuildContext context) async {
  final added = await showModalBottomSheet<Set<String>>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(sheetContext).viewInsets.bottom),
      child: const FractionallySizedBox(heightFactor: 0.85, child: _GroupAddMemberSheet()),
    ),
  );

  return added ?? const {};
}

class _GroupAddMemberSheet extends StatefulWidget {
  const _GroupAddMemberSheet();

  @override
  State<_GroupAddMemberSheet> createState() => _GroupAddMemberSheetState();
}

class _GroupAddMemberSheetState extends State<_GroupAddMemberSheet> {
  final _controller = TextEditingController();
  final _store = GroupAddMemberStore();
  Set<String> get _added => _store.state.added;
  String get _query => _store.state.query;
  bool get _busy => _store.state.adding;

  @override
  void dispose() {
    _controller.dispose();
    _store.destroy();
    super.dispose();
  }

  /// The sources the reader turned on. Following someone onto a tab that is
  /// switched off would put a member in the group whose posts never arrive.
  Set<GroupAddSource> get _enabledSources {
    final prefs = PrefService.of(context, listen: false);
    return {
      for (final source in GroupAddSource.values)
        if (prefs.get<bool>(enabledOptionOfSource(source)) == true) source,
    };
  }

  /// What the typed text could be elsewhere, recomputed as it is typed. No
  /// network is touched, so this can keep up with the keyboard.
  List<GroupAddCandidate> get _candidates => groupAddCandidates(_query, enabled: _enabledSources);

  Future<void> _search(String value) => _store.search(value, (query) => Twitter.searchUsers(query, limit: 20));

  Future<void> _addUser(UserWithExtra user) async {
    final id = user.idStr;
    if (id == null || _busy || _added.contains(id)) return;
    final subscriptions = context.read<SubscriptionsModel>();
    final succeeded = await _store.add('x:$id', () async {
      if (!subscriptions.state.any((item) => item.id == id)) {
        await subscriptions.toggleSubscribe(UserSubscription.fromUser(user), false);
      }
      if (!subscriptions.state.any((item) => item.id == id)) {
        throw StateError('Subscription was not saved');
      }
      return id;
    });
    if (!succeeded && mounted) _showAddError();
  }

  String _candidateKey(GroupAddCandidate candidate) => '${candidate.source.name}:${candidate.value}';

  Future<void> _addCandidate(GroupAddCandidate candidate) async {
    if (_busy || _store.state.addedCandidates.contains(_candidateKey(candidate))) return;
    final subscriptions = context.read<SubscriptionsModel>();
    final succeeded = await _store.add(_candidateKey(candidate), () async {
      final id = await followGroupAddCandidate(context, candidate);
      await subscriptions.reloadSubscriptions();
      if (!subscriptions.state.any((item) => item.id == id)) {
        throw StateError('Subscription was not saved');
      }
      return id;
    });
    if (!succeeded && mounted) _showAddError();
  }

  void _showAddError() {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).group_add_member_failed)));
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<GroupAddMemberStore, GroupAddMemberState>(
    store: _store,
    onState: (context, state) => _content(context),
  );

  Widget _content(BuildContext context) {
    final l10n = L10n.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: Column(
        children: [
          TextField(
            controller: _controller,
            autofocus: true,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              hintText: l10n.search,
              prefixIcon: const Icon(Icons.search),
              border: const OutlineInputBorder(),
            ),
            // Live, so a subreddit or a handle is offered while it is typed —
            // only the X search waits for the keyboard's search key.
            onChanged: _store.changeQuery,
            onSubmitted: _search,
          ),
          if (_busy) const LinearProgressIndicator(),
          const SizedBox(height: 8),
          Expanded(child: _results(context)),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(onPressed: _busy ? null : () => Navigator.pop(context, _added), child: Text(l10n.ok)),
          ),
        ],
      ),
    );
  }

  Widget _results(BuildContext context) {
    final l10n = L10n.of(context);
    if (_query.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 24),
        child: Text(
          l10n.group_add_member_hint,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).hintColor),
        ),
      );
    }

    final candidates = _candidates;
    final users = _store.state.users;

    return ListView(
      children: [
        for (final candidate in candidates) _candidateTile(context, candidate),
        if (_store.state.searchError != null)
          ListTile(leading: const Icon(Icons.error_outline), title: Text(l10n.unable_to_load_the_search_results))
        else if (_store.state.searching)
          const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          )
        else if ((users ?? const []).isEmpty && candidates.isEmpty)
          Padding(
            padding: const EdgeInsets.all(24),
            child: Center(child: Text(l10n.no_results)),
          )
        else
          for (final user in users ?? const <UserWithExtra>[])
            ListTile(
              leading: UserAvatar(uri: user.profileImageUrlHttps),
              title: Text(user.name ?? ''),
              subtitle: Text('@${user.screenName ?? ''}'),
              trailing: _added.contains(user.idStr) ? const Icon(Icons.check) : const Icon(Icons.add),
              onTap: _busy || _added.contains(user.idStr) ? null : () => _addUser(user),
            ),
      ],
    );
  }

  Widget _candidateTile(BuildContext context, GroupAddCandidate candidate) {
    final plugin = pluginById(pluginIdOfSource(candidate.source));

    return ListTile(
      leading: plugin == null
          ? const SizedBox(width: 40, child: Icon(Icons.travel_explore))
          : pluginBrandIcon(context, plugin, size: 40),
      title: Text(candidate.label, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: plugin == null ? null : Text(plugin.title(context)),
      trailing: _store.state.addedCandidates.contains(_candidateKey(candidate))
          ? const Icon(Icons.check)
          : const Icon(Icons.add),
      onTap: _busy || _store.state.addedCandidates.contains(_candidateKey(candidate))
          ? null
          : () => _addCandidate(candidate),
    );
  }
}
