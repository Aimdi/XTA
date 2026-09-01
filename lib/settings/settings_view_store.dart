import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/client_regular_account.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_model.dart';

class SettingsValueStore<T> extends Store<T> {
  SettingsValueStore(super.initialState);

  void setValue(T value) => update(value);
}

class SettingsRevisionStore extends Store<int> {
  SettingsRevisionStore() : super(0);

  void refresh() => update(state + 1);
}

class SettingsPackageInfoStore extends Store<PackageInfo?> {
  SettingsPackageInfoStore() : super(null);

  Future<void> load() async {
    await execute(PackageInfo.fromPlatform);
  }
}

class SettingsAccountsStore extends Store<List<Account>> {
  SettingsAccountsStore() : super(const []);

  Future<void> load() async {
    await execute(getAccounts);
  }

  Future<void> delete(String id) async {
    await XRegularAccount().deleteAccount(id);
    await load();
  }
}

typedef FeedOverrideCounts = ({int replies, int retweets});

class SettingsFeedOverridesStore extends Store<FeedOverrideCounts?> {
  final GroupsModel groups;

  SettingsFeedOverridesStore(this.groups) : super(null);

  Future<void> load() async {
    update(await groups.countIncludeOverrides());
  }

  Future<void> applyDefaults() async {
    await groups.clearIncludeOverrides(replies: true);
    await groups.clearIncludeOverrides(replies: false);
    await load();
  }
}

enum SettingsExportOption {
  settings,
  subscriptions,
  groups,
  groupMembers,
  tweets,
  savedFolders,
  likedTweets,
  accounts,
}

@immutable
class SettingsExportState {
  final Set<SettingsExportOption> selected;
  final bool busy;

  const SettingsExportState({this.selected = const {}, this.busy = false});

  bool includes(SettingsExportOption option) => selected.contains(option);

  bool get canIncludeGroupMembers =>
      includes(SettingsExportOption.subscriptions) &&
      includes(SettingsExportOption.groups);

  SettingsExportState copyWith({
    Set<SettingsExportOption>? selected,
    bool? busy,
  }) => SettingsExportState(
    selected: selected ?? this.selected,
    busy: busy ?? this.busy,
  );
}

class SettingsExportStore extends Store<SettingsExportState> {
  SettingsExportStore() : super(const SettingsExportState());

  void toggle(SettingsExportOption option) {
    final next = {...state.selected};
    next.contains(option) ? next.remove(option) : next.add(option);
    if (!next.contains(SettingsExportOption.subscriptions) ||
        !next.contains(SettingsExportOption.groups)) {
      next.remove(SettingsExportOption.groupMembers);
    }
    update(state.copyWith(selected: Set.unmodifiable(next)));
  }

  void setBusy(bool value) => update(state.copyWith(busy: value));
}
