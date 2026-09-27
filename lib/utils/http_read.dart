import 'dart:async';

import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart' as errors;
import 'package:xta/utils/read_request_scope.dart';
import 'package:xta/utils/read_retry.dart';

extension ResilientHttpRead on http.Client {
  /// GET only: callers keep control of authentication, parsing and write flows.
  Future<http.Response> getWithReadRetry(
    Uri uri, {
    Map<String, String>? headers,
    required Duration timeout,
    Future<void> Function()? beforeRetry,
  }) async {
    final abort = Completer<void>();
    var attempts = 0;
    try {
      return await ReadRequestScope().start(() async {
        if (attempts++ > 0) await beforeRetry?.call();
        ReadWork.checkpoint();
        final request = http.AbortableRequest('GET', uri, abortTrigger: abort.future);
        if (headers != null) request.headers.addAll(headers);
        final response = await http.Response.fromStream(await send(request));
        ReadWork.checkpoint();
        if (transientReadStatus(response.statusCode)) throw errors.HttpException(response);
        return response;
      }, timeout: timeout);
    } on errors.HttpException catch (error) {
      return error.response;
    } finally {
      abort.complete();
    }
  }
}
