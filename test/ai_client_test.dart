import 'dart:convert';
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/utils/ai_client.dart';

void main() {
  test('empty fields mean AI stays off', () {
    expect(const AiConfig(baseUrl: '', apiKey: 'k', model: 'grok-4').isConfigured, isFalse);
    expect(const AiConfig(baseUrl: aiGrokBaseUrl, apiKey: 'k', model: aiGrokModel).isConfigured, isTrue);
  });

  test('recognises a Grok endpoint or model', () {
    expect(const AiConfig(baseUrl: aiGrokBaseUrl, apiKey: 'k', model: 'anything').isGrok, isTrue);
    expect(const AiConfig(baseUrl: aiOpenAiBaseUrl, apiKey: 'k', model: 'grok-4').isGrok, isTrue);
    expect(const AiConfig(baseUrl: aiOpenAiBaseUrl, apiKey: 'k', model: aiOpenAiModel).isGrok, isFalse);
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
      const AiConfig(baseUrl: '$aiGrokBaseUrl/', apiKey: 'secret', model: aiGrokModel),
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

  test('OpenRouter keeps provider/model IDs and posts to the documented endpoint', () async {
    final client = MockClient((request) async {
      expect(request.url.toString(), 'https://openrouter.ai/api/v1/chat/completions');
      expect(request.headers['Authorization'], 'Bearer router-key');
      expect(jsonDecode(request.body)['model'], 'openrouter/free');
      return http.Response('{"choices":[{"message":{"content":"ranked"}}]}', 200);
    });
    expect(
      await aiChatCompletion(
        const AiConfig(baseUrl: aiOpenRouterBaseUrl, apiKey: 'router-key', model: aiOpenRouterModel),
        'Rank these accounts',
        client: client,
      ),
      'ranked',
    );
  });

  test('accepts full endpoints and custom paths without duplicating chat/completions', () {
    expect(
      aiChatUri('https://custom.example/proxy/v1/chat/completions/?api-version=1').toString(),
      'https://custom.example/proxy/v1/chat/completions?api-version=1',
    );
    expect(aiChatUri('http://localhost:8080/v1').toString(), 'http://localhost:8080/v1/chat/completions');
    expect(aiChatUri('https://openrouter.ai/'), aiChatUri(aiOpenRouterBaseUrl));
    expect(() => aiChatUri('not a URL'), throwsA(isA<AiException>()));
  });

  test('a stalled provider times out', () async {
    final client = MockClient((_) => Completer<http.Response>().future);
    await expectLater(
      aiChatCompletion(
        const AiConfig(baseUrl: aiOpenRouterBaseUrl, apiKey: 'k', model: aiOpenRouterModel),
        'ping',
        client: client,
        timeout: const Duration(milliseconds: 10),
      ),
      throwsA(isA<TimeoutException>()),
    );
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
    expect(aiCompletionText('<html>Bad gateway</html>'), '');
    expect(aiCompletionText('{"choices":[{"message":{"content":123}}]}'), '');
    expect(aiCompletionText('{"choices":[{"text":{}}]}'), '');
    expect(
      aiCompletionText(
        jsonEncode({
          'choices': [
            {
              'message': {
                'content': [
                  {'type': 'text', 'text': 'first'},
                  {'type': 'text', 'text': 42},
                  {'type': 'image_url', 'text': 'ignored'},
                  {'type': 'text', 'text': 'second'},
                ],
              },
            },
          ],
        }),
      ),
      'first\nsecond',
    );
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
