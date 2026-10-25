import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/client/client.dart';

/// A community's header, read once per page.
class CommunityStore extends Store<Community> {
  final String id;
  final Future<Community> Function(String id) _read;

  CommunityStore(this.id, {Future<Community> Function(String id)? read})
    : _read = read ?? Twitter.getCommunity,
      super(Community(id: id, name: ''));

  Future<void> load() => execute(() => _read(id));
}
