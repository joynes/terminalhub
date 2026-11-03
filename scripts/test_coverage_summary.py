import tempfile
import unittest
from pathlib import Path
from coverage_summary import summarize


class CoverageSummaryTest(unittest.TestCase):
    def report(self, xml):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "report.xml"
        path.write_text(xml)
        return summarize(path)

    def test_zero_covered_sources_stay_in_report_and_totals_are_weighted(self):
        result = self.report('''<report><package name="app">
            <sourcefile name="Small.kt"><counter type="LINE" missed="0" covered="1"/></sourcefile>
            <sourcefile name="Large.kt"><counter type="LINE" missed="99" covered="0"/></sourcefile>
            </package><counter type="LINE" missed="99" covered="1"/>
            <counter type="BRANCH" missed="4" covered="0"/></report>''')
        self.assertEqual(1.0, result["counters"]["LINE"]["percent"])
        self.assertEqual(0.0, result["counters"]["BRANCH"]["percent"])
        self.assertEqual("app/Large.kt", result["sources"][0]["file"])
        self.assertEqual(99, result["sources"][0]["missed"])

    def test_missing_or_empty_line_counters_cannot_pass_as_full_coverage(self):
        for xml in ('<report/>', '<report><counter type="LINE" missed="0" covered="0"/></report>'):
            with self.subTest(xml=xml), self.assertRaisesRegex(ValueError, "no executable lines"):
                self.report(xml)

    def test_non_executable_source_is_ignored_but_executable_zero_coverage_is_not(self):
        result = self.report('''<report><package name="app"><sourcefile name="Constants.kt"/>
            <sourcefile name="Work.kt"><counter type="LINE" missed="3" covered="0"/></sourcefile>
            </package><counter type="LINE" missed="3" covered="0"/></report>''')
        self.assertEqual(1, len(result["sources"]))
        self.assertEqual(0.0, result["counters"]["LINE"]["percent"])


if __name__ == "__main__":
    unittest.main()
