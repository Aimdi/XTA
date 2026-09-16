import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_transfer.dart';

class ResumeClient extends http.BaseClient {
  final Future<http.StreamedResponse> Function(http.BaseRequest) response;
  ResumeClient(this.response);
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) => response(request);
}

void main() {
  late Directory directory;
  final entry = DownloadEntry(
    id: 'video',
    uri: Uri.parse('https://example.org/video.mp4'),
    fileName: 'video.mp4',
    createdAt: DateTime(2026),
  );
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('xta-resume');
  });
  tearDown(() => directory.delete(recursive: true));
  Future<String?> attempt(
    Future<http.StreamedResponse> Function(http.BaseRequest) response, {
    List<int>? expected,
    DownloadCancellation? token,
  }) => DownloadTransfer(
    clientFactory: () => ResumeClient(response),
    temporaryDirectory: () async => directory,
    save: (_, file, _) async {
      expect(await file.readAsBytes(), expected);
      return 'saved';
    },
  ).call(entry, token ?? DownloadCancellation(), (_, _) {}, (_) {});
  Future<void> interrupt({String tag = '"v1"'}) async {
    await expectLater(
      attempt((_) async => http.StreamedResponse(Stream.value([1, 2]), 200, contentLength: 4, headers: {'etag': tag})),
      throwsA(isA<HttpException>()),
    );
  }

  test('a new transfer resumes persisted bytes with Range and If-Range', () async {
    await interrupt();
    expect(
      await attempt((request) async {
        expect(request.headers['range'], 'bytes=2-');
        expect(request.headers['if-range'], '"v1"');
        expect(request.headers['accept-encoding'], 'identity');
        return http.StreamedResponse(
          Stream.value([3, 4]),
          206,
          contentLength: 2,
          headers: {'etag': '"v1"', 'content-range': 'bytes 2-3/4'},
        );
      }, expected: [1, 2, 3, 4]),
      'saved',
    );
    expect(await Directory('${directory.path}/xta-download-staging').list().toList(), isEmpty);
  });
  test('changed representation or ignored range restarts instead of concatenating', () async {
    await interrupt();
    await attempt(
      (request) async =>
          http.StreamedResponse(Stream.value([7, 8, 9]), 200, contentLength: 3, headers: {'etag': '"v2"'}),
      expected: [7, 8, 9],
    );
  });
  test('invalid Content-Range is rejected and forces a clean retry', () async {
    await interrupt();
    await expectLater(
      attempt(
        (_) async => http.StreamedResponse(
          Stream.value([3, 4]),
          206,
          contentLength: 2,
          headers: {'etag': '"v1"', 'content-range': 'bytes 1-2/4'},
        ),
      ),
      throwsA(isA<HttpException>()),
    );
    await attempt((request) async {
      expect(request.headers['range'], isNull);
      return http.StreamedResponse(Stream.value([5]), 200, contentLength: 1);
    }, expected: [5]);
  });
  test('416 retries once without a range', () async {
    await interrupt();
    var calls = 0;
    await attempt((request) async {
      if (++calls == 1) return http.StreamedResponse(const Stream.empty(), 416);
      expect(request.headers['range'], isNull);
      return http.StreamedResponse(Stream.value([9]), 200, contentLength: 1);
    }, expected: [9]);
    expect(calls, 2);
  });
  test('weak validators cannot authorize byte concatenation', () async {
    await interrupt(tag: 'W/"v1"');
    await attempt((request) async {
      expect(request.headers['range'], isNull);
      return http.StreamedResponse(Stream.value([4]), 200, contentLength: 1);
    }, expected: [4]);
  });
  test('a changed ETag in a partial response never reaches the destination', () async {
    await interrupt();
    await expectLater(
      attempt(
        (_) async => http.StreamedResponse(
          Stream.value([3, 4]),
          206,
          contentLength: 2,
          headers: {'etag': '"v2"', 'content-range': 'bytes 2-3/4'},
        ),
      ),
      throwsA(isA<HttpException>()),
    );
  });
}
