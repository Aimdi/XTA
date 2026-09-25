import json
import unittest
from report_canary import classify, report

class CanaryReportTest(unittest.TestCase):
    def test_failure_identifies_service_and_retains_assertion_and_stack(self):
        raw = '\n'.join(json.dumps(e) for e in [
            {'type': 'testStart', 'test': {'id': 1, 'name': 'guest search'}},
            {'type': 'error', 'testID': 1, 'error': 'Expected: 200\nActual: 503', 'stackTrace': 'test/live/tiktok_search_smoke_test.dart:20'},
        ])
        result = report('TikTok', 'https://github.com/run', 'failure', raw)
        self.assertIn('TikTok live checks failed', result)
        self.assertIn('Actual: 503', result)
        self.assertIn('guest search', result)
        self.assertIn('smoke_test.dart:20', result)
        self.assertNotIn('X API', result)
    def test_setup_failure_does_not_claim_service_outage(self):
        result = report('Instagram', 'https://github.com/run', 'skipped', '')
        self.assertIn('probe did not run', result)
        self.assertNotIn('checks failed', result)

    def test_service_failure_is_classified_as_service(self):
        raw = json.dumps({
            'type': 'error',
            'error': 'Expected: 200\nActual: 503',
            'stackTrace': 'test/live/service.dart:1',
        })
        self.assertEqual(classify('failure', raw), 'service')

    def test_runner_and_probe_failures_are_infrastructure(self):
        samples = [
            'Building native assets failed',
            'Bad state: Hash of downloaded file sqlite.so is wrong',
            'Every endpoint was unreachable, so this run proves nothing about X.',
            'Failed to compile',
            'TimeoutException after 0:00:12.000000',
            'SocketException: Failed host lookup: x.com',
            'ClientException: Connection reset by peer',
            'ClientException: Connection refused',
        ]
        for raw in samples:
            with self.subTest(raw=raw):
                self.assertEqual(classify('failure', raw), 'infrastructure')
                self.assertIn(
                    'Classification: **infrastructure**',
                    report('X', 'https://github.com/run', 'failure', raw),
                )

    def test_bootstrap_parser_failures_override_inconclusive_footer(self):
        samples = [
            "Couldn't find ondemand file index",
            "Could not find ondemand file index",
            "Couldn't find ondemand file hash",
            "Couldn't get KEY_BYTE indices",
            "Couldn't get [twitter-site-verification] key from the page source",
            'FormatException: X pages did not contain transaction signing data',
        ]
        for failure in samples:
            raw = json.dumps({
                'type': 'error',
                'error': failure + '\nEvery endpoint was unreachable, so this run proves nothing about X.',
            })
            with self.subTest(failure=failure):
                self.assertEqual(classify('failure', raw), 'service')
                self.assertIn(
                    'Classification: **service**',
                    report('X', 'https://github.com/run', 'failure', raw),
                )

    def test_success_and_skipped_setup_do_not_reclassify_log_text(self):
        raw = "Couldn't find ondemand file index"
        self.assertEqual(classify('success', raw), 'success')
        self.assertEqual(classify('skipped', raw), 'infrastructure')

    def test_secrets_and_url_queries_are_removed(self):
        raw = json.dumps({'type': 'error', 'error': 'Authorization: Bearer TOPSECRET\nCookie: SESSION\nhttps://example.org/api?token=SECRETQUERY', 'stackTrace': ''})
        result = report('X', 'https://github.com/run', 'failure', raw)
        for secret in ['TOPSECRET', 'SESSION', 'SECRETQUERY']:
            self.assertNotIn(secret, result)
        self.assertIn('https://example.org/api', result)
    def test_non_json_runner_failure_is_not_lost(self):
        self.assertIn('Failed to compile', report('Substack', 'https://github.com/run', 'failure', 'Failed to compile'))

if __name__ == '__main__':
    unittest.main()
