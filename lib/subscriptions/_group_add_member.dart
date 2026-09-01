import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/subscriptions/group_add_follow.dart';
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
  final _added = <String>{};

  List<UserWithExtra>? _users;
  Object? _error;
  String _query = '';
  bool _busy = false;
  // True only between the reader asking X and X answering. Before they ask,
  // an empty user list is not a load in progress and must not spin.
  bool _searching = false;

  @override
  void dispose() {
    _controller.dispose();
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

  Future<void> _search(String value) async {
    final query = value.trim();
    if (query.isEmpty) {
      return;
    }

    setState(() {
      _query = query;
      _users = null;
      _error = null;
      _searching = true;
    });

    try {
      final users = await Twitter.searchUsers(query, limit: 20);
      if (mounted && _query == query) {
        setState(() {
          _users = users;
          _searching = false;
        });
      }
    } catch (e) {
      // A failed X search must not take the other rows down with it: they are
      // independent, and one of them is often exactly what the reader came for.
      if (mounted && _query == query) {
        setState(() {
          _error = e;
          _searching = false;
        });
      }
    }
  }

  Future<void> _addUser(UserWithExtra user) async {
    final id = user.idStr;
    if (id == null || _busy) {
      return;
    }

    setState(() => _busy = true);
    await context.read<SubscriptionsModel>().toggleSubscribe(UserSubscription.fromUser(user), false);
    if (mounted) {
      setState(() {
        _added.add(id);
        _busy = false;
      });
    }
  }

  Future<void> _addCandidate(GroupAddCandidate candidate) async {
    if (_busy) {
      return;
    }

    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    final failed = L10n.of(context).group_add_member_failed;
    final subscriptions = context.read<SubscriptionsModel>();

    String? id;
    try {
      id = await followGroupAddCandidate(context, candidate);
      // The member list is drawn from the subscriptions, so it has to be re-read
      // before the new row can be ticked.
      await subscriptions.reloadSubscriptions();
    } catch (_) {
      messenger.showSnackBar(SnackBar(content: Text(failed)));
    }

    if (mounted) {
      setState(() {
        if (id != null) {
          _added.add(id);
        }
        _busy = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
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
            onChanged: (value) => setState(() {
              _query = value.trim();
              _users = null;
              _error = null;
              _searching = false;
            }),
            onSubmitted: _search,
          ),
          const SizedBox(height: 8),
          Expanded(child: _results(context)),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              onPressed: () => Navigator.pop(context, _added),
              child: Text(l10n.ok),
            ),
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
    final users = _users;

    return ListView(
      children: [
        for (final candidate in candidates) _candidateTile(context, candidate),
        if (_error != null)
          ListTile(
            leading: const Icon(Icons.error_outline),
            title: Text(l10n.unable_to_load_the_search_results),
          )
        else if (_searching)
          const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator()))
        else if ((users ?? const []).isEmpty && candidates.isEmpty)
          Padding(padding: const EdgeInsets.all(24), child: Center(child: Text(l10n.no_results)))
        else
          for (final user in users ?? const <UserWithExtra>[])
            ListTile(
              leading: UserAvatar(uri: user.profileImageUrlHttps),
              title: Text(user.name ?? ''),
              subtitle: Text('@${user.screenName ?? ''}'),
              trailing: _added.contains(user.idStr) ? const Icon(Icons.check) : const Icon(Icons.add),
              onTap: () => _addUser(user),
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
      trailing: _added.contains(candidate.value.toLowerCase()) ? const Icon(Icons.check) : const Icon(Icons.add),
      onTap: () => _addCandidate(candidate),
    );
  }
}
