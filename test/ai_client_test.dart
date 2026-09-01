import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/utils/ai_client.dart';

void main() {
  test('empty fields mean AI stays off', () {
    expect(
      const AiConfig(baseUrl: '', apiKey: 'k', model: 'grok-4').isConfigured,
      isFalse,
    );
    expect(
      const AiConfig(
        baseUrl: aiGrokBaseUrl,
        apiKey: 'k',
        model: aiGrokModel,
      ).isConfigured,
      isTrue,
    );
  });

  test('recognises a Grok endpoint or model', () {
    expect(
      const AiConfig(
        baseUrl: aiGrokBaseUrl,
        apiKey: 'k',
        model: 'anything',
      ).isGrok,
      isTrue,
    );
    expect(
      const AiConfig(
        baseUrl: aiOpenAiBaseUrl,
        apiKey: 'k',
        model: 'grok-4',
      ).isGrok,
      isTrue,
    );
    expect(
      const AiConfig(
        baseUrl: aiOpenAiBaseUrl,
        apiKey: 'k',
        model: aiOpenAiModel,
      ).isGrok,
      isFalse,
    );
  });

  test('reads prefs without throwing on missing keys', () {
    final prefs = PrefServiceCache();
    final config = AiConfig.fromPrefs(prefs);
    expect(config.isConfigured, isFalse);
  });

  test('posts a chat completion and reads the text', () async {
    http.Request? seen;
    final client = MockClient((request) async {
      seen = request;
      return http.Response(
        jsonEncode({
          'choices': [
            {
              'message': {'content': '  hello  '},
            },
          ],
        }),
        200,
      );
    });

    final text = await aiChatCompletion(
      const AiConfig(
        baseUrl: '$aiGrokBaseUrl/',
        apiKey: 'secret',
        model: aiGrokModel,
      ),
      'ping',
      client: client,
    );

    expect(text, 'hello');
    expect(seen!.url, Uri.parse('$aiGrokBaseUrl/chat/completions'));
    expect(seen!.headers['Authorization'], 'Bearer secret');
    final body = jsonDecode(seen!.body) as Map<String, dynamic>;
    expect(body['model'], aiGrokModel);
    expect(body['messages'][0]['content'], 'ping');
  });

  test('a non-2xx response is an AiException', () async {
    final client = MockClient((_) async => http.Response('nope', 401));
    expect(
      () => aiChatCompletion(
        const AiConfig(baseUrl: aiGrokBaseUrl, apiKey: 'k', model: aiGrokModel),
        'ping',
        client: client,
      ),
      throwsA(isA<AiException>()),
    );
  });

  test('aiCompletionText survives a reshaped body', () {
    expect(aiCompletionText('[]'), '');
    expect(aiCompletionText('{"choices":[]}'), '');
    expect(
      aiCompletionText(
        jsonEncode({
          'choices': [
            {'text': 'legacy'},
          ],
        }),
      ),
      'legacy',
    );
  });
}
