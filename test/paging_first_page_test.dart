import 'package:flutter_test/flutter_test.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/utils/paging.dart';

void main() {
  test('pagingAwaitingFirstPage is only the in-flight first page', () {
    expect(pagingAwaitingFirstPage(PagingState<int, String>()), isTrue);
    expect(
      pagingAwaitingFirstPage(
        PagingState<int, String>(error: Exception('nope')),
      ),
      isFalse,
    );
    expect(
      pagingAwaitingFirstPage(
        PagingState<int, String>(pages: const [<String>[]], keys: const [0]),
      ),
      isFalse,
    );
  });

  testWidgets('scheduleFirstPageFetch kicks page 0 once', (tester) async {
    var fetches = 0;
    final controller = PagingController<int, String>(
      getNextPageKey: (state) {
        final keys = state.keys;
        if (keys == null || keys.isEmpty) return 0;
        return null;
      },
      fetchPage: (key) async {
        fetches++;
        return ['a'];
      },
    );
    addTearDown(controller.dispose);

    var started = false;
    scheduleFirstPageFetch(
      controller,
      alreadyStarted: started,
      markStarted: () => started = true,
      isMounted: () => true,
    );
    expect(started, isTrue);
    expect(fetches, 0);

    scheduleFirstPageFetch(
      controller,
      alreadyStarted: started,
      markStarted: () => fail('must not start twice'),
      isMounted: () => true,
    );

    tester.binding.scheduleFrame();
    await tester.pump();
    expect(fetches, 1);
  });
}
