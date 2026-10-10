import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/utils/json.dart';

/// Settings of the signed-in Pixiv account that XTA reads. XTA never changes
/// them; the reader does that on pixiv.net.
class PixivAccountApi {
  final PixivClient client;

  const PixivAccountApi(this.client);

  /// A `Provider` override (a test's fake), else one over the app's client.
  static PixivAccountApi of(BuildContext context) =>
      context.read<PixivAccountApi?>() ?? PixivAccountApi(context.read<PixivClient>());

  /// Whether the account shows AI-generated works (`true`) or partly hides
  /// them (`false`) everywhere on Pixiv.
  Future<bool> showsAiWorks() async {
    final json = await client.getJson('/v1/user/ai-show-settings');
    final value = Json(json)['show_ai'].boolean;
    if (value == null) {
      throw PixivException(PixivErrorKind.badResponse, 'ai-show-settings without show_ai');
    }
    return value;
  }
}

/// The account's AI display setting once read; null until then.
class PixivAiShowStore extends Store<bool?> {
  final PixivAccountApi api;

  PixivAiShowStore(this.api) : super(null);

  Future<void> load() => execute(api.showsAiWorks);
}
