import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_group.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_segmented_switch.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

export 'package:xta/plugins/pixiv/pixiv_social_api.dart' show PixivUserListKind;

/// Creators in Pixiv's order, each once, as their pages arrive.
class PixivUserListStore extends PixivPagedListStore<PixivUserPreview> with PixivTrackedPages<PixivUserPreview> {
  PixivUserListStore(super.loader, {super.filter}) : super(keyOf: _userId);
}

int _userId(PixivUserPreview preview) => preview.user.id;

/// Pages of the [kind] list for [userId], or for the signed-in reader when null.
PixivPageLoader<PixivUserPreview> pixivUserListLoader(
  PixivSocialApi api,
  PixivUserListKind kind, {
  int? userId,
  String restrict = 'public',
}) =>
    ({nextUrl}) async => api.userList(kind, userId ?? await api.ownUserId(), restrict: restrict, nextUrl: nextUrl);

/// The creators [mute] lets through; a muted creator disappears from lists too.
List<PixivUserPreview> pixivVisibleUsers(List<PixivUserPreview> previews, PixivMuteState mute) => [
  for (final preview in previews)
    if (!mute.authorIds.contains(preview.user.id)) preview,
];

String pixivUserListTitle(L10n l10n, PixivUserListKind kind) => switch (kind) {
  PixivUserListKind.following => l10n.plugin_pixiv_profile_following,
  PixivUserListKind.followers => l10n.plugin_pixiv_profile_followers,
};

Future<void> openPixivUserList(BuildContext context, PixivUserListKind kind, {int? userId}) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => PixivUserListScreen(kind: kind, userId: userId),
  ),
);

/// Whom someone follows, or who follows them, as its own screen.
class PixivUserListScreen extends StatelessWidget {
  final PixivUserListKind kind;

  /// Whose list; null is the signed-in reader's.
  final int? userId;

  const PixivUserListScreen({super.key, required this.kind, this.userId});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(pixivUserListTitle(L10n.of(context), kind))),
    body: PixivUserList(kind: kind, userId: userId),
  );
}

/// Preview cards with follow and Add to group, scrolling on page after page.
/// The reader's own following list switches between public and private follows.
class PixivUserList extends StatefulWidget {
  final PixivUserListKind kind;
  final int? userId;

  const PixivUserList({super.key, required this.kind, this.userId});

  @override
  State<PixivUserList> createState() => _PixivUserListState();
}

class _PixivUserListState extends State<PixivUserList> {
  late final PixivSocialApi _api;
  late final PixivUserListStore _store;
  final _restrict = PluginViewStore<String>('public');

  @override
  void initState() {
    super.initState();
    _api = PixivSocialApi.of(context);
    final mute = context.read<PixivMuteStore>();
    _store = PixivUserListStore(_loader('public'), filter: (previews) => pixivVisibleUsers(previews, mute.state))
      ..refresh();
  }

  bool get _offersRestrict {
    final userId = widget.userId;
    final own = userId == null || userId == context.read<PixivClient>().storedUserId;
    return own && widget.kind == PixivUserListKind.following;
  }

  PixivPageLoader<PixivUserPreview> _loader(String restrict) =>
      pixivUserListLoader(_api, widget.kind, userId: widget.userId, restrict: restrict);

  void _useRestrict(String restrict) {
    if (restrict == _restrict.state) return;
    _restrict.select(restrict);
    _store.useLoader(_loader(restrict));
    _store.refresh();
  }

  @override
  void dispose() {
    _store.destroyWhenSettled();
    _restrict.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      if (_offersRestrict)
        ScopedBuilder<PluginViewStore<String>, String>(
          store: _restrict,
          onState: (context, restrict) => PixivFollowRestrictSwitch(
            key: const ValueKey('pixiv-user-list-restrict'),
            restrict: restrict,
            onChanged: _useRestrict,
          ),
        ),
      Expanded(child: _users(context)),
    ],
  );

  Widget _users(BuildContext context) => ScopedBuilder<PixivUserListStore, List<PixivUserPreview>>(
    store: _store,
    onLoading: (context) =>
        _store.state.isEmpty ? const Center(child: CircularProgressIndicator()) : _list(context, _store.state),
    onError: (context, error) => _store.state.isNotEmpty
        ? _list(context, _store.state)
        : Padding(
            padding: const EdgeInsets.all(24),
            child: FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: pixivErrorMessage(L10n.of(context), error ?? Exception()),
              onRetry: _store.refresh,
            ),
          ),
    onState: (context, previews) => _list(context, previews),
  );

  Widget _list(BuildContext context, List<PixivUserPreview> previews) => ScopedBuilder<PixivMuteStore, PixivMuteState>(
    store: context.read<PixivMuteStore>(),
    onState: (context, mute) => _cards(context, pixivVisibleUsers(previews, mute)),
  );

  Widget _cards(BuildContext context, List<PixivUserPreview> previews) {
    if (previews.isEmpty) return _empty(context);
    final bottom = MediaQuery.paddingOf(context).bottom;
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.pixels > notification.metrics.maxScrollExtent - 800) _store.loadMore();
        return false;
      },
      child: RefreshIndicator(
        onRefresh: _store.refresh,
        child: ListView.separated(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsets.fromLTRB(12, 12, 12, 12 + bottom),
          itemCount: previews.length + (_store.loadingMore ? 1 : 0),
          separatorBuilder: (_, _) => const SizedBox(height: 12),
          itemBuilder: (context, index) => index < previews.length
              ? _card(context, previews[index])
              : const Center(child: CircularProgressIndicator()),
        ),
      ),
    );
  }

  Widget _card(BuildContext context, PixivUserPreview preview) => PixivUserPreviewCard(
    key: ValueKey('pixiv-user-list-${preview.user.id}'),
    preview: preview,
    actions: [
      IconButton(
        key: ValueKey('pixiv-user-list-group-${preview.user.id}'),
        tooltip: L10n.of(context).add_to_group,
        icon: const Icon(Icons.group_add_outlined),
        onPressed: () => addPixivToGroup(context, preview.user),
      ),
    ],
  );

  Widget _empty(BuildContext context) {
    final l10n = L10n.of(context);
    return EmptyPane(
      icon: Icons.people_outline,
      message: switch (widget.kind) {
        PixivUserListKind.following => l10n.plugin_pixiv_profile_following_empty,
        PixivUserListKind.followers => l10n.plugin_pixiv_profile_followers_empty,
      },
      onRefresh: _store.refresh,
      action: FilledButton.icon(onPressed: _store.refresh, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
    );
  }
}
