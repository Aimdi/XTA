import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/x_cookie_login.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_skeleton.dart';

/// The screen names of the reader's own X accounts on this device, told apart
/// without their `@` and in any case. Null until they are read.
class ReaderAccountsStore extends Store<Set<String>?> {
  final Future<List<Account>> Function() _read;
  bool _active = true;

  ReaderAccountsStore(this._read) : super(null);

  static String _key(String screenName) => normalizeScreenName(screenName).toLowerCase();

  /// Reads the accounts. A table that cannot be read holds none of them.
  Future<void> load() async {
    Set<String> names;
    try {
      names = {
        for (final account in await _read())
          if (account.screenName case final name?) _key(name),
      };
    } catch (_) {
      names = const {};
    }
    if (_active) update(names);
  }

  /// Whether [screenName] is one of the reader's own accounts.
  bool owns(String? screenName) => screenName != null && (state?.contains(_key(screenName)) ?? false);

  @override
  Future<void> destroy() {
    _active = false;
    return super.destroy();
  }
}

/// A tab of a protected account's posts, which X sends only to the account's
/// approved followers. While they are [hidden] the tab says so in [message],
/// and [child] is never built, so nothing asks X for them.
///
/// Unless the account is one of the reader's own: X reports an account as not
/// following itself, yet sends it its posts. [accounts] reads the reader's
/// accounts, [getAccounts] when not given, and only for posts X keeps back.
class ProtectedPostsGate extends StatefulWidget {
  final bool hidden;

  /// The account's handle, looked for among the reader's accounts.
  final String? screenName;
  final String message;
  final Widget child;
  final Future<List<Account>> Function()? accounts;

  const ProtectedPostsGate({
    super.key,
    required this.hidden,
    required this.screenName,
    required this.message,
    required this.child,
    this.accounts,
  });

  @override
  State<ProtectedPostsGate> createState() => _ProtectedPostsGateState();
}

class _ProtectedPostsGateState extends State<ProtectedPostsGate> {
  ReaderAccountsStore? _readerAccounts;

  @override
  void initState() {
    super.initState();
    _readIfHidden();
  }

  @override
  void didUpdateWidget(ProtectedPostsGate oldWidget) {
    super.didUpdateWidget(oldWidget);
    _readIfHidden();
  }

  void _readIfHidden() {
    if (!widget.hidden || _readerAccounts != null) return;
    final store = _readerAccounts = ReaderAccountsStore(widget.accounts ?? getAccounts);
    unawaited(store.load());
  }

  @override
  void dispose() {
    _readerAccounts?.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final store = _readerAccounts;
    if (!widget.hidden || store == null) return widget.child;
    return ScopedBuilder<ReaderAccountsStore, Set<String>?>(
      store: store,
      onState: (context, names) {
        // The tab's own placeholder, for the moment the accounts take to read.
        if (names == null) return const TweetFeedSkeleton();
        if (store.owns(widget.screenName)) return widget.child;
        // A scroll view, as the tab it stands in for, so the header above
        // still scrolls away.
        return ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [TweetStateTile(icon: Icons.lock_outline, message: widget.message)],
        );
      },
    );
  }
}
