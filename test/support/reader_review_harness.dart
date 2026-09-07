import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_archive.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/local_post_model.dart';
import 'mastodon_harness.dart';

MastodonPost reviewPost(String id, {String? parent, bool media = false, bool sensitive = false}) =>
  mastodonPostFromStatus({
    'id': id, 'in_reply_to_id': parent, 'url': 'https://studio.example/@maya/$id',
    'account': {'id': 'maya', 'acct': 'maya@studio.example', 'display_name': 'Maya Chen'},
    'content': '<p>${switch(id) {
      'root' => 'A place to pause. A few sketches from the coast this weekend.',
      'a' => 'The light in the second sketch is beautiful. Was this just before sunset?',
      'a1' => 'Yes! About twenty minutes before. Everything changed colour so quickly.',
      'b' => 'These would make a lovely little print collection.',
      _ => 'Field notes: colour, quiet places, and the long way home.',
    }}</p>',
    'created_at': '2026-09-07T09:00:00Z', 'replies_count': parent == null ? 4 : 1,
    'favourites_count': 42, 'reblogs_count': 8, 'sensitive': sensitive,
    'spoiler_text': sensitive ? 'A sensitive sketch' : '',
    'media_attachments': media ? [{'type': 'image', 'preview_url': 'https://review.example/$id.png'}] : [],
  })!;

class ReaderReviewClient extends MastodonFixtureClient {
  final pictures = [for (var i = 0; i < 12; i++) reviewPost('image-$i', media: true, sensitive: i == 3)];
  @override
  Future<({MastodonProfile profile, List<MastodonPost> posts, Set<String> pinnedIds, String instance})>
    profileAnywhere(List<String> instances, String acct) async =>
      (profile: sampleProfile, posts: [reviewPost('root'), ...pictures], pinnedIds: {'root'}, instance: 'https://studio.example');
  @override
  Future<List<MastodonPost>> getStatuses(String instance, String id, {int limit = 20,
    bool excludeReplies = true, bool onlyMedia = false, bool pinned = false, String? maxId}) async => maxId == null ? pictures : [];
  @override
  Future<MastodonThread> fetchThreadAnywhere(List<String> instances, MastodonPost seed) async =>
    MastodonThread(status: seed, descendants: [reviewPost('a', parent: seed.id),
      reviewPost('b', parent: seed.id), reviewPost('a1', parent: 'a')]);
}

class ReviewSaved extends SavedTweetModel {
  ReviewSaved() {
    update([for (final post in [reviewPost('root'), reviewPost('image-0', media: true), reviewPost('a')])
      SavedTweet(id: mastodonArchiveId(post), user: post.acct, folderId: 'inspiration',
        note: post.id == 'root' ? 'Colours to come back to.' : null, content: jsonEncode(mastodonArchiveBlob(post)))]);
  }
  @override
  Future<void> listSavedTweets() async {}
  @override
  Future<void> refreshSavedTweets() async {}
}
class ReviewFolders extends SavedTweetFolderModel {
  ReviewFolders() { update([SavedTweetFolder(id: 'inspiration', name: 'Inspiration', position: 0, createdAt: DateTime(2026, 9, 7))]); }
  @override
  Future<void> listFolders() async {}
}
class ReviewLikes extends LikedTweetModel { @override Future<void> listLikedTweets() async {} }
class ReviewNotes extends LocalPostModel { @override Future<void> listLocalPosts() async {} }
class ReviewGroups extends GroupsModel {
  ReviewGroups(super.prefs);
  @override Future<List<SubscriptionGroupMember>> listGroupMembers() async => [];
  @override Future<({int replies, int retweets})> countIncludeOverrides() async => (replies: 0, retweets: 0);
}

class ReaderReviewHarness extends MastodonHarness {
  final saved = ReviewSaved(); final folders = ReviewFolders(); final likes = ReviewLikes(); final notes = ReviewNotes();
  late final groups = ReviewGroups(prefs);
  ReaderReviewHarness() : super(client: ReaderReviewClient());
  Widget providers(Widget child) => MultiProvider(providers: [
    Provider<SavedTweetModel>.value(value: saved), Provider<SavedTweetFolderModel>.value(value: folders),
    Provider<LikedTweetModel>.value(value: likes), Provider<LocalPostModel>.value(value: notes),
    Provider<GroupsModel>.value(value: groups),
  ], child: child);
  @override
  Future<void> close(WidgetTester tester) async {
    await super.close(tester); await saved.destroy(); await folders.destroy();
    await likes.destroy(); await notes.destroy(); await groups.destroy();
  }
}

/// Deterministic illustration fixtures, not live-account media or network traffic.
Future<List<int>> reviewImageBytes() async {
  final recorder = ui.PictureRecorder(); final canvas = Canvas(recorder);
  canvas.drawRect(const Rect.fromLTWH(0, 0, 480, 360), Paint()..color = const Color(0xFFD8C3A8));
  canvas.drawCircle(const Offset(330, 95), 42, Paint()..color = const Color(0xFFF8E7C9));
  canvas.drawPath(Path()..moveTo(0, 270)..lineTo(150, 150)..lineTo(330, 310)..lineTo(480, 190)
    ..lineTo(480, 360)..lineTo(0, 360)..close(), Paint()..color = const Color(0xFF4B716E));
  canvas.drawPath(Path()..moveTo(0, 300)..quadraticBezierTo(260, 240, 480, 330)
    ..lineTo(480, 360)..lineTo(0, 360)..close(), Paint()..color = const Color(0xFF223E44));
  final picture = recorder.endRecording(); final image = await picture.toImage(480, 360);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose(); picture.dispose(); return bytes!.buffer.asUint8List();
}

class ReviewImageOverrides extends HttpOverrides {
  final List<int> bytes;
  ReviewImageOverrides(this.bytes);
  @override HttpClient createHttpClient(SecurityContext? context) => _ImageClient(bytes);
}
class _ImageClient extends Fake implements HttpClient {
  final List<int> bytes; _ImageClient(this.bytes);
  @override Future<HttpClientRequest> getUrl(Uri url) async => _ImageRequest(bytes);
  @override Future<HttpClientRequest> openUrl(String method, Uri url) async => _ImageRequest(bytes);
  @override set autoUncompress(bool value) {}
  @override void close({bool force = false}) {}
}
class _ImageHeaders extends Fake implements HttpHeaders {
  @override void set(String name, Object value, {bool preserveHeaderCase = false}) {}
}
class _ImageRequest extends Fake implements HttpClientRequest {
  final List<int> bytes; _ImageRequest(this.bytes);
  @override HttpHeaders get headers => _ImageHeaders();
  @override Future<HttpClientResponse> close() async => _ImageResponse(bytes);
}
class _ImageResponse extends Stream<List<int>> implements HttpClientResponse {
  final List<int> bytes; _ImageResponse(this.bytes);
  @override int get statusCode => 200;
  @override int get contentLength => bytes.length;
  @override HttpClientResponseCompressionState get compressionState => HttpClientResponseCompressionState.notCompressed;
  @override StreamSubscription<List<int>> listen(void Function(List<int>)? onData,
    {Function? onError, void Function()? onDone, bool? cancelOnError}) =>
    Stream.value(bytes).listen(onData, onError: onError, onDone: onDone, cancelOnError: cancelOnError);
  @override dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
