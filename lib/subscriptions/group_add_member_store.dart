import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/user.dart';

typedef GroupAddMemberState = ({
  String query,
  List<UserWithExtra>? users,
  Object? searchError,
  bool searching,
  bool adding,
  Set<String> added,
  Set<String> addedCandidates,
});

class GroupAddMemberStore extends Store<GroupAddMemberState> {
  GroupAddMemberStore()
    : super((
        query: '',
        users: null,
        searchError: null,
        searching: false,
        adding: false,
        added: {},
        addedCandidates: {},
      ));

  int _searchGeneration = 0;
  bool _disposed = false;

  void _update({
    String? query,
    List<UserWithExtra>? users,
    Object? error,
    bool? searching,
    bool? adding,
    Set<String>? added,
    Set<String>? addedCandidates,
    bool resetSearch = false,
  }) {
    if (_disposed) return;
    update((
      query: query ?? state.query,
      users: resetSearch ? null : users ?? state.users,
      searchError: resetSearch ? null : error ?? state.searchError,
      searching: searching ?? state.searching,
      adding: adding ?? state.adding,
      added: added ?? state.added,
      addedCandidates: addedCandidates ?? state.addedCandidates,
    ));
  }

  void changeQuery(String value) {
    _searchGeneration++;
    _update(query: value.trim(), searching: false, resetSearch: true);
  }

  Future<void> search(String value, Future<List<UserWithExtra>> Function(String) searchUsers) async {
    changeQuery(value);
    if (state.query.isEmpty) return;
    final generation = _searchGeneration;
    _update(searching: true);
    try {
      final users = await searchUsers(state.query).timeout(const Duration(seconds: 30));
      if (generation == _searchGeneration) _update(users: users, searching: false);
    } catch (error) {
      if (generation == _searchGeneration) _update(error: error, searching: false);
    }
  }

  Future<bool> add(String candidateKey, Future<String> Function() follow) async {
    if (_disposed || state.adding || state.addedCandidates.contains(candidateKey)) return false;
    _update(adding: true);
    try {
      final id = await follow();
      if (!_disposed) {
        _update(added: {...state.added, id}, addedCandidates: {...state.addedCandidates, candidateKey});
      }
      return true;
    } catch (_) {
      return false;
    } finally {
      _update(adding: false);
    }
  }

  @override
  Future destroy() {
    _disposed = true;
    _searchGeneration++;
    return super.destroy();
  }
}
