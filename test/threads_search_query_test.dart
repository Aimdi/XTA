import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/threads/threads_search_query.dart';

/// docs/specs/threads-x-parity-3.md, Search: what a query names, for the search sheet and the results page alike.
void main() {
  test('a post link names the post, and is not kept in the history', () {
    final query = threadsQueryOf(' https://www.threads.com/@maya.makes/post/DAbc123 ');

    expect(query, isA<ThreadsPostQuery>().having((query) => query.link.handle, 'handle', 'maya.makes'));
    expect(query!.remembered, isNull);
    expect(query.terms, isNull, reason: 'it opens the post rather than searching');
  });

  test('a #tag names the posts filed under it, spaces and all', () {
    expect(threadsQueryOf('#Keramik'), isA<ThreadsTagQuery>().having((query) => query.tag, 'tag', 'Keramik'));
    final club = threadsQueryOf('# Book Club ')!;
    expect(club.terms, (query: 'Book Club', tag: true, profile: null));
    expect(club.remembered, '#Book Club');
    expect(threadsQueryOf('#'), isA<ThreadsWordsQuery>(), reason: 'a # alone names no tag');
  });

  test('an @handle or a profile link names the profile', () {
    expect(
      threadsQueryOf('@Maya.Makes'),
      isA<ThreadsProfileQuery>().having((query) => query.handle, 'handle', 'maya.makes'),
    );
    final linked = threadsQueryOf('https://www.threads.net/@ola')!;
    expect(linked, isA<ThreadsProfileQuery>().having((query) => query.handle, 'handle', 'ola'));
    expect(linked.remembered, 'ola');
    expect(linked.terms, isNull);
  });

  test('other words search posts, offering the handle they also spell', () {
    expect(threadsQueryOf('maya.makes')!.terms, (query: 'maya.makes', tag: false, profile: 'maya.makes'));
    expect(threadsQueryOf('glaze recipes')!.terms, (query: 'glaze recipes', tag: false, profile: null));
    expect(threadsQueryOf('   '), isNull);
  });

  test('the field shows a tag with its #', () {
    expect(threadsSearchText((query: 'Keramik', tag: true, profile: null)), '#Keramik');
    expect(threadsSearchText((query: 'kiln', tag: false, profile: null)), 'kiln');
  });
}
