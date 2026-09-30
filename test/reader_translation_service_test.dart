import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/constants.dart';
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/reading/reader_translation_service.dart';
import 'package:xta/utils/ai_client.dart';

const _deepl = ReaderTranslationConfig(provider: ReaderTranslationProvider.deepl, apiKey: 'secret:fx');
const _libre = ReaderTranslationConfig(
  provider: ReaderTranslationProvider.libre,
  endpoint: 'https://tools.example/libre/',
);
const _ai = ReaderTranslationConfig(
  provider: ReaderTranslationProvider.ai,
  ai: AiConfig(baseUrl: 'https://ai.example/v1', apiKey: 'k', model: 'm'),
);

String _joined(List<ReaderTranslationPiece> pieces) => pieces.map((piece) => piece.text).join();

void main() {
  group('pieces', () {
    test('keep URLs, line breaks and surrounding spaces verbatim', () {
      const text = '  Read this: https://example.com/a?b=1 now.\r\n\nSecond line www.example.org\n';
      final pieces = readerTranslationPieces(text);
      expect(_joined(pieces), text);
      expect(
        [
          for (final piece in pieces)
            if (piece.translate) piece.text,
        ],
        ['Read this:', 'now.', 'Second line'],
      );
      expect(pieces.where((piece) => piece.text.contains('https://')).single.translate, isFalse);
    });

    test('do not send runs without letters', () {
      final pieces = readerTranslationPieces('12:30 — 🎉 !!!');
      expect(pieces.every((piece) => !piece.translate), isTrue);
    });

    test('chunk long paragraphs at sentence ends without splitting surrogate pairs', () {
      final sentence = 'Ein kurzer Satz mit Umlauten äöü. ';
      final long = sentence * 400;
      final pieces = readerTranslationPieces(long, max: 1000);
      expect(_joined(pieces), long);
      final chunks = [
        for (final piece in pieces)
          if (piece.translate) piece.text,
      ];
      expect(chunks.length, greaterThan(10));
      expect(chunks.every((chunk) => chunk.length <= 1000 && chunk.endsWith('.')), isTrue);

      final emoji = '😀' * 600;
      final emojiPieces = readerTranslationPieces('a$emoji', max: 101);
      expect(_joined(emojiPieces), 'a$emoji');
      for (final piece in emojiPieces) {
        final last = piece.text.codeUnitAt(piece.text.length - 1);
        expect(last >= 0xd800 && last <= 0xdbff, isFalse);
      }
    });
  });

  group('config', () {
    test('DeepL picks Free or Pro from the key and keeps configured base paths', () {
      expect(readerTranslationUri(_deepl).toString(), 'https://api-free.deepl.com/v2/translate');
      expect(
        readerTranslationUri(
          const ReaderTranslationConfig(provider: ReaderTranslationProvider.deepl, apiKey: 'pro'),
        ).toString(),
        'https://api.deepl.com/v2/translate',
      );
      expect(readerTranslationUri(_libre).toString(), 'https://tools.example/libre/translate');
      expect(readerTranslationUri(_libre, action: 'languages').toString(), 'https://tools.example/libre/languages');
      const typedPath = ReaderTranslationConfig(
        provider: ReaderTranslationProvider.libre,
        endpoint: 'https://tools.example/libre/translate',
      );
      expect(readerTranslationUri(typedPath).toString(), 'https://tools.example/libre/translate');
    });

    test('rejects unusable endpoints and keys', () {
      for (final endpoint in ['ftp://x.example', 'https://user:pass@x.example', 'https://x.example/?a=1', 'x']) {
        expect(
          readerTranslationConfigValid(
            ReaderTranslationConfig(provider: ReaderTranslationProvider.libre, endpoint: endpoint),
          ),
          isFalse,
          reason: endpoint,
        );
      }
      expect(
        readerTranslationConfigValid(const ReaderTranslationConfig(provider: ReaderTranslationProvider.deepl)),
        isFalse,
      );
      expect(readerTranslationConfigValid(_ai), isTrue);
      expect(
        readerTranslationConfigValid(const ReaderTranslationConfig(provider: ReaderTranslationProvider.ai)),
        isFalse,
      );
    });

    test('maps every app locale to provider language codes', () {
      expect(deeplTargetCode('pt_BR'), 'PT-BR');
      expect(deeplTargetCode('pt'), 'PT-PT');
      expect(deeplTargetCode('zh_Hant'), 'ZH-HANT');
      expect(deeplTargetCode('zh_Hans'), 'ZH-HANS');
      expect(deeplTargetCode('nb_NO'), 'NB');
      expect(deeplTargetCode('en'), 'EN-US');
      expect(deeplTargetCode('be_Latn'), 'BE');
      expect(libreTargetCandidates('zh_Hant').first, 'zh-Hant');
      expect(libreTargetCandidates('pt_BR'), contains('pb'));
      expect(readerTranslationLanguageName('pt_BR'), 'Brazilian Portuguese');
    });

    test('the API key lives under a key that exports strip', () {
      expect(isSecretPrefKey(readerTranslationApiKeyKey), isTrue);
      expect(jsonEncode(_deepl.toJson()), isNot(contains('secret')));
    });
  });

  group('service', () {
    test('DeepL sends one batched JSON request and restores the text around URLs', () async {
      final requests = <http.Request>[];
      final client = MockClient((request) async {
        requests.add(request);
        final texts = (jsonDecode(request.body)['text'] as List).cast<String>();
        return http.Response(
          jsonEncode({
            'translations': [
              for (final text in texts) {'text': 'DE:$text'},
            ],
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      });
      final service = ReaderTranslationService(client: client);
      final result = await service.translate('Hello https://x.example/a\n\nWorld', _deepl, language: 'de');
      expect(result, 'DE:Hello https://x.example/a\n\nDE:World');
      expect(requests, hasLength(1));
      expect(requests.single.url.toString(), 'https://api-free.deepl.com/v2/translate');
      expect(requests.single.headers['Authorization'], 'DeepL-Auth-Key secret:fx');
      final body = jsonDecode(requests.single.body);
      expect(body['target_lang'], 'DE');
      expect(body['text'], ['Hello', 'World']);
    });

    test('LibreTranslate uses the instance language names and optional key', () async {
      final seen = <Uri>[];
      final client = MockClient((request) async {
        seen.add(request.url);
        if (request.url.path.endsWith('/languages')) {
          return http.Response(
            jsonEncode([
              {'code': 'en'},
              {'code': 'zt'},
            ]),
            200,
          );
        }
        final body = jsonDecode(request.body);
        expect(body['target'], 'zt');
        expect(body['source'], 'auto');
        expect(body['api_key'], 'k');
        return http.Response(jsonEncode({'translatedText': '你好'}), 200, headers: {'content-type': 'application/json'});
      });
      final config = ReaderTranslationConfig(provider: _libre.provider, endpoint: _libre.endpoint, apiKey: 'k');
      final service = ReaderTranslationService(client: client);
      expect(await service.translate('Hello', config, language: 'zh_Hant'), '你好');
      expect(await service.translate('Hello', config, language: 'zh_Hant'), '你好');
      expect(seen.where((uri) => uri.path.endsWith('/languages')), hasLength(1));
      expect(seen.last.toString(), 'https://tools.example/libre/translate');
    });

    test('AI batches pieces as a JSON array and falls back to one request each', () async {
      final prompts = <String>[];
      var reply = '["Hallo", "Welt"]';
      final service = ReaderTranslationService(
        ai: (config, prompt) async {
          prompts.add(prompt);
          if (prompt.contains('JSON array')) return reply;
          return prompt.endsWith('Hello') ? 'Hallo' : 'Welt';
        },
      );
      expect(await service.translate('Hello\nWorld', _ai, language: 'de'), 'Hallo\nWelt');
      expect(prompts.single, contains('German'));
      reply = 'not json';
      prompts.clear();
      expect(await service.translate('Hello\nWorld', _ai, language: 'de'), 'Hallo\nWelt');
      expect(prompts, hasLength(3));
    });

    test('failures are reported by reason', () async {
      Future<ReaderTranslationFailure?> reason(Future<String> run) async {
        try {
          await run;
          return null;
        } on ReaderTranslationException catch (error) {
          return error.reason;
        }
      }

      final refusing = ReaderTranslationService(client: MockClient((_) async => http.Response('no', 403)));
      expect(await reason(refusing.translate('Hi', _deepl, language: 'de')), ReaderTranslationFailure.provider);
      final offline = ReaderTranslationService(client: MockClient((_) async => throw http.ClientException('down')));
      expect(await reason(offline.translate('Hi', _deepl, language: 'de')), ReaderTranslationFailure.network);
      final service = ReaderTranslationService(client: MockClient((_) async => http.Response('{}', 200)));
      expect(
        await reason(service.translate('Hi', const ReaderTranslationConfig(), language: 'de')),
        ReaderTranslationFailure.notConfigured,
      );
      expect(
        await reason(service.translate('a' * (readerTranslationMaxInput + 1), _deepl, language: 'de')),
        ReaderTranslationFailure.tooLong,
      );
      expect(await reason(service.translate('Hi', _deepl, language: 'de')), ReaderTranslationFailure.provider);
      expect(
        await reason(service.translate('Hi', _deepl, language: 'de', current: () => false)),
        ReaderTranslationFailure.cancelled,
      );
      expect(await service.translate('🎉 12', _deepl, language: 'de'), '🎉 12');
    });
  });
}
