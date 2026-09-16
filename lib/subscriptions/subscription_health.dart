import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/errors.dart';
import 'package:xta/utils/read_request_scope.dart';

enum SubscriptionHealthKind { exists, renamed, missing, suspended, unreachable, rateLimited }

typedef SubscriptionHealth<T> = ({SubscriptionHealthKind kind, T? profile});

Future<SubscriptionHealth<T>> checkSubscriptionHealth<T>({
  required Future<T> Function() byName,
  required Future<T> Function() byId,
  Duration timeout = const Duration(seconds: 15),
}) async {
  Future<SubscriptionHealth<T>> lookup(Future<T> Function() fetch) async {
    try {
      final profile = await ReadRequestScope().start(fetch, timeout: timeout);
      return (kind: SubscriptionHealthKind.exists, profile: profile);
    } on RateLimitedException {
      return (kind: SubscriptionHealthKind.rateLimited, profile: null);
    } on TwitterError catch (error) {
      return (
        kind: switch (error.code) {
          34 || 50 => SubscriptionHealthKind.missing,
          63 => SubscriptionHealthKind.suspended,
          _ => SubscriptionHealthKind.unreachable,
        },
        profile: null,
      );
    } catch (_) {
      return (kind: SubscriptionHealthKind.unreachable, profile: null);
    }
  }

  final name = await lookup(byName);
  if (name.kind != SubscriptionHealthKind.missing) return name;
  final id = await lookup(byId);
  return id.kind == SubscriptionHealthKind.exists ? (kind: SubscriptionHealthKind.renamed, profile: id.profile) : id;
}
