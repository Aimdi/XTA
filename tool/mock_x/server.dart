// ignore_for_file: avoid_print
//
// Serves the recorded responses of test/fixtures as if it were X, for an app
// built with --dart-define=XTA_MOCK_X=http://localhost:8787 (docs/mock-x.md).
//
//   fvm dart run tool/mock_x/server.dart [--port 8787]

import 'dart:io';

import 'mock_server.dart';
import 'mock_x.dart';

Future<void> main(List<String> args) async {
  final portIndex = args.indexOf('--port');
  final port = portIndex >= 0 && portIndex + 1 < args.length ? int.tryParse(args[portIndex + 1]) : 8787;
  if (port == null) {
    stderr.writeln('Usage: dart run tool/mock_x/server.dart [--port 8787]');
    exit(64);
  }
  await serveMockX(
    MockX(Directory('test/fixtures')),
    port: port,
    onAnswer: (method, url, answer) {
      print('${answer.status} $method ${url.path}${answer.fixture == null ? '' : ' <- ${answer.fixture}'}');
      if (!answer.answered) print(answer.body);
    },
  );
  print('The stand-in for X is listening on http://localhost:$port');
}
