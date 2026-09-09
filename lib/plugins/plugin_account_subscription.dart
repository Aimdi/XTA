import 'package:xta/database/entities.dart';

/// Group identity is source-scoped; equal handles on two networks stay separate.
class PluginAccountSubscription extends Subscription {
  final String pluginId;
  final Map<String, Object?> row;

  PluginAccountSubscription(this.pluginId, Map<String, Object?> data)
    : row = Map.unmodifiable(data),
      super(
        id: '$pluginId:${data['id']}',
        screenName: _text(data['screen_name']) ?? '${data['id']}',
        name: _text(data['name']) ?? '${data['id']}',
        profileImageUrlHttps: _text(data['avatar_url']),
        verified: false,
        createdAt: DateTime.tryParse('${data['created_at']}') ?? DateTime.fromMillisecondsSinceEpoch(0),
        inFeed: data['in_feed'] != 0,
      );

  String get accountId => '${row['id']}';

  @override
  Map<String, dynamic> toMap() => Map<String, dynamic>.from(row);
}

String? _text(Object? value) => value is String ? value : null;
