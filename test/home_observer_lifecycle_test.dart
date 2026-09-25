import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/home_model.dart';

class _ObservedHome extends HomeModel {
  var reloads = 0;
  _ObservedHome(super.prefs, super.groupsModel);

  @override
  Future<void> loadPages() async {
    reloads++;
  }
}

void main() {
  test('destroyed home models stop listening to the app group store', () async {
    final groups = GroupsModel(PrefServiceCache(cache: {}));
    addTearDown(groups.destroy);
    final home = _ObservedHome(groups.prefs, groups);
    await Future<void>.delayed(Duration.zero);
    final initial = home.reloads;
    groups.update([], force: true);
    await Future<void>.delayed(Duration.zero);
    expect(home.reloads, initial + 1);

    await home.destroy();
    for (var update = 0; update < 20; update++) {
      groups.update([], force: true);
    }
    await Future<void>.delayed(Duration.zero);
    expect(home.reloads, initial + 1);
  });
}
