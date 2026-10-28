import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/quick_pick.dart';

String _name(String item) => item;

QuickPickView _view({String query = '', bool showAll = false}) =>
    (query: query, showAll: showAll, createdIds: const []);

void main() {
  final eight = [for (var i = 0; i < 8; i++) 'Choice $i'];

  test('six show until All, which shows every one', () {
    final recent = quickPickChips(eight, _view(), _name);
    expect(recent.items, hasLength(kQuickPickShown));
    expect(recent.section, QuickPickSection.recent);
    expect(recent.hasMore, isTrue);

    final all = quickPickChips(eight, _view(showAll: true), _name);
    expect(all.items, hasLength(8));
    expect(all.section, QuickPickSection.all);
    expect(all.hasMore, isFalse);
  });

  test('a sheet can show more before All', () {
    final chips = quickPickChips(eight, _view(), _name, shown: 8);
    expect(chips.items, hasLength(8));
    expect(chips.hasMore, isFalse);
  });

  test('created choices lead, and every choice shows once', () {
    final ordered = quickPickOrdered(['a', 'b', 'c', 'new'], ['c', 'a', 'b'], ['new'], (item) => item);
    expect(ordered, ['new', 'c', 'a', 'b']);
  });

  test('a choice gone since the sheet opened is passed over', () {
    expect(quickPickOrdered(['a', 'b'], ['gone', 'b', 'a'], const [], (item) => item), ['b', 'a']);
  });

  group('searching', () {
    const names = ['Art', 'Japan Trip', 'News'];

    test('matches ignore case, and a typed name can become a choice', () {
      final chips = quickPickChips(names, _view(query: ' JAP'), _name);
      expect(chips.items, ['Japan Trip']);
      expect(chips.section, QuickPickSection.matches);
      expect(chips.canCreate, isTrue);
      expect(chips.hasMore, isFalse);
    });

    test('a name a choice has, whatever its case, cannot be created again', () {
      expect(quickPickNameTaken(names, 'japan trip', _name), isTrue);
      expect(quickPickChips(names, _view(query: 'japan trip '), _name).canCreate, isFalse);
    });

    test('nothing found says so and offers the name', () {
      final chips = quickPickChips(names, _view(query: 'Kyoto'), _name);
      expect(chips.items, isEmpty);
      expect(chips.section, QuickPickSection.noMatch);
      expect(chips.canCreate, isTrue);
    });
  });

  test('creating empties the search and keeps All as it was', () {
    final store = QuickPickStore()
      ..search('Kyoto')
      ..showAll()
      ..created('k');
    expect(store.state.query, isEmpty);
    expect(store.state.showAll, isTrue);
    expect(store.state.createdIds, ['k']);
    store.destroy();
  });
}
