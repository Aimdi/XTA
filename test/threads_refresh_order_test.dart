import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_store.dart';

class _Accounts extends ThreadsAccountsStore {
  _Accounts(List<ThreadsAccount> accounts) {
    update(accounts);
  }

  @override
  Future<void> load() async {}
}

/// Answers every account at once, and notes who was asked.
class _Direct extends ThreadsDirectClient {
  _Direct(super.prefs);

  final asked = <String>[];

  @override
  Future<List<ThreadsPost>> fetchGuestAccount(String handle) async {
    asked.add(handle);
    return [ThreadsPost(id: handle, handle: handle, authorName: handle, text: 'from $handle')];
  }
}

/// docs/specs/threads-x-parity.md, Following.
void main() {
  test('successive pulls to refresh reach every followed account', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginThreadsDirectCookies: '',
        optionPluginThreadsDirectBearer: '',
        optionPluginThreadsInstance: '',
        optionPluginThreadsFeedSnapshot: '',
      },
    );
    final handles = [for (var i = 1; i <= threadsMaxAccountsPerLoad + 5; i++) 'a${'$i'.padLeft(2, '0')}'];
    final direct = _Direct(prefs);
    final store = ThreadsFeedStore(
      ThreadsClient(httpClient: MockClient((_) async => http.Response('[]', 404))),
      direct,
      prefs,
      _Accounts([for (final handle in handles) ThreadsAccount(handle: handle, name: handle)]),
    );

    await store.refresh(force: true);
    final first = [...direct.asked];
    direct.asked.clear();
    await store.refresh(force: true);
    final second = [...direct.asked];

    expect(first, hasLength(threadsMaxAccountsPerLoad));
    expect(second, hasLength(threadsMaxAccountsPerLoad));
    // The five the first pull could not reach go first on the next.
    expect(second.take(5), handles.skip(threadsMaxAccountsPerLoad));
    expect({...first, ...second}, handles.toSet());
    expect(store.state, hasLength(handles.length));
  });
}
