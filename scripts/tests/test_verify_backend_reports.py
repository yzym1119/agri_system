import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'verify-backend-reports.py'

class BackendReportGateTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT.exists(), '缺少后端测试报告检查入口')
        spec = importlib.util.spec_from_file_location('gate', SCRIPT)
        self.gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.gate)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def report(self, suite, cases='<testcase name="works"/>', **counts):
        values = {'tests': '1', 'failures': '0', 'errors': '0', 'skipped': '0', **counts}
        folder = self.root / 'module/target/surefire-reports'
        folder.mkdir(parents=True, exist_ok=True)
        attrs = ' '.join(f'{k}="{v}"' for k, v in values.items())
        (folder / f'TEST-{suite}.xml').write_text(f'<testsuite name="{suite}" {attrs}>{cases}</testsuite>')

    def complete(self):
        for suite in self.gate.REQUIRED_SUITES:
            self.report(suite)
        self.report(self.gate.AUTHORITY_SUITE, f'<testcase name="{self.gate.AUTHORITY_MYSQL_CASE}"/>')

    def test_empty_reports_fail(self):
        with self.assertRaises(ValueError):
            self.gate.verify(self.root)

    def test_complete_reports_pass(self):
        self.complete()
        result = self.gate.verify(self.root)
        self.assertEqual(result['tests'], 5)
        self.assertEqual(result['skipped'], 0)

    def test_skipped_case_cannot_hide_behind_zero_summary(self):
        self.complete()
        self.report('extra', '<testcase name="not_run"><skipped/></testcase>')
        with self.assertRaises(ValueError):
            self.gate.verify(self.root)

    def test_missing_conditional_mysql_case_fails(self):
        self.complete()
        self.report(self.gate.AUTHORITY_SUITE)
        with self.assertRaises(ValueError):
            self.gate.verify(self.root)

    def test_missing_integration_suite_fails(self):
        self.complete()
        next(self.root.rglob('*LegacyCleanupMigrationTest.xml')).unlink()
        with self.assertRaises(ValueError):
            self.gate.verify(self.root)

    def test_failed_and_malformed_reports_fail(self):
        for content in ['<testsuite', '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="bad"><failure/></testcase></testsuite>', '<testsuite tests="0" failures="0" errors="0" skipped="0"/>']:
            with self.subTest(content=content):
                self.complete()
                self.report('extra')
                next(self.root.rglob('TEST-extra.xml')).write_text(content)
                with self.assertRaises(ValueError):
                    self.gate.verify(self.root)

if __name__ == '__main__':
    unittest.main()
