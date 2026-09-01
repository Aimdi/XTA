import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/threaded_conversation.dart';

TweetChain _chain(String id, {String? replyTo}) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..inReplyToStatusIdStr = replyTo;
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  test('buildCappedThreadList inserts continue markers past depth 2', () {
    final nodes = buildThreadTree([
      _chain('root'),
      _chain('a', replyTo: 'root'),
      _chain('b', replyTo: 'a'),
      _chain('c', replyTo: 'b'),
      _chain('d', replyTo: 'c'),
    ], 'root');

    final display = buildCappedThreadList(nodes, maxDepth: 2);
    expect(display.whereType<ThreadDisplayNode>().length, lessThan(nodes.length));
    expect(display.whereType<ThreadContinueMarker>(), isNotEmpty);
  });

  test('skipThreadSubtree jumps past children', () {
    final nodes = [
      ThreadNode(_chain('a'), 0),
      ThreadNode(_chain('b'), 1),
      ThreadNode(_chain('c'), 2),
      ThreadNode(_chain('d'), 1),
    ];
    // Pre-order: a → b → c → d(sibling of b). Subtree of a is the whole list.
    expect(skipThreadSubtree(nodes, 0), 4);
    // Subtree of b is only c; stop at d (same depth as b).
    expect(skipThreadSubtree(nodes, 1), 3);
  });
}
