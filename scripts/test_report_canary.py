import json
import unittest
from report_canary import report

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
