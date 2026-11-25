"""Professional report regression tests with actual analytical engine outputs."""
import contextlib
import csv
from html.parser import HTMLParser
import io
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"desktop"))
import astra_professional_report as P
import astra_report_export as E
import astra_portfolio_analytics
import astra_portfolio_report
import astra_fixed_income
import astra_bond_report
import astra_yield_curve
import astra_yield_curve_report
import astra_portfolio_optimize
import astra_optimization_report


class Document(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tags=[]
        self.text=[]
    def handle_starttag(self,tag,attrs):
        self.tags.append((tag,dict(attrs)))
    def handle_data(self,data):
        self.text.append(data)


class ProfessionalReportTests(unittest.TestCase):
    def analyze(self,example,engine,reporter):
        data=json.loads((ROOT/"desktop/examples"/example).read_text(encoding="utf-8"))
        result=engine.analyze(data)
        return result,reporter.report(result)

    def test_actual_portfolio_report_and_allocation_chart(self):
        result,md=self.analyze("portfolio_analysis.json",astra_portfolio_analytics,astra_portfolio_report)
        rendered=P.render_html(md,"portfolio",result)
        self.assertIn("Closing market-value allocation",rendered)
        self.assertIn("65.455%",rendered)
        self.assertIn("34.545%",rendered)
        self.assertIn("BWP 110,000.00",rendered)
        self.assertIn("Basis and limitations",rendered)
        self.assertIn("table-header-group",rendered)
        parser=Document()
        parser.feed(rendered)
        self.assertGreaterEqual(sum(tag=="table" for tag,_ in parser.tags),4)
        self.assertFalse(any(tag in ("script","iframe","object","img","link") for tag,_ in parser.tags))

    def test_real_curve_bond_and_frontier_charts(self):
        fixtures=[
            ("yield_curve.json",astra_yield_curve,astra_yield_curve_report,"curve","Continuous zero curve"),
            ("bond_analysis.json",astra_fixed_income,astra_bond_report,"bond","Bond yield sensitivity"),
            ("portfolio_optimization.json",astra_portfolio_optimize,astra_optimization_report,"optimization","Nondominated allocation grid"),
        ]
        for example,engine,reporter,mode,title in fixtures:
            with self.subTest(mode=mode):
                result,md=self.analyze(example,engine,reporter)
                rendered=P.render_html(md,mode,result)
                self.assertIn(title,rendered)
                parser=Document()
                parser.feed(rendered)
                self.assertTrue(any(tag=="svg" and attrs.get("role")=="img" for tag,attrs in parser.tags))
                self.assertNotIn('points="nan',rendered.lower())

    def test_input_is_escaped_and_raw_html_never_executes(self):
        md="# <script>alert(1)</script>\n\n## Notes\n\n**<img src=x onerror=x>**\n\n- <iframe src=x>\n\n| Label | Value |\n|---|---:|\n| <a href=x> | **bold** |"
        rendered=P.render_html(md,"unknown",{})
        self.assertIn("&lt;script&gt;",rendered)
        self.assertIn("&lt;img src=x onerror=x&gt;",rendered)
        self.assertIn("<strong>bold</strong>",rendered)
        self.assertIn("Content-Security-Policy",rendered)
        parser=Document()
        parser.feed(rendered)
        self.assertFalse(any(tag in ("script","iframe","img","a") for tag,_ in parser.tags))

    def test_safe_csv_export_and_signed_numbers(self):
        md="# Report\n## Data\n| Item | Return |\n|---|---:|\n| =1+1 | -12.5% |\n| +cmd | 1.0 |\n| @SUM(A1) | -1 |\n| -cmd | 1e-5 |"
        csv_text=P.table_csv(md,1)
        self.assertTrue(csv_text.startswith("\ufeff"))
        rows=list(csv.reader(io.StringIO(csv_text.lstrip("\ufeff"))))
        self.assertEqual([["Item","Return"],["'=1+1","-12.5%"],["'+cmd","1.0"],
                          ["'@SUM(A1)","-1"],["'-cmd","1e-5"]],rows)
        self.assertEqual("' \t=1",P.spreadsheet_cell(" \t=1"))
        with self.assertRaises(ValueError):
            P.table_csv(md,0)

    def test_table_parsing_rejects_malformed_rows_and_limits(self):
        with self.assertRaises(ValueError):
            P.blocks("| A | B |\n|---|---|\n| only one |")
        with self.assertRaises(ValueError):
            P.blocks("x"*(P.MAX_MARKDOWN+1))
        self.assertEqual([("Section",["A","B"],[["1","2"]])],
                         P.tables("# Title\n## Section\n| A | B |\n|---|---|\n| 1 | 2 |"))

    def test_chart_unavailable_without_fabricating_data(self):
        rendered=P.render_html("# Test\n## Results\nNo value.","curve",{})
        self.assertIn("Chart unavailable",rendered)
        self.assertNotIn("<svg",rendered)
        self.assertEqual("",P.charts("bond",{"scenarios":[]}))

    def test_sampling_preserves_endpoints_and_plot_escapes_labels(self):
        values=list(range(2520))
        selected=P.sample(values)
        self.assertEqual(120,len(selected))
        self.assertEqual((0,2519),(selected[0],selected[-1]))
        rendered=P.plot("<title>","x","y",[("<script>",[(P.numeric("1"),P.numeric("2"))])])
        self.assertIn("&lt;script&gt;",rendered)
        self.assertNotIn("<script>",rendered)

    def test_cli_exclusive_output_and_traceability(self):
        result,md=self.analyze("portfolio_analysis.json",astra_portfolio_analytics,astra_portfolio_report)
        fake=SimpleNamespace(from_files=lambda *a,**kw:({"result":result},md),
                             render=lambda e,m,f:m+"\n## Input traceability\n\nCanonical request SHA-256: "+"a"*64+".")
        with tempfile.TemporaryDirectory() as tmp,patch.dict(sys.modules,{"astra_analytics":fake}):
            path=Path(tmp)/"report.html"
            args=["portfolio","example.json","--output",str(path)]
            self.assertEqual(0,E.main(args))
            before=path.read_bytes()
            self.assertIn(b"Canonical request SHA-256",before)
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1,E.main(args))
                self.assertEqual(1,E.main(["portfolio","example.json","--format","csv"]))
            self.assertEqual(before,path.read_bytes())
            stdout=io.StringIO()
            with contextlib.redirect_stdout(stdout):
                self.assertEqual(0,E.main(["portfolio","example.json","--list-tables"]))
            self.assertGreaterEqual(len(json.loads(stdout.getvalue())),4)
            csv_path=Path(tmp)/"summary.csv"
            self.assertEqual(0,E.main(["portfolio","example.json","--format","csv","--table","1","--output",str(csv_path)]))
            self.assertTrue(csv_path.read_bytes().startswith(b"\xef\xbb\xbf"))


if __name__=="__main__":
    unittest.main()
