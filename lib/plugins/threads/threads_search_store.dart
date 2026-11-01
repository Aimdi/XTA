import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// The two orders Threads' public search offers.
enum ThreadsSearchOrder { top, recent }

/// One order of a search of Threads' public pages, for words or a tag (docs/specs/threads-x-parity.md, Search).
/// Null until it has been read.
class ThreadsSearchStore extends Store<List<ThreadsPost>?> {
  final ThreadsDirectClient direct;
  final String query;
  final bool tag;
  final ThreadsSearchOrder order;

  ThreadsSearchStore(this.direct, {required this.query, required this.order, this.tag = false}) : super(null);

  Future<void> load() =>
      execute(() => direct.searchGuestPosts(query, tag: tag, recent: order == ThreadsSearchOrder.recent));
}
